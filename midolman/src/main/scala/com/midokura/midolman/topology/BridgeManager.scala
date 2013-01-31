/*
 * Copyright 2012 Midokura Europe SARL
 */
package com.midokura.midolman.topology

import collection.{Map => ROMap, mutable}
import collection.JavaConversions._
import compat.Platform

import akka.event.LoggingAdapter
import akka.util.Duration

import java.util.UUID
import java.util.concurrent.TimeUnit

import com.midokura.midolman.FlowController
import com.midokura.midolman.simulation.Bridge
import com.midokura.midolman.topology.builders.BridgeBuilderImpl
import com.midokura.midonet.cluster.Client
import com.midokura.midonet.cluster.client._
import com.midokura.packets.{IntIPv4, MAC}
import com.midokura.util.functors.Callback0


/* The MacFlowCount is called from the Coordinators' actors and dispatches
 * to the BridgeManager's actor to get/modify the flow counts.  */
trait MacFlowCount {
    def increment(mac: MAC, port: UUID): Unit
    def decrement(mac: MAC, port: UUID): Unit
}

trait RemoveFlowCallbackGenerator {
    def getCallback(mac: MAC, port: UUID): Callback0
}

class BridgeConfig() {
    var tunnelKey: Int = 0 // Only set in prepareBridgeCreate
    var inboundFilter: UUID = null
    var outboundFilter: UUID = null

    override def hashCode: Int = {
        var hCode = 0
        if (null != inboundFilter)
            hCode += inboundFilter.hashCode
        if (null != outboundFilter)
            hCode = hCode * 17 + outboundFilter.hashCode
        hCode
    }

    override def equals(other: Any) = other match {
        case that: BridgeConfig =>
            (that canEqual this) &&
                (this.inboundFilter == that.inboundFilter) &&
                (this.outboundFilter == that.outboundFilter)
        case _ =>
            false
    }

    def canEqual(other: Any) = other.isInstanceOf[BridgeConfig]

    override def clone: BridgeConfig = {
        val ret = new BridgeConfig()
        ret.inboundFilter = this.inboundFilter
        ret.outboundFilter = this.outboundFilter
        ret.tunnelKey = this.tunnelKey
        ret
    }
}

object BridgeManager {
    val Name = "BridgeManager"
    var macPortExpiration = 30*1000

    case class TriggerUpdate(cfg: BridgeConfig,
                             macLearningTable: MacLearningTable,
                             rtrMacToLogicalPortId: ROMap[MAC, UUID],
                             rtrIpToMac: ROMap[IntIPv4, MAC])

    case class CheckExpiredMacPorts()

    def setMacPortExpiration(expiration: Int) {
        macPortExpiration = expiration
    }

}

class MacLearningManager(log: LoggingAdapter, expirationMillis: Long) {
    var backendMap: MacLearningTable = null

    private val flowCountMap = mutable.Map[(MAC, UUID), Int]()
    // Map mac-port pairs that need to be deleted to the time at which they
    // should be deleted. A map allows easy updates as the flowCounts change.
    // The ordering provided by the LinkedHashMap allows traversing in
    // insertion-order, which should be identical to the expiration order.
    // A mac-port pair will only be present this this map if it is also present
    // in the flowCountMap. The life-cycle of a mac-port pair is:
    // - When it's first learned, add ((mac,port), 1) to flowCountMap and
    //   write to to backendMap (which reflects distributed/shared map).
    // - When flows are added/removed, increment/decrement the flowCount
    // - If flowCount goes from 1 to 0, add (mac,port) to macPortToRemove
    // - If flowCount goes from 0 to 1, remove (mac,port) from macPortToRemove
    // - When iterating macPortToRemove, if the (mac,port) deletion time is
    //   in the past, remove it from macPortToRemove, flowCountMap and
    //   backendMap.
    private val macPortsToRemove = mutable.LinkedHashMap[(MAC, UUID), Long]()

    def incRefCount(mac: MAC, port: UUID): Unit = {
        flowCountMap.get((mac, port)) match {
            case None =>
                log.debug("First learning mac-port association. " +
                    "Incrementing reference count of {} on {} to 1",
                    mac, port)
                flowCountMap.put((mac, port), 1)
                backendMap.add(mac, port)
            case Some(i: Int) =>
                log.debug("Incrementing reference count of {} on {} to {}",
                    mac, port, i+1)
                flowCountMap.put((mac, port), i+1)
                if (i == 0) {
                    log.debug("Unscheduling removal of mac-port pair, mac {}, " +
                        "port {}.", mac, port)
                    macPortsToRemove.remove((mac, port))
                }
        }
    }

    def decRefCount(mac: MAC, port: UUID, currentTime: Long): Unit = {
        flowCountMap.get((mac, port)) match {
            case None =>
                log.error("Decrement flow count for unlearned mac-port " +
                    "{} {}", mac, port)
            case Some(i: Int) =>
                if (i <= 0) {
                    log.error("Decrement a flow count past {} " +
                        "for mac-port {} {}", i, mac, port)
                } else {
                    log.debug("Decrementing reference count of {} on {} " +
                        "to {}", mac, port, i-1)
                    flowCountMap.put((mac, port), i-1)
                    if (i == 1) {
                        log.debug("Scheduling removal of mac-port pair, mac {}, " +
                            "port {}.", mac, port)
                        macPortsToRemove.put((mac, port),
                            currentTime + expirationMillis)
                    }
                }
        }
    }

    def doDeletions(currentTime: Long): Unit = {
        log.debug("Size deleting {}", macPortsToRemove.size)
        val it: Iterator[((MAC, UUID), Long)] = macPortsToRemove.iterator
        while (it.hasNext) {
            val ((mac, port), expireTime) = it.next()
            if (expireTime <= currentTime) {
                log.debug("Forgetting mac-port entry {} {}", mac, port)
                backendMap.remove(mac, port)
                flowCountMap.remove((mac, port))
                macPortsToRemove.remove((mac, port))
            }
            else return
        }
    }
}

class BridgeManager(id: UUID, val clusterClient: Client)
        extends DeviceManager(id) {
    import BridgeManager._
    implicit val system = context.system

    private var cfg: BridgeConfig = null

    private val learningMgr = new MacLearningManager(log, macPortExpiration)
    private val flowCounts = new MacFlowCountImpl
    private val flowRemovedCallback = new RemoveFlowCallbackGeneratorImpl

    private var rtrMacToLogicalPortId: ROMap[MAC, UUID] = null
    private var rtrIpToMac: ROMap[IntIPv4, MAC] = null

    private var filterChanged = false

    override def chainsUpdated() {
        log.info("chains updated")
        context.actorFor("..").tell(
            new Bridge(id, getTunnelKey, learningMgr.backendMap, flowCounts,
                       inFilter, outFilter, flowRemovedCallback,
                       rtrMacToLogicalPortId, rtrIpToMac))
        if(filterChanged){
            FlowController.getRef() ! FlowController.InvalidateFlowsByTag(
            FlowTagger.invalidateFlowsByDevice(id))
        }
        filterChanged = false
    }

    def getTunnelKey: Long = {
        cfg match {
            case null => 0
            case c => c.tunnelKey
        }
    }

    override def preStart() {
        clusterClient.getBridge(id, new BridgeBuilderImpl(id,
            FlowController.getRef(), self))
        // Schedule the recurring cleanup of expired mac-port associations.
        context.system.scheduler.schedule(
            Duration(macPortExpiration, TimeUnit.MILLISECONDS),
            Duration(2000, TimeUnit.MILLISECONDS), self, CheckExpiredMacPorts)

    }

    override def getInFilterID: UUID = {
        cfg match {
            case null => null
            case _ => cfg.inboundFilter
        }
    }

    override def getOutFilterID: UUID = {
        cfg match {
            case null => null
            case _ => cfg.outboundFilter
        }
    }

    private case class FlowIncrement(mac: MAC, port: UUID)

    private case class FlowDecrement(mac: MAC, port: UUID)

    override def receive = super.receive orElse {

        case FlowIncrement(mac, port) =>
            learningMgr.incRefCount(mac, port)

        case FlowDecrement(mac, port) =>
            learningMgr.decRefCount(mac, port, Platform.currentTime)

        case CheckExpiredMacPorts() =>
            learningMgr.doDeletions(Platform.currentTime)

        case TriggerUpdate(newCfg, macLearningTable, newRtrMacToLogicalPortId,
                           newRtrIpToMac) =>
            log.debug("Received a Bridge update from the data store.")
            if (newCfg != cfg && cfg != null) {
                // the cfg of this bridge changed, invalidate all the flows
                filterChanged = true
            }
            cfg = newCfg.clone
            learningMgr.backendMap = macLearningTable
            rtrMacToLogicalPortId = newRtrMacToLogicalPortId
            rtrIpToMac = newRtrIpToMac
            // Notify that the update finished
            configUpdated()
    }

    private class MacFlowCountImpl extends MacFlowCount {
        override def increment(mac: MAC, port: UUID) {
            self ! FlowIncrement(mac, port)
        }

        override def decrement(mac: MAC, port: UUID) {
            self ! FlowDecrement(mac, port)
        }
    }

    class RemoveFlowCallbackGeneratorImpl() extends RemoveFlowCallbackGenerator{
        def getCallback(mac: MAC, port: UUID): Callback0 = {
            new Callback0() {
                def call() {
                    // TODO(ross): check, is this the proper self, that is
                    // BridgeManager?  or it will be the self of the actor who
                    // execute this callback?
                    self ! FlowDecrement(mac, port)
                }
            }
        }
    }
}
