/*
* Copyright 2012 Midokura Europe SARL
*/
package com.midokura.midolman

import flows.{WildcardMatches, WildcardMatch, WildcardFlow}
import logging.ActorLogWithoutPath
import scala.collection.JavaConversions._
import scala.collection.{Set => ROSet, immutable, mutable}
import scala.collection.mutable.ListBuffer
import akka.actor.{Cancellable, Actor, ActorLogging, ActorRef}
import akka.dispatch.{Future, Promise}
import akka.pattern.ask
import akka.util.Timeout
import akka.util.duration._
import java.lang.{Boolean => JBoolean, Short => JShort}
import java.util.{HashSet, Set => JSet, UUID, List => JList}
import java.nio.ByteBuffer

import com.google.inject.Inject

import com.midokura.midolman.host.interfaces.InterfaceDescription
import com.midokura.midolman.host.scanner.InterfaceScanner
import com.midokura.midolman.FlowController.AddWildcardFlow
import com.midokura.midolman.datapath._
import com.midokura.midolman.monitoring.MonitoringActor
import com.midokura.midolman.rules.{ChainPacketContext, RuleResult}
import com.midokura.midolman.services.HostIdProviderService
import com.midokura.midolman.simulation.{Bridge => RCUBridge, Chain}
import com.midokura.midolman.topology._
import com.midokura.midolman.topology.VirtualTopologyActor.{BridgeRequest,
        ChainRequest, PortRequest}
import com.midokura.midolman.topology.rcu.{Host, PortSet}
import com.midokura.midonet.cluster.client
import com.midokura.midonet.cluster.client.ExteriorPort
import com.midokura.midonet.cluster.data.TunnelZone
import com.midokura.midonet.cluster.data.TunnelZone.{HostConfig => TZHostConfig}
import com.midokura.midonet.cluster.data.zones.{IpsecTunnelZoneHost,
        CapwapTunnelZoneHost, GreTunnelZoneHost}
import com.midokura.netlink.Callback
import com.midokura.netlink.exceptions.NetlinkException
import com.midokura.netlink.exceptions.NetlinkException.ErrorCode
import com.midokura.netlink.protos.OvsDatapathConnection
import com.midokura.packets.Ethernet
import com.midokura.sdn.dp.{Flow => KernelFlow, _}
import com.midokura.sdn.dp.flows.{FlowActionUserspace, FlowAction, FlowKeys, FlowActions}
import com.midokura.sdn.dp.ports._
import com.midokura.util.functors.Callback0
import com.midokura.packets.IntIPv4


/**
 * Holder object that keeps the external message definitions
 */
object PortOperation extends Enumeration {
    val Create, Delete = Value
}

object TunnelChangeEventOperation extends Enumeration {
    val Established, Removed = Value
}

sealed trait PortOp[P <: Port[_ <: PortOptions, P]] {
    val port: P
    val tag: Option[AnyRef]
    val op: PortOperation.Value
}

sealed trait CreatePortOp[P <: Port[_ <: PortOptions, P]] extends {
    val op = PortOperation.Create
} with PortOp[P]

sealed trait DeletePortOp[P <: Port[_ <: PortOptions, P]] extends {
    val op = PortOperation.Delete
} with PortOp[P]

sealed trait PortOpReply[P <: Port[_ <: PortOptions, P]] {
    val port: P
    val tag: Option[AnyRef]
    val op: PortOperation.Value
    val timeout: Boolean
    val error: NetlinkException
}

/**
 * This will make the Datapath Controller to start the local state
 * initialization process.
 */
case class Initialize()

object DatapathController extends Referenceable {

    override val Name = "DatapathController"

    // Java API
    def getInitialize: Initialize = {
        Initialize()
    }

    /**
     * Reply sent back to the sender of the Initialize message when the basic
     * initialization of the datapath is complete.
     */
    case class InitializationComplete()


    /**
     * Message sent to the [[com.midokura.midolman.FlowController]] actor to let
     * it know that it can install the the packetIn hook inside the datapath.
     *
     * @param datapath the active datapath
     */
    case class DatapathReady(datapath: Datapath)

    /**
     * Will trigger an internal port creation operation. The sender will
     * receive an [[com.midokura.midolman.DatapathController.PortInternalOpReply]]
     * message in return.
     *
     * @param port the port information
     * @param tag a value that is going to be copied to the reply message
     */
    case class CreatePortInternal(port: InternalPort, tag: Option[AnyRef])
        extends CreatePortOp[InternalPort]

    /**
     * Will trigger an internal port delete operation. The sender will
     * receive an [[com.midokura.midolman.DatapathController.PortInternalOpReply]]
     * message when the operation is completed.
     *
     * @param port the port information
     * @param tag a value that is going to be copied to the reply message
     */
    case class DeletePortInternal(port: InternalPort, tag: Option[AnyRef])
        extends DeletePortOp[InternalPort]

    /**
     * Reply message that is sent when a [[com.midokura.midolman.DatapathController.CreatePortInternal]]
     * or [[com.midokura.midolman.DatapathController.DeletePortInternal]]
     * operation completes. It contains the operation type, the port data
     * (updated or the original) and any error or timeout if the operation failed.
     *
     * @param port the internal port data
     * @param op the operation type
     * @param timeout true if the operation timed out
     * @param error non null if the underlying layer has thrown exceptions
     * @param tag is the same value that was passed in the initial operation by
     *            the caller
     */
    case class PortInternalOpReply(port: InternalPort, op: PortOperation.Value,
                                   timeout: Boolean, error: NetlinkException,
                                   tag: Option[AnyRef])
        extends PortOpReply[InternalPort]

    /**
     * Will trigger an netdev port creation operation. The sender will
     * receive an `PortNetdevOpReply` message in return.
     *
     * @param port the port information
     * @param tag a value that is going to be copied to the reply message
     */
    case class CreatePortNetdev(port: NetDevPort, tag: Option[AnyRef])
        extends CreatePortOp[NetDevPort]

    /**
     * Will trigger an netdev port deletion operation. The sender will
     * receive an [[com.midokura.midolman.DatapathController.PortNetdevOpReply]]
     * message in return.
     *
     * @param port the port information
     * @param tag a value that is going to be copied to the reply message
     */
    case class DeletePortNetdev(port: NetDevPort, tag: Option[AnyRef])
        extends DeletePortOp[NetDevPort]

    /**
     * Reply message that is sent when a [[com.midokura.midolman.DatapathController.CreatePortNetdev]]
     * or [[com.midokura.midolman.DatapathController.DeletePortNetdev]]
     * operation completes. It contains the operation type, the port data
     * (updated or the original) and any error or timeout if the operation failed.
     *
     * @param port the internal port data
     * @param op the operation type
     * @param timeout true if the operation timed out
     * @param error non null if the underlying layer has thrown exceptions
     * @param tag is the same value that was passed in the initial operation by
     *            the caller
     */
    case class PortNetdevOpReply(port: NetDevPort, op: PortOperation.Value,
                                 timeout: Boolean, error: NetlinkException,
                                 tag: Option[AnyRef])
        extends PortOpReply[NetDevPort]

    /**
     * Will trigger an `patch` tunnel creation operation. The sender will
     * receive an [[com.midokura.midolman.DatapathController.TunnelPatchOpReply]]
     * message in return.
     *
     * @param port the tunnel port information
     * @param tag a value that is going to be copied to the reply message
     */
    case class CreateTunnelPatch(port: PatchTunnelPort, tag: Option[AnyRef])
        extends CreatePortOp[PatchTunnelPort]

    /**
     * Will trigger an `patch` tunnel deletion operation. The sender will
     * receive an [[com.midokura.midolman.DatapathController.TunnelPatchOpReply]]
     * message in return.
     *
     * @param port the tunnel port information
     * @param tag a value that is going to be copied to the reply message
     */
    case class DeleteTunnelPatch(port: PatchTunnelPort, tag: Option[AnyRef])
        extends DeletePortOp[PatchTunnelPort]

    /**
     * Reply message that is sent when a [[com.midokura.midolman.DatapathController.CreateTunnelPatch]]
     * or [[com.midokura.midolman.DatapathController.DeleteTunnelPatch]]
     * operation completes. It contains the operation type, the port data
     * (updated or the original) and any error or timeout if the operation failed.
     *
     * @param port the internal port data
     * @param op the operation type
     * @param timeout true if the operation timed out
     * @param error non null if the underlying layer has thrown exceptions
     * @param tag is the same value that was passed in the initial operation by
     *            the caller
     */
    case class TunnelPatchOpReply(port: PatchTunnelPort, op: PortOperation.Value,
                                  timeout: Boolean, error: NetlinkException,
                                  tag: Option[AnyRef])
        extends PortOpReply[PatchTunnelPort]

    /**
     * Will trigger an `gre` tunnel creation operation. The sender will
     * receive an [[com.midokura.midolman.DatapathController.TunnelGreOpReply]]
     * message in return.
     *
     * @param port the tunnel port information
     * @param tag a value that is going to be copied to the reply message
     */
    case class CreateTunnelGre(port: GreTunnelPort, tag: Option[AnyRef])
        extends CreatePortOp[GreTunnelPort]

    /**
     * Will trigger an `gre` tunnel deletion operation. The sender will
     * receive an [[com.midokura.midolman.DatapathController.TunnelGreOpReply]]
     * message in return.
     *
     * @param port the tunnel port information
     * @param tag a value that is going to be copied to the reply message
     */
    case class DeleteTunnelGre(port: GreTunnelPort, tag: Option[AnyRef])
        extends DeletePortOp[GreTunnelPort]

    /**
     * Reply message that is sent when a [[com.midokura.midolman.DatapathController.CreateTunnelGre]]
     * or [[com.midokura.midolman.DatapathController.DeleteTunnelGre]]
     * operation completes. It contains the operation type, the port data
     * (updated or the original) and any error or timeout if the operation failed.
     *
     * @param port the internal port data
     * @param op the operation type
     * @param timeout true if the operation timed out
     * @param error non null if the underlying layer has thrown exceptions
     * @param tag is the same value that was passed in the initial operation by
     *            the caller
     */
    case class TunnelGreOpReply(port: GreTunnelPort, op: PortOperation.Value,
                                timeout: Boolean, error: NetlinkException,
                                tag: Option[AnyRef])
        extends PortOpReply[GreTunnelPort]

    /**
     * Will trigger an `capwap` tunnel creation operation. The sender will
     * receive an [[com.midokura.midolman.DatapathController.TunnelCapwapOpReply]]
     * message in return.
     *
     * @param port the tunnel port information
     * @param tag a value that is going to be copied to the reply message
     */
    case class CreateTunnelCapwap(port: CapWapTunnelPort, tag: Option[AnyRef])
        extends CreatePortOp[CapWapTunnelPort]

    /**
     * Will trigger an `capwap` tunnel deletion operation. The sender will
     * receive an [[com.midokura.midolman.DatapathController.TunnelCapwapOpReply]]
     * message in return.
     *
     * @param port the tunnel port information
     * @param tag a value that is going to be copied to the reply message
     */
    case class DeleteTunnelCapwap(port: CapWapTunnelPort, tag: Option[AnyRef])
        extends DeletePortOp[CapWapTunnelPort]

    /**
     * Reply message that is sent when a [[com.midokura.midolman.DatapathController.CreateTunnelCapwap]]
     * or [[com.midokura.midolman.DatapathController.DeleteTunnelCapwap]]
     * operation completes. It contains the operation type, the port data
     * (updated or the original) and any error or timeout if the operation failed.
     *
     * @param port the internal port data
     * @param op the operation type
     * @param timeout true if the operation timed out
     * @param error non null if the underlying layer has thrown exceptions
     * @param tag is the same value that was passed in the initial operation by
     *            the caller
     */
    case class TunnelCapwapOpReply(port: CapWapTunnelPort, op: PortOperation.Value,
                                   timeout: Boolean, error: NetlinkException,
                                   tag: Option[AnyRef])
        extends PortOpReply[CapWapTunnelPort]

    /**
     * This message requests that the DatapathController keep a temporary
     * binding of a virtual port (port in the virtual topology) to a local
     * datapath port. This may be used e.g. by the VPNManager to create
     * VPN ports - VPN ports are not associated with VMs and therefore not
     * in any host's Interface-VPort mappings.
     *
     * The binding will be removed when the datapath port is deleted.
     *
     * @param vportID the virtual port we want to bind to this internal port
     * @param port the internal port we want to bind to
     */
    case class BindToInternalPort(vportID: UUID, port: InternalPort)
    case class BindToNetDevPort(vportID: UUID, port: NetDevPort)

    case class InstallFlow(flow: KernelFlow)

    case class DeleteFlow(flow: KernelFlow)

    /**
     * Upon receiving this message, the DatapathController translates any
     * actions that are not understood by the Netlink layer and then sends the
     * packet to the kernel (who in turn executes the actions on the packet's
     * data).
     *
     * @param ethPkt The Ethernet packet that should be sent to the kernel.
     * @param actions The list of actions the kernel should apply to the data
     */
    case class SendPacket(ethPkt: Ethernet, actions: List[FlowAction[_]])

    case class PacketIn(wMatch: WildcardMatch, pktBytes: Array[Byte],
                        dpMatch: FlowMatch, reason: Packet.Reason,
                        cookie: Option[Int])

   /**
    * This message encapsulates a given port stats to the monitoring agent.
    * @param stats
    */
    case class PortStats(portID: UUID, stats: Port.Stats)

    class DatapathPortChangedEvent(val port: Port[_, _], val op: PortOperation.Value) {}

    class TunnelChangeEvent(val myself: Option[TZHostConfig[_,_]],
                            val peer: TZHostConfig[_, _],
                            val portOption: Option[Short],
                            val op: TunnelChangeEventOperation.Value)

    /**
     * This message requests stats for a given port.
     * @param portID
     */
    case class PortStatsRequest(portID: UUID)

    /**
     * Dummy ChainPacketContext used in egress port set chains.
     * All that is available is the Output Port ID (there's no information
     * on the ingress port or connection tracking at the egress controller).
     * @param outportID UUID for the output port
     */
    class EgressPortSetChainPacketContext(outportID: UUID)
            extends ChainPacketContext {
        override def getInPortId() = null
        override def getOutPortId() = outportID
        override def getPortGroups() = new HashSet[UUID]()
        override def addTraversedElementID(id: UUID) { }
        override def isConnTracked() = false
        override def isForwardFlow() = true
        override def getFlowCookie() = null
        override def addFlowTag(tag: Any) {}
        override def addFlowRemovedCallback(cb: Callback0) {}
        override def getParentCookie = null
    }

    /**
     * This message is sent every 2 seconds to check that the kernel contains exactly the same
     * ports/interfaces as the system. In case that somebody uses a command line tool (for example)
     * to bring down an interface, the system will react to it.
     * TODO this version is constantly checking for changes. It should react to 'netlink' notifications instead.
     */
    case class CheckForPortUpdates(datapathName: String)

    /**
     * This message is only to be sent when testing. This will disable the feature that is constantly
     * inspecting the ports to react to unexpected changes.
     */
    case class DisablePortWatcher()

    /**
     * This message is sent when the separate thread has succesfully retrieved all information about the interfaces.
     */
    case class InterfacesUpdate(interfaces: JList[InterfaceDescription])

    /**
     * This message is sent when the DHCP handler needs to get information
     * on local interfaces that are used for tunnels, what it returns is 
     * { source IP address, tunnel type } where the source IP address
     * correspond to the source IP address of the tunnel type
     */
    case class LocalTunnelInterfaceInfo()

    /**
     * This message is sent when the LocalTunnelInterfaceInfo handler 
     * completes the interface scan and pass the result as well as
     * original sender info
     */
    case class LocalInterfaceTunnelInfoFinal(caller : ActorRef,
                                             interfaces: JList[InterfaceDescription])
}


/**
 * The DP (Datapath) Controller is responsible for managing MidoNet's local
 * kernel datapath. It queries the Virt-Phys mapping to discover (and receive
 * updates about) what virtual ports are mapped to this host's interfaces.
 * It uses the Netlink API to query the local datapaths, create the datapath
 * if it does not exist, create datapath ports for the appropriate host
 * interfaces and learn their IDs (usually a Short), locally track the mapping
 * of datapath port ID to MidoNet virtual port ID. When a locally managed vport
 * has been successfully mapped to a local network interface, the DP Controller
 * notifies the Virtual-Physical Mapping that the vport is ready to receive flows.
 * This allows other Midolman daemons (at other physical hosts) to correctly
 * forward flows that should be emitted from the vport in question.
 * The DP Controller knows when the Datapath is ready to be used and notifies
 * the Flow Controller so that the latter may register for Netlink PacketIn
 * notifications. For any PacketIn that the FlowController cannot handle with
 * the already-installed wildcarded flows, DP Controller receives a PacketIn
 * from the FlowController, translates the arriving datapath port ID to a virtual
 * port UUID and passes the PacketIn to the Simulation Controller. Upon receiving
 * a simulation result from the Simulation Controller, the DP is responsible
 * for creating the corresponding wildcard flow. If the flow is being emitted
 * from a single remote virtual port, this involves querying the Virtual-Physical
 * Mapping for the location of the host responsible for that virtual port, and
 * then building an appropriate tunnel port or using the existing one. If the
 * flow is being emitted from a single local virtual port, the DP Controller
 * recognizes this and uses the corresponding datapath port. Finally, if the
 * flow is being emitted from a PortSet, the DP Controller queries the
 * Virtual-Physical Mapping for the set of hosts subscribed to the PortSet;
 * it must then map each of those hosts to a tunnel and build a wildcard flow
 * description that outputs the flow to all of those tunnels and any local
 * datapath port that corresponds to a virtual port belonging to that PortSet.
 * Finally, the wildcard flow, free of any MidoNet ID references, is pushed to
 * the FlowController.
 *
 * The DP Controller is responsible for managing overlay tunnels (see the
 * previous paragraph).
 *
 * The DP Controller notifies the Flow Validation Engine of any installed
 * wildcard flow so that the FVE may do appropriate indexing of flows (e.g. by
 * the ID of any virtual device that was traversed by the flow). The DP Controller
 * may receive requests from the FVE to invalidate specific wildcard flows; these
 * are passed on to the FlowController.
 */
class DatapathController() extends Actor with ActorLogWithoutPath {

    import DatapathController._
    import VirtualToPhysicalMapper._
    import context._

    implicit val requestReplyTimeout = new Timeout(1 second)

    @Inject
    val datapathConnection: OvsDatapathConnection = null

    @Inject
    val hostService: HostIdProviderService = null

    @Inject
    val interfaceScanner: InterfaceScanner = null

    var datapath: Datapath = null

    val localToVifPorts: mutable.Map[Short, UUID] = mutable.Map()
    val localTunnelPorts: mutable.Set[JShort] = mutable.Set()
    // Map of vport ID to local interface name - according to ZK.
    val vifPorts: mutable.Map[UUID, String] = mutable.Map()

    // the list of local ports
    val localDatapathPorts: mutable.Map[String, Port[_, _]] = mutable.Map()
    val zones = mutable.Map[UUID, TunnelZone[_, _]]()
    val zonesToTunnels: mutable.MultiMap[UUID, Port[_,_]] =
        new mutable.HashMap[UUID, mutable.Set[Port[_,_]]] with
            mutable.MultiMap[UUID, Port[_,_]]
    val tunnelsToHosts = mutable.Map[Integer, TZHostConfig[_,_]]()
    val portsDownPool: mutable.Map[String, Port[_,_]] = mutable.Map()

    // peerHostId -> { ZoneID -> Port[_,_] }
    val peerToTunnels = mutable.Map[UUID, mutable.Map[UUID, Port[_,_]]]()

    var pendingUpdateCount = 0

    var initializer: ActorRef = null
    var initialized = false
    var host: Host = null
    // If a Host message arrives while one is being processed, we stash it
    // in this variable. We don't use Akka's stash here, because we only
    // care about the last Host message (i.e. ignore intermediate messages).
    var nextHost: Host = null

    var portWatcher: Cancellable = null
    var portWatcherEnabled = true

    override def preStart() {
        super.preStart()
        context.become(DatapathInitializationActor)
    }

    protected def receive = null

    val DatapathInitializationActor: Receive = {

        /**
         * Initialization request message
         */
        case Initialize() =>
            initializer = sender
            log.info("Initialize from: " + sender)
            VirtualToPhysicalMapper.getRef() ! HostRequest(hostService.getHostId)

        case DisablePortWatcher() =>
            log.info("Disabling the port watching feature.")
            portWatcherEnabled = false

        case h: Host =>
            // If we already had the host info, process this after init.
            this.host match {
                case null =>
                    // Only set it if the datapath is known.
                    if (null != h.datapath) {
                        this.host = h
                        readDatapathInformation(h.datapath)
                    }
                case _ =>
                    this.nextHost = h
            }

        case _SetLocalDatapathPorts(datapathObj, ports) =>
            this.datapath = datapathObj
            ports.foreach { _ match {
                    case p: GreTunnelPort =>
                        deleteDatapathPort(self, p, None)
                    case p: CapWapTunnelPort =>
                        deleteDatapathPort(self, p, None)
                    case p: NetDevPort =>
                        deleteDatapathPort(self, p, None)
                    case p =>
                        log.debug("Keeping port {} found during " +
                            "initialization", p)
                        localDatapathPorts.put(p.getName, p)
                }
            }
            log.debug("Finished processing datapath's existing ports. " +
                "Pending updates {}", pendingUpdateCount)
            if (pendingUpdateCount == 0)
                completeInitialization

        /**
        * Handle personal create port requests
        */
        case newPortOp: CreatePortOp[Port[_, _]] if (sender == self) =>
            createDatapathPort(sender, newPortOp.port, newPortOp.tag)

        /**
         * Handle personal delete port requests
         */
        case delPortOp: DeletePortOp[Port[_, _]] if (sender == self) =>
            deleteDatapathPort(sender, delPortOp.port, delPortOp.tag)

        case opReply: PortOpReply[Port[_, _]] if (sender == self) =>
            handlePortOperationReply(opReply)

        case Messages.Ping(value) =>
            sender ! Messages.Pong(value)

        /**
         * Log unhandled messages.
         */
        case m =>
            log.info("(behaving as InitializationActor). Not handling message: " + m)
    }

    /**
     * Complete initialization and notify the actor that requested init.
     */
    private def completeInitialization {
        log.info("Initialization complete. Starting to act as a controller.")
        initialized = true
        become(DatapathControllerActor)
        FlowController.getRef() ! DatapathController.DatapathReady(datapath)
        for ((zoneId, zone) <- host.zones) {
            VirtualToPhysicalMapper.getRef() ! TunnelZoneRequest(zoneId)
        }
        if (portWatcherEnabled) {
            // schedule port requests.
            log.info("Starting to schedule the port link status updates.")
            portWatcher = system.scheduler.schedule(1 second, 2 seconds,
                self, CheckForPortUpdates(datapath.getName))
        }
        initializer ! InitializationComplete()
        log.info("Process the host's zones and vport bindings.")
        doDatapathPortsUpdate()
    }

    private def processNextHost() {
        if (null != nextHost && pendingUpdateCount == 0) {
            val oldZones = host.zones
            val newZones = nextHost.zones

            host = nextHost
            nextHost = null

            doDatapathPortsUpdate()
            doDatapathZonesUpdate(oldZones, newZones)
        }
    }

    private def doDatapathZonesUpdate(
            oldZones: Map[UUID, TZHostConfig[_, _]],
            newZones: Map[UUID, TZHostConfig[_, _]]) {
        val dropped = oldZones.keySet.diff(newZones.keySet)
        for (zone <- dropped) {
            VirtualToPhysicalMapper.getRef() ! TunnelZoneUnsubscribe(zone)
            dropTunnelsInZone(zone)
        }

        val added = newZones.keySet.diff(oldZones.keySet)
        for (zone <- added) {
            VirtualToPhysicalMapper.getRef() ! TunnelZoneRequest(zone)
        }
    }

    val DatapathControllerActor: Receive = {

        // When we get the initialization message we switch into initialization
        // mode and only respond to some messages.
        // When initialization is completed we will revert back to this Actor
        // loop for general message response
        case m: Initialize =>
            initialized = false
            become(DatapathInitializationActor)
            // In case there were some scheduled port update checks, cancel them.
            if (portWatcher != null) {
                portWatcher.cancel()
            }
            self ! m

        case h: Host =>
            this.nextHost = h
            processNextHost()

        case zoneMembers: ZoneMembers[_] =>
            if (!host.zones.contains(zoneMembers.zone)) {
                log.debug("Got ZoneMembers for zone:{} but I'm no " +
                    "longer subscribed", zoneMembers.zone)
            } else {
                log.debug("ZoneMembers: {}", zoneMembers)
                for (member <- zoneMembers.members) {
                    handleZoneChange(zoneMembers.zone,
                                     member.asInstanceOf[TZHostConfig[_,_]],
                                     HostConfigOperation.Added)
                }
            }

        case m: ZoneChanged[_] =>
            log.debug("ZoneChanged: {}", m)
            handleZoneChange(m)

        case newPortOp: CreatePortOp[Port[_, _]] =>
            createDatapathPort(sender, newPortOp.port, newPortOp.tag)

        case delPortOp: DeletePortOp[Port[_, _]] =>
            deleteDatapathPort(sender, delPortOp.port, delPortOp.tag)

        case opReply: PortOpReply[Port[_, _]] =>
            handlePortOperationReply(opReply)

        case AddWildcardFlow(flow, cookie, pktBytes, flowRemovalCallbacks, tags) =>
            handleAddWildcardFlow(flow, cookie, pktBytes, flowRemovalCallbacks,
                                    tags)

        case SendPacket(ethPkt, actions) =>
            handleSendPacket(ethPkt, actions)

        case PacketIn(wMatch, pktBytes, dpMatch, reason, cookie) =>
            handleFlowPacketIn(wMatch, pktBytes, dpMatch, reason, cookie)

        case Messages.Ping(value) =>
            sender ! Messages.Pong(value)

        case PortStatsRequest(portID) =>
            vifPorts.get(portID) match {
                case Some(portName) =>
                    datapathConnection.portsGet(portName, datapath, new Callback[Port[_,_]]{
                    def onSuccess(data: Port[_, _]) {
                        MonitoringActor.getRef() ! PortStats(portID, data.getStats)
                    }

                    def onTimeout() {
                    log.error("Timeout when retrieving port stats")
                }

                def onError(e: NetlinkException) {
                    log.error("Error retrieving port stats for port {}({}): {}", Array(portID, vifPorts.get(portID).get, e))
                }
              })

              case None =>
                  log.debug("Port was not found {}", portID)
            }

        case CheckForPortUpdates(datapathName: String) =>
            checkPortUpdates()

        case InterfacesUpdate(interfaces: JList[InterfaceDescription]) =>
            updateInterfaces(interfaces)

        case LocalTunnelInterfaceInfo() =>
            getLocalInterfaceTunnelPhaseOne(sender)

        case LocalInterfaceTunnelInfoFinal(caller : ActorRef, 
                interfaces: JList[InterfaceDescription]) =>
            getLocalInterfaceTunnelInfo(caller, interfaces)

    }

    def checkPortUpdates() {

        interfaceScanner.scanInterfaces(new Callback[JList[InterfaceDescription]] {
            def onError(e: NetlinkException) {
                log.error("Error while retrieving the interface status:" + e.getMessage)
            }

            def onTimeout() {
                log.error("Timeout while retrieving the interface status.")
            }

            def onSuccess(data: JList[InterfaceDescription]) {
                self ! InterfacesUpdate(data)
            }
        })
    }

    def updateInterfaces(interfaces : JList[InterfaceDescription]) {
        val interfacesSet = new HashSet[String]

        val deletedPorts = new HashSet[String]
        deletedPorts.addAll(localDatapathPorts.keySet)

        for (interface <- interfaces) {
            deletedPorts.remove(interface.getName)
            if (interface.isUp) {
                interfacesSet.add(interface.getName)
            }

            // interface went down.
            if (localDatapathPorts.contains(interface.getName) &&
                    !interface.isUp) {
                localDatapathPorts.get(interface.getName).get match {
                    case p: NetDevPort =>
                        log.info("Interface went down: {}", interface.getName)
                        updatePort(interface.getName, false);
                    case p: InternalPort =>
                        log.info("Interface went down: {}", interface.getName)
                        updatePort(interface.getName, false);
                    case default =>
                        log.error("port type not matched {}", default)
                }
            }
        }

        // this set contains the ports that have been deleted.
        // the behaviour is the same as if the port had gone down.
        deletedPorts.foreach{
            deletedPort =>
                localDatapathPorts.get(deletedPort).get match {
                    case p: NetDevPort =>
                        log.info("Interface was deleted: {}, {}",
                            Array(localDatapathPorts.get(deletedPort).get.getPortNo,deletedPort))
                        // delete the dp <-> port link
                        deleteDatapathPort(self, p, None)
                        // set port to inactive.
                        updatePort(p.getName, false);
                    case default =>
                        log.error("port type not matched {}", default)
            }
        }

        // remove all the local ports. The rest will be datapath ports that are not known to the system.
        // one of them might be a port that went up again.
        interfacesSet.removeAll(localDatapathPorts.keySet)
        interfacesSet.foreach( interface =>
            if (portsDownPool.contains(interface)) {
                val p: Port[_,_] = portsDownPool.get(interface).get
                log.info("Resurrecting a previously deleted port. {} {}", Array(p.getPortNo, p.getName))

                // recreate port in datapath.
                createDatapathPort(self, Ports.newNetDevPort(interface),
                    localToVifPorts.get(p.getPortNo.shortValue()))
                updatePort(p.getName, true);
            }
        );
    }

    def updatePort(portName : String, up: Boolean) {
        var port: Port[_,_] = null
        if (up) {
            port = portsDownPool.get(portName).get
            localDatapathPorts.put(portName, port)
            portsDownPool.remove(portName)
        } else {
            port = localDatapathPorts.get(portName).get
            portsDownPool.put(portName, port)
            localDatapathPorts.remove(portName)
        }

        val portVif = localToVifPorts.get(port.getPortNo.shortValue())
        if (portVif != None) {
            VirtualToPhysicalMapper.getRef() ! LocalPortActive(portVif.get, active = up)
        } else {
            // the port does not contain a known zookeeper id yet. Nothing to do.
            log.info(portName + " did not map to anything. Ignoring.")
        }
    }

    def newTunnelPort[HostType <: TZHostConfig[_,_]](
            source: HostType, target: HostType): Port[_,_] = {
        source match {
            case capwap: CapwapTunnelZoneHost =>
                val name = "tncpw%08X" format target.getIp.addressAsInt()
                Ports.newCapwapTunnelPort(name)
            case gre: GreTunnelZoneHost =>
                val name = "tngre%08X" format target.getIp.addressAsInt()
                Ports.newGreTunnelPort(name)
            case ipsec: IpsecTunnelZoneHost =>
                val name = "ipsec%08X" format target.getIp.addressAsInt()
                log.error("Tunnel host type not implemented: {}", source)
                null
            case _ =>
                log.error("Tunnel host config did not match: {}", source)
                null
        }
    }

    def handleZoneChange(m: ZoneChanged[_]) {
        val hostConfig = m.hostConfig.asInstanceOf[TZHostConfig[_, _]]
        handleZoneChange(m.zone, hostConfig, m.op)
    }

    def handleZoneChange(zone: UUID,
                         hostConfig: TZHostConfig[_,_],
                         op: HostConfigOperation.Value) {
        def _closeTunnel[HostType <: TZHostConfig[_,_]](peerConf: HostType) {
            peerToTunnels.get(peerConf.getId).foreach {
                mapping => mapping.get(zone).foreach {
                    case tunnelPort: Port[_,_] =>
                        log.debug("Need to close the tunnel with name: {}",
                            tunnelPort.getName)
                        deleteDatapathPort(self, tunnelPort, Some((peerConf, zone)))
                }
            }
        }

        def _openTunnel[HostType <: TZHostConfig[_,_]](peerConf: HostType) {
            val myConfig = host.zones(zone)
            val tunnelPort = newTunnelPort(myConfig, peerConf)
            tunnelPort.setOptions()
            val options = tunnelPort.getOptions.asInstanceOf[TunnelPortOptions[_]]
            options.setSourceIPv4(myConfig.getIp.addressAsInt())
            options.setDestinationIPv4(peerConf.getIp.addressAsInt())
            createDatapathPort(self, tunnelPort, Some((peerConf, zone)))
        }

        if (hostConfig.getId == host.id)
            return
        if (!host.zones.contains(zone))
            return

        hostConfig match {
            case peer: GreTunnelZoneHost if op == HostConfigOperation.Added =>
                log.info("Opening a tunnel port to {}", hostConfig)
                _openTunnel(peer)

            case peer: CapwapTunnelZoneHost if op == HostConfigOperation.Added =>
                log.info("Opening a tunnel port to {}", hostConfig)
                _openTunnel(peer)

            case peer: GreTunnelZoneHost if op == HostConfigOperation.Deleted =>
                log.info("Closing a tunnel port to {}", hostConfig)
                _closeTunnel(peer)

            case peer: CapwapTunnelZoneHost if op == HostConfigOperation.Deleted =>
                log.info("Closing a tunnel port to {}", hostConfig)
                _closeTunnel(peer)

            case _ =>
        }
    }

    def dropTunnelsInZone(zoneId: UUID) {
        zonesToTunnels.get(zoneId) foreach { tunnels =>
            log.info("dropping all tunnels in zone: {}", zoneId)
            for (port <- tunnels) {
                tunnelsToHosts.get(port.getPortNo) match {
                    case Some(tzhost: GreTunnelZoneHost) =>
                        deleteDatapathPort(self, port, Some((tzhost, zoneId)))
                    case Some(tzhost: CapwapTunnelZoneHost) =>
                        deleteDatapathPort(self, port, Some((tzhost, zoneId)))
                    case _ =>
                        log.error("Cannot find TZHost for port {} while "+
                            "dropping tunnels in zone {}", port.getPortNo, zoneId)
                }
            }
        }
    }

    def handleAddWildcardFlow(flow: WildcardFlow,
                              cookie: Option[Int],
                              pktBytes: Array[Byte],
                              flowRemovalCallbacks: ROSet[Callback0],
                              tags: ROSet[Any]) {
        val flowMatch = flow.getMatch
        val inPortUUID = flowMatch.getInputPortUUID

        // tags can be null
        val dpTags = new mutable.HashSet[Any]
        if (tags != null)
            dpTags ++= tags


        vifToLocalPortNumber(inPortUUID) match {
            case Some(portNo: Short) =>
                flowMatch
                    .setInputPortNumber(portNo)
                    .unsetInputPortUUID()
                // tag flow with short inPort to be able to perform
                // invalidation
                dpTags += FlowTagger.invalidateDPPort(portNo)
            case None =>
        }

        var flowActions = flow.getActions
        if (flowActions == null)
            flowActions = Nil

        translateActions(flowActions, Option(inPortUUID),
                         Option(dpTags), flow.getMatch) onComplete {
            case Right(actions) =>
                flow.setActions(actions.toList)
                FlowController.getRef() ! AddWildcardFlow(flow, cookie,
                    pktBytes,flowRemovalCallbacks, dpTags)
            case _ =>
                // TODO(pino): should we push a temporary drop flow instead?
                FlowController.getRef() ! AddWildcardFlow(flow, cookie,
                    pktBytes, flowRemovalCallbacks, dpTags)
        }
    }

    def translateActions(actions: Seq[FlowAction[_]],
                         inPortUUID: Option[UUID],
                         dpTags: Option[mutable.Set[Any]],
                         wMatch: WildcardMatch): Future[Seq[FlowAction[_]]] = {
        val translated = Promise[Seq[FlowAction[_]]]()

        // check for VRN port or portSet
        var vrnPort: Option[Either[UUID, UUID]] = None
        for (action <- actions) {
            action match {
                case s: FlowActionOutputToVrnPortSet if (vrnPort == None ) =>
                    vrnPort = Some(Right(s.portSetId))
                case p: FlowActionOutputToVrnPort if (vrnPort == None) =>
                    vrnPort = Some(Left(p.portId))
                case u: FlowActionUserspace =>
                    u.setUplinkPid(datapathConnection.getChannel.getLocalAddress.getPid)
                case _ =>
            }
        }

        vrnPort match {
            case Some(Right(portSet)) =>
                // we need to expand a port set

                val portSetFuture = ask(
                    VirtualToPhysicalMapper.getRef(),
                    PortSetRequest(portSet, update = false)).mapTo[PortSet]

                val bridgeFuture = ask(
                    VirtualTopologyActor.getRef(),
                    BridgeRequest(portSet, update = false)).mapTo[RCUBridge]

                portSetFuture map {
                    set => bridgeFuture onSuccess {
                        case br =>
                            // Don't include the input port in the expanded
                            // port set.
                            var outPorts = set.localPorts
                            inPortUUID foreach { p => outPorts -= p }
                            log.debug("Flooding on bridge {}. inPort: {}, " +
                                "local bridge ports: {}, " +
                                "remote hosts having ports on this bridge: {}",
                                br.id, inPortUUID, set.localPorts, set.hosts)
                            // add tag for flow invalidation
                            dpTags foreach { tags =>
                                tags += FlowTagger.invalidateBroadcastFlows(
                                    br.id, br.id)
                            }
                            val localPortFutures =
                                outPorts.toSeq map {
                                    portID => ask(VirtualTopologyActor.getRef(),
                                                  PortRequest(portID, false))
                                              .mapTo[client.Port[_]]
                                }
                            Future.sequence(localPortFutures) onComplete {
                                case Right(localPorts) =>
                                    applyOutboundFilters(localPorts,
                                        portSet, wMatch,
                                        { portIDs => translated.success(
                                            translateToDpPorts(
                                                actions, portSet,
                                                portsForLocalPorts(portIDs),
                                                Some(br.tunnelKey),
                                                tunnelsForHosts(set.hosts.toSeq),
                                                dpTags.orNull))
                                        })

                                case _ => log.error("Error getting " +
                                    "configurations of local ports of " +
                                    "PortSet {}", portSet)
                            }
                    }
                }

            case Some(Left(port)) =>
                // we need to translate a single port
                vifToLocalPortNumber(port) match {
                    case Some(localPort) =>
                        translated.success(
                            translateToDpPorts(actions, port, List(localPort),
                                None, Nil, dpTags.orNull))
                    case None =>
                        ask(VirtualTopologyActor.getRef(), PortRequest(port,
                            update = false)).mapTo[client.Port[_]] map {
                                case p: ExteriorPort[_] =>
                                    translated.success(translateToDpPorts(
                                            actions, port, Nil,
                                            Some(p.tunnelKey),
                                            tunnelsForHosts(List(p.hostID)),
                                            dpTags.orNull))
                        }
                }
            case None =>
                translated.success(actions)
        }
        translated.future
    }

    def translateToDpPorts(acts: Seq[FlowAction[_]], port: UUID,
                           localPorts: Seq[Short],
                           tunnelKey: Option[Long], tunnelPorts: Seq[Short],
                           dpTags: mutable.Set[Any]): Seq[FlowAction[_]] = {
        tunnelKey match {
            case Some(k) =>
                log.debug("Translating output actions for vport (or set) {}," +
                    " having tunnel key {}, and corresponding to local dp " +
                    "ports {}, and tunnel ports {}",
                    port, k, localPorts, tunnelPorts)

            case None =>
                log.debug("No tunnel key provided. Translating output " +
                    "action for vport {}, corresponding to local dp port {}",
                    port, localPorts)
        }
        // TODO(pino): when we detect the flow won't have output actions,
        // set the flow to expire soon so that we can retry.
        if (localPorts.length == 0 && tunnelPorts.length == 0)
            log.error("No local datapath ports or tunnels found. This flow " +
                "will be dropped because we cannot make Output actions.")
        val newActs = ListBuffer[FlowAction[_]]()
        var newTags = new mutable.HashSet[Any]

        var translatablePort = port

        var translatedActions = localPorts.map { id =>
            FlowActions.output(id).asInstanceOf[FlowAction[_]]
        }
        // add tag for flow invalidation
        localPorts.foreach{id =>
            newTags += FlowTagger.invalidateDPPort(id)
        }

        if (null != tunnelPorts && tunnelPorts.length > 0) {
            translatedActions = translatedActions ++ tunnelKey.map { key =>
                FlowActions.setKey(FlowKeys.tunnelID(key))
                    .asInstanceOf[FlowAction[_]]
            } ++ tunnelPorts.map { id =>
                FlowActions.output(id).asInstanceOf[FlowAction[_]]
            }
            tunnelPorts.foreach{id => newTags += FlowTagger.invalidateDPPort(id)}
        }

        for (act <- acts) {
            act match {
                case p: FlowActionOutputToVrnPort if (p.portId == translatablePort) =>
                    newActs ++= translatedActions
                    translatablePort = null
                    if (dpTags != null)
                        dpTags ++= newTags

                case p: FlowActionOutputToVrnPortSet if (p.portSetId == translatablePort) =>
                    newActs ++= translatedActions
                    translatablePort = null
                    if (dpTags != null)
                        dpTags ++= newTags

                // we only translate the first ones.
                case x: FlowActionOutputToVrnPort =>
                case x: FlowActionOutputToVrnPortSet =>

                case a => newActs += a
            }
        }

        newActs
    }

    def tunnelsForHosts(hosts: Seq[UUID]): Seq[Short] = {
        val tunnels = mutable.ListBuffer[Short]()

        def tunnelForHost(host: UUID): Option[Short] = {
            peerToTunnels.get(host).flatMap {
                mappings => mappings.values.headOption.map {
                    port => port.getPortNo.shortValue
                }
            }
        }

        for (host <- hosts)
            tunnels ++= tunnelForHost(host).toList

        tunnels
    }

    def portsForLocalPorts(localVrnPorts: Seq[UUID]): Seq[Short] = {
        localVrnPorts map {
            vifToLocalPortNumber(_) match {
                case Some(value) => value
                case None => null.asInstanceOf[Short]
            }
        }
    }

    def translateToLocalPort(acts: Seq[FlowAction[_]], port: UUID, localPort: Short): Seq[FlowAction[_]] = {
        val translatedActs = mutable.ListBuffer[FlowAction[_]]()

        for (act <- acts) {
            act match {
                case port: FlowActionOutputToVrnPort if (port.portId == port) =>
                    translatedActs += FlowActions.output(localPort)

                case port: FlowActionOutputToVrnPort =>
                    // this should not happen so we drop it
                case set: FlowActionOutputToVrnPortSet =>
                    // this should not happen so we drop it
                case action =>
                    translatedActs += action

            }
        }

        translatedActs
    }

    def vifToLocalPortNumber(vif: UUID): Option[Short] = {
        vifPorts.get(vif) match {
            case Some(tapName: String) =>
                localDatapathPorts.get(tapName) match {
                    case Some(p: Port[_, _]) => Some[Short](p.getPortNo.shortValue())
                    case _ => None
                }
            case _ => None
        }
    }

    /**
     * Once a port has been created/removed from the datapath, this method
     * adds/removes the port to the DatapathController's map of ports,
     * tells the VirtualToPhysicalMapper and installs/invalidates a flow to
     * match the port's tunnelKey.
     */
    private def finalizePortActivation(port: Port[_,_], vifId: UUID,
                                       active: Boolean) {
        def tellVtpm() {
            VirtualToPhysicalMapper.getRef() ! LocalPortActive(vifId, active)
        }

        if (active) {
            localToVifPorts.put(port.getPortNo.shortValue, vifId)
            log.debug("Port {} became active", port.getPortNo.shortValue())
        }
        else {
            localToVifPorts.remove(port.getPortNo.shortValue)
            log.debug("Port {} became inactive", port.getPortNo.shortValue())
        }
        port match {
            case netdev: NetDevPort => installTunnelKeyFlow(netdev, vifId, active)

            case internal: InternalPort => installTunnelKeyFlow(internal, vifId, active)

            case _ =>
                log.warning("local port activated, but it's not a " +
                    "NetDevPort nor Internal Port, I don't know what to do with it: {}", port)
        }
        tellVtpm()
    }

    private def installTunnelKeyFlow(port: Port[_, _], vifId: UUID, active: Boolean) {
        val clientPortFuture = VirtualTopologyActor.getRef() ?
            PortRequest(vifId, update = false)

        clientPortFuture.mapTo[client.ExteriorPort[_]] onComplete {
            case Right(exterior) =>
                // trigger invalidation. This is done regardless of
                // whether we are activating or deactivating:
                //
                //   + The case for invalidating on deactivation is
                //     obvious.
                //   + On activation we invalidate flows for this dp port
                //     number in case it has been reused by the dp: we
                //     want to start with a clean state.
                FlowController.getRef() ! FlowController.InvalidateFlowsByTag(
                    FlowTagger.invalidateDPPort(port.getPortNo.shortValue()))

                if (active) {
                    // packets for the port may have arrived before the
                    // port came up and made us install temporary drop flows.
                    // Invalidate them before adding the new flow
                    FlowController.getRef() ! FlowController.InvalidateFlowsByTag(
                        FlowTagger.invalidateByTunnelKey(exterior.tunnelKey))

                    addTaggedFlow(new WildcardMatch().setTunnelID(exterior.tunnelKey),
                        List(FlowActions.output(port.getPortNo.shortValue)),
                        tags = Set(FlowTagger.invalidateDPPort(port.getPortNo.shortValue())),
                        expiration = 0)
                    log.debug("Added flow for tunnelkey {}", exterior.tunnelKey)
                }

            case _ =>
                log.warning("local port activated, but it's not an " +
                    "ExteriorPort, I don't know what to do with it: {}",
                    port)

        }
    }

    private def addDropFlow(wMatch: WildcardMatch,
                        cookie: Option[Int] = None,
                        expiration: Long = 3000) {
        log.debug("adding drop flow for PacketIn match {}",
            wMatch)
        FlowController.getRef().tell(
            AddWildcardFlow(new WildcardFlow().setMatch(wMatch)
                .setIdleExpirationMillis(expiration),
                None, null, null, null))
    }

    private def addTaggedFlow(wMatch: WildcardMatch,
                        actions: Seq[FlowAction[_]],
                        tags: ROSet[Any],
                        cookie: Option[Int] = None,
                        pktBytes: Array[Byte] = null,
                        expiration: Long = 3000,
                        priority: Short = 0) {
        log.debug("adding flow with match {} with actions {}",
                  wMatch, actions)

        FlowController.getRef().tell(
                AddWildcardFlow(new WildcardFlow().setMatch(wMatch)
                                        .setIdleExpirationMillis(expiration)
                                        .setActions(actions)
                                        .setPriority(priority),
                                cookie,
                                if (actions == Nil) null else pktBytes,
                                null, tags))
    }

    def handleFlowPacketIn(wMatch: WildcardMatch, pktBytes: Array[Byte],
                           dpMatch: FlowMatch, reason: Packet.Reason,
                           cookie: Option[Int]) {

        wMatch.getInputPortNumber match {
            case port: JShort =>
                log.debug("PacketIn on port #{}", port)
                if (localToVifPorts.contains(port)) {
                    wMatch.setInputPortUUID(localToVifPorts(port))
                    SimulationController.getRef().tell(
                        PacketIn(wMatch, pktBytes, dpMatch, reason, cookie))
                    return
                } else if (localTunnelPorts.contains(port)) {
                    log.debug("PacketIn came from a tunnel port")
                    if (wMatch.getTunnelID == null) {
                        log.error("SCREAM: got a PacketIn on a tunnel port " +
                                  "and a wildcard match with no tunnel ID; " +
                                  "dropping all flows from tunnel port #{}",
                                  port)
                        addDropFlow(new WildcardMatch().setInputPort(port),
                                    cookie)
                        return
                    }

                    val portSetFuture = VirtualToPhysicalMapper.getRef() ?
                        PortSetForTunnelKeyRequest(wMatch.getTunnelID)

                    portSetFuture.mapTo[PortSet] onComplete {
                        case Right(portSet) if (portSet != null) =>
                            val action = new FlowActionOutputToVrnPortSet(portSet.id)
                            log.debug("tun => portSet, action: {}, portSet: {}",
                                action, portSet)
                            // egress port filter simulation
                            val localPortFutures =
                                portSet.localPorts.toSeq map {
                                    portID => ask(VirtualTopologyActor.getRef(),
                                                  PortRequest(portID, false))
                                              .mapTo[client.Port[_]]
                                }
                            Future.sequence(localPortFutures) onComplete {
                                // Take the outgoing filter for each port
                                // and apply it, checking for Action.ACCEPT.
                                case Right(localPorts) =>
                                    applyOutboundFilters(localPorts,
                                        portSet.id, wMatch,
                                        { portIDs =>
                                          val tags = mutable.Set[Any]()
                                          addTaggedFlow(wMatch,
                                             translateToDpPorts(List(action),
                                                portSet.id,
                                                portsForLocalPorts(portIDs),
                                                None, Nil, tags),
                                             tags, cookie, pktBytes)
                                        })
                                case _ => log.error("Error getting " +
                                    "configurations of local ports of " +
                                    "PortSet {}", portSet)
                            }

                        case _ =>
                            // for now, install a drop flow. We will invalidate
                            // it if the port comes up later on.
                            log.debug("PacketIn came from a tunnel port but " +
                                "the key does not map to any PortSet")
                            addTaggedFlow(new WildcardMatch().
                                            setTunnelID(wMatch.getTunnelID).
                                            setInputPort(port),
                                actions = Nil,
                                tags = Set(FlowTagger.invalidateByTunnelKey(
                                               wMatch.getTunnelID)),
                                cookie = cookie)
                    }

                } else {
                    // Otherwise, drop the flow. There's a port on the DP that
                    // doesn't belong to us and is receiving packets.
                    addTaggedFlow(new WildcardMatch().setInputPort(port),
                        actions = Nil,
                        tags = Set(FlowTagger.invalidateDPPort(port)),
                        cookie = cookie,
                        priority = 1000) // TODO(abel) use a constant here
                }

            case _ =>
                // Missing InputPortNumber. This should never happen.
                log.error("SCREAM: got a PacketIn that has no inPort number.",
                    wMatch)
        }

    }

    private def applyOutboundFilters(
                    localPorts: Seq[client.Port[_]],
                    portSetID: UUID,
                    pktMatch: WildcardMatch, thunk: Sequence[UUID] => Unit) {
        // Fetch all of the chains.
        val chainFutures = localPorts map { port =>
                if (port.outFilterID == null)
                    Promise.successful(null)
                else
                    ask(VirtualTopologyActor.getRef,
                        ChainRequest(port.outFilterID, false)).mapTo[Chain]
            }
        // Apply the chains.
        Future.sequence(chainFutures) onComplete {
            case Right(chains) =>
                val egressPorts = (localPorts zip chains) filter { portchain =>
                    val port = portchain._1
                    val chain = portchain._2
                    val fwdInfo = new EgressPortSetChainPacketContext(port.id)

                    // apply chain and check result is ACCEPT.
                    val result =
                        Chain.apply(chain, fwdInfo, pktMatch, port.id, true)
                            .action
                    if (result != RuleResult.Action.ACCEPT &&
                            result != RuleResult.Action.DROP &&
                            result != RuleResult.Action.REJECT)
                        log.error("Applying chain {} produced {}, not " +
                                  "ACCEPT, DROP, or REJECT", chain.id, result)
                    result == RuleResult.Action.ACCEPT
                }

                thunk(egressPorts map {portchain => portchain._1.id})

            case _ => log.error("Error getting chains for PortSet {}",
                                portSetID)
        }
    }

    def handleSendPacket(ethPkt: Ethernet, origActions: List[FlowAction[_]]) {
        log.debug("Sending packet {} with action list {}", ethPkt, origActions)
        if (null == origActions || origActions.size == 0) {
            // Empty action list drops the packet. No need to send to DP.
            return
        }
        translateActions(origActions, None, None,
                         WildcardMatches.fromEthernetPacket(ethPkt)) onComplete {
            case Right(actions) =>
                log.debug("Translated actions to action list {}", actions)
                val packet = new Packet().
                    setMatch(FlowMatches.fromEthernetPacket(ethPkt)).
                    setData(ethPkt.serialize).setActions(actions)
                datapathConnection.packetsExecute(datapath, packet,
                    new ErrorHandlingCallback[JBoolean] {
                        def onSuccess(data: JBoolean) {}

                        def handleError(ex: NetlinkException, timeout: Boolean) {
                            log.error(ex,
                                "Failed to send a packet {} due to {}", packet,
                                if (timeout) "timeout" else "error")
                        }
                    }
                )
            case _ =>
                log.error("Failed to translate actions {}", origActions)
        }
    }

    def handlePortOperationReply(opReply: PortOpReply[_]) {
        log.debug("Port operation reply: {}", opReply)

        pendingUpdateCount -= 1
        log.debug("Pending count for handlePortOperationReply {}", pendingUpdateCount)

        def _handleTunnelCreate(port: Port[_,_],
                                hConf: TZHostConfig[_,_], zone: UUID) {
            peerToTunnels.get(hConf.getId) match {
                case Some(tunnels) =>
                    tunnels.put(zone, port)
                    log.debug("handleTunnelCreate - added zone {} port {} to" +
                        "tunnels map", zone, port.getName)
                case None =>
                    val mapping = mutable.Map[UUID, Port[_,_]]()
                    mapping.put(zone, port)
                    peerToTunnels.put(hConf.getId, mapping)
                    log.debug("handleTunnelCreate - added peer port {}", hConf.getId)

            }
            tunnelsToHosts.put(port.getPortNo, hConf)
            zonesToTunnels.addBinding(zone, port)
            // trigger invalidation
            val tunnelPortNum: JShort = port.getPortNo.shortValue
            FlowController.getRef() ! FlowController.InvalidateFlowsByTag(
                FlowTagger.invalidateDPPort(tunnelPortNum))
            localTunnelPorts.add(tunnelPortNum)
            log.debug("Adding tunnel with port #{}", tunnelPortNum)
            context.system.eventStream.publish(
                new TunnelChangeEvent(this.host.zones.get(zone), hConf,
                    Some(tunnelPortNum),
                    TunnelChangeEventOperation.Established))
        }

        def _handleTunnelDelete(port: Port[_,_],
                                hConf: TZHostConfig[_,_], zone: UUID) {
            peerToTunnels.get(hConf.getId) match {
                case Some(zoneTunnelMap) =>
                    zoneTunnelMap.remove(zone)
                    if (zoneTunnelMap.size == 0) {
                        peerToTunnels.remove(hConf.getId)
                    }
                    // trigger invalidation
                    FlowController.getRef() ! FlowController.InvalidateFlowsByTag(
                        FlowTagger.invalidateDPPort(port.getPortNo.shortValue())
                    )

                case None =>
            }
            tunnelsToHosts.remove(port.getPortNo)
            zonesToTunnels.removeBinding(zone, port)
            localTunnelPorts.remove(port.getPortNo.shortValue)
            log.debug("Removing tunnel with port #{}",
                      port.getPortNo.shortValue)
            context.system.eventStream.publish(
                new TunnelChangeEvent(
                    host.zones.get(zone), hConf,
                    None, TunnelChangeEventOperation.Removed))
        }

        opReply match {

            case TunnelGreOpReply(p, PortOperation.Create, false, null,
                    Some((hConf: GreTunnelZoneHost, zone: UUID))) =>
                _handleTunnelCreate(p, hConf, zone)

            case TunnelCapwapOpReply(p, PortOperation.Create, false, null,
                    Some((hConf: CapwapTunnelZoneHost, zone: UUID))) =>
                _handleTunnelCreate(p, hConf, zone)

            case TunnelCapwapOpReply(p, PortOperation.Delete, false, null,
                    Some((hConf: CapwapTunnelZoneHost, zone: UUID))) =>
                _handleTunnelDelete(p, hConf, zone)

            case TunnelGreOpReply(p, PortOperation.Delete, false, null,
                    Some((hConf: GreTunnelZoneHost, zone: UUID))) =>
                _handleTunnelDelete(p, hConf, zone)

            case PortNetdevOpReply(p, PortOperation.Create, false, null, Some(vifId: UUID)) =>
                log.info("DP port created. Mapping created: {} -> {}", vifId,
                    p.getPortNo)
                finalizePortActivation(p, vifId, active = true)

            case PortNetdevOpReply(p, PortOperation.Delete, false, null, None) =>
                localToVifPorts.get(p.getPortNo.shortValue()) match {
                    case None =>
                    case Some(vif) =>
                        log.info("Mapping removed: {} -> {}", vif, p.getPortNo)
                        finalizePortActivation(p, vif, active = false)
                }

            //            case PortInternalOpReply(_,_,_,_,_) =>
            //            case TunnelPatchOpReply(_,_,_,_,_) =>
            case reply =>
        }

        if (opReply.error == null && !opReply.timeout) {
            opReply.port match {
                case p: Port[_,_] =>
                    context.system.eventStream.publish(
                        new DatapathPortChangedEvent(p, opReply.op))
            }

            opReply.port match {
                case p: GreTunnelPort =>     // do nothing
                case p: CapWapTunnelPort => // do nothing
                case p: PatchTunnelPort => // do nothing
                case p: Port[_,_] => opReply.op match {
                    case PortOperation.Create =>
                        localDatapathPorts.put(p.getName, p)
                    case PortOperation.Delete =>
                        localDatapathPorts.remove(p.getName)
                }
                case value =>
                    log.error("No match {}", value)
            }
        } else if (opReply.error != null) {
            log.warning("Failed to delete port: {} due to error: {}",
                opReply.port, opReply.error)
        } else if (opReply.timeout) {
            log.warning("Failed to delete port: {} due to timeout", opReply.port)
        }

        if (pendingUpdateCount == 0) {
            if (!initialized)
                completeInitialization
            else
                processNextHost()
        }
    }

    /**
     * Avoid calling this when there are already pending updates.
     * Don't call this during initialization.
     */
    private def doDatapathPortsUpdate() {
        val ports: Map[UUID, String] = host.ports
        log.info("Migrating local datapath to configuration {}", ports)
        log.info("Current known local ports: {}", localDatapathPorts)

        vifPorts.clear()
        // post myself messages to force the creation of missing ports
        val newTaps: mutable.Set[String] = mutable.Set()
        for ((vifId, tapName) <- ports) {
            vifPorts.put(vifId, tapName)
            newTaps.add(tapName)
            // new port
            if (!localDatapathPorts.contains(tapName)) {
                createDatapathPort(
                    self, Ports.newNetDevPort(tapName), Some(vifId))
            }
            // port is already tracked.
            else {
                val p = localDatapathPorts(tapName)
                val shortPortNum = p.getPortNo.shortValue()
                if(!localToVifPorts.contains(shortPortNum)) {
                    // The dpPort already existed but hadn't been mapped to a
                    // virtual port UUID. Map it now and notify that the
                    // vport is now active.
                    log.info("DP port exists. Mapping created: {} -> {}",
                        vifId, shortPortNum)
                    finalizePortActivation(p, vifId, active = true)
                }
            }
        }

        // find ports that need to be removed and post myself messages to
        // remove them
        for ((portName, portData) <- localDatapathPorts) {
            log.info("Looking at {} -> {}", portName, portData)
            if (!newTaps.contains(portName) && portName != datapath.getName) {
                portData match {
                    case p: NetDevPort =>
                        deleteDatapathPort(self, p, None)
                    case p: InternalPort =>
                        if (p.getPortNo != 0) {
                            deleteDatapathPort(self, p, None)
                        }
                    case default =>
                        log.error("port type not matched {}", default)
                }
            }
        }

        log.info("Pending updates {}", pendingUpdateCount)
        if (pendingUpdateCount == 0)
                processNextHost()
    }

    def createDatapathPort(caller: ActorRef, port: Port[_, _], tag: Option[AnyRef]) {
        if (caller == self)
            pendingUpdateCount += 1
        log.info("creating port: {} (by request of: {})", port, caller)

        datapathConnection.portsCreate(datapath, port,
            new ErrorHandlingCallback[Port[_, _]] {
                def onSuccess(data: Port[_, _]) {
                    sendOpReply(caller, data, tag, PortOperation.Create, null, timeout = false)
                }

                def handleError(ex: NetlinkException, timeout: Boolean) {
                    sendOpReply(caller, port, tag, PortOperation.Create, ex, timeout)
                }
            })
    }

    def deleteDatapathPort(caller: ActorRef, port: Port[_, _], tag: Option[AnyRef]) {
        if (caller == self)
            pendingUpdateCount += 1
        log.info("deleting port: {} (by request of: {})", port, caller)

        datapathConnection.portsDelete(port, datapath, new ErrorHandlingCallback[Port[_, _]] {
            def onSuccess(data: Port[_, _]) {
                sendOpReply(caller, data, tag, PortOperation.Delete, null, timeout = false)
            }

            def handleError(ex: NetlinkException, timeout: Boolean) {
                sendOpReply(caller, port, tag, PortOperation.Delete, ex, timeout = false)
            }
        })
    }

    private def sendOpReply(actor: ActorRef, port: Port[_, _], tag: Option[AnyRef],
                            op: PortOperation.Value,
                            ex: NetlinkException, timeout: Boolean) {
        port match {
            case p: InternalPort =>
                actor ! PortInternalOpReply(p, op, timeout, ex, tag)
            case p: NetDevPort =>
                actor ! PortNetdevOpReply(p, op, timeout, ex, tag)
            case p: PatchTunnelPort =>
                actor ! TunnelPatchOpReply(p, op, timeout, ex, tag)
            case p: GreTunnelPort =>
                actor ! TunnelGreOpReply(p, op, timeout, ex, tag)
            case p: CapWapTunnelPort =>
                actor ! TunnelCapwapOpReply(p, op, timeout, ex, tag)
        }
    }

    private def getLocalInterfaceTunnelPhaseOne(caller : ActorRef) {
        interfaceScanner.scanInterfaces(new Callback[JList[InterfaceDescription]] {
            def onError(e: NetlinkException) {
                log.error("Error while retrieving the interface status:" + e.getMessage)
            }

            def onTimeout() {
                log.error("Timeout while retrieving the interface status.")
            }

            def onSuccess(data: JList[InterfaceDescription]) {
                self ! LocalInterfaceTunnelInfoFinal(caller, data)
            }
        })
    }

    private def getLocalInterfaceTunnelInfo(caller: ActorRef, 
        interfaces : JList[InterfaceDescription]) {
        // First we would populate the data structure with tunnel info 
        // on all local interfaces
        var addrTunnelMapping = mutable.Map[Int, TunnelZone.Type]()
        // This next variable is the structure for return message
        var retInterfaceTunnelMap : mutable.MultiMap[InterfaceDescription, TunnelZone.Type] = 
            new mutable.HashMap[InterfaceDescription, mutable.Set[TunnelZone.Type]] with 
                mutable.MultiMap[InterfaceDescription, TunnelZone.Type]
        for ((zoneId, zoneConfig) <- host.zones) {
            if (zoneConfig.isInstanceOf[GreTunnelZoneHost]) {
                addrTunnelMapping.put(zoneConfig.getIp.addressAsInt, 
                                      TunnelZone.Type.Gre)
            } else if (zoneConfig.isInstanceOf[CapwapTunnelZoneHost]) {
                addrTunnelMapping.put(zoneConfig.getIp.addressAsInt, 
                                      TunnelZone.Type.Capwap)
            } else if (zoneConfig.isInstanceOf[IpsecTunnelZoneHost]) {
                addrTunnelMapping.put(zoneConfig.getIp.addressAsInt, 
                                      TunnelZone.Type.Ipsec)
            }
        }

        if (addrTunnelMapping.isEmpty == false) {
            var ipAddr : Int = 0
            log.debug("Host has some tunnel zone(s) configured")
            for (interface <- interfaces) {
                for (inetAddress <- interface.getInetAddresses()) {
                    // IPv6 alert: this assumes only IPv4
                    if (inetAddress.getAddress().length == 4) {
                        ipAddr = ByteBuffer.wrap(inetAddress.getAddress()).getInt
                        addrTunnelMapping.get(ipAddr) match {
                            case Some(tunnelType : TunnelZone.Type) =>
                                retInterfaceTunnelMap.addBinding(interface, tunnelType) 
                            case _ =>
                                log.debug("No match for any tunnel on local interface {}", inetAddress.toString())
                        }   
                    }
                }
            }
        }
        caller ! retInterfaceTunnelMap
    }

    /**
     * ONLY USE THIS DURING INITIALIZATION.
     * @param wantedDatapath
     */
    private def readDatapathInformation(wantedDatapath: String) {
        def handleExistingDP(dp: Datapath) {
            log.info("The datapath already existed. Flushing the flows.")
            datapathConnection.flowsFlush(dp,
                new ErrorHandlingCallback[JBoolean] {
                    def onSuccess(data: JBoolean) {}
                    def handleError(ex: NetlinkException, timeout: Boolean) {
                        log.error("Failed to flush the Datapath's flows!")
                    }
                }
            )
            // Query the datapath ports without waiting for the flush to exit.
            queryDatapathPorts(dp)
        }
        log.info("Wanted datapath: {}", wantedDatapath)

        val retryTask = new Runnable {
            def run() {
                readDatapathInformation(wantedDatapath)
            }
        }

        val dpCreateCallback = new ErrorHandlingCallback[Datapath] {
            def onSuccess(data: Datapath) {
                log.info("Datapath created {}", data)
                queryDatapathPorts(data)
            }

            def handleError(ex: NetlinkException, timeout: Boolean) {
                log.error(ex, "Datapath creation failure {}", timeout)
                context.system.scheduler.scheduleOnce(100 millis, retryTask)
            }
        }

        val dpGetCallback = new ErrorHandlingCallback[Datapath] {
            def onSuccess(dp: Datapath) {
                handleExistingDP(dp)
            }

            def handleError(ex: NetlinkException, timeout: Boolean) {
                if (timeout) {
                    log.error("Timeout while getting the datapath", timeout)
                    context.system.scheduler.scheduleOnce(100 millis, retryTask)
                } else if (ex != null) {
                    val errorCode: ErrorCode = ex.getErrorCodeEnum

                    if (errorCode != null &&
                        errorCode == NetlinkException.ErrorCode.ENODEV) {
                        log.info("Datapath is missing. Creating.")
                        datapathConnection.datapathsCreate(
                            wantedDatapath, dpCreateCallback)
                    }
                }
            }
        }

        datapathConnection.datapathsGet(wantedDatapath, dpGetCallback)
    }

    /**
     * ONLY USE THIS DURING INITIALIZATION.
     * @param datapath
     */
    private def queryDatapathPorts(datapath: Datapath) {
        log.info("Enumerating ports for datapath: " + datapath)
        datapathConnection.portsEnumerate(datapath,
            new ErrorHandlingCallback[JSet[Port[_, _]]] {
                def onSuccess(ports: JSet[Port[_, _]]) {
                    self ! _SetLocalDatapathPorts(datapath, ports.toSet)
                }

                // WARN: this is ugly. Normally we should configure the message error handling
                // inside the router
                def handleError(ex: NetlinkException, timeout: Boolean) {
                    context.system.scheduler.scheduleOnce(100 millis, new Runnable {
                        def run() {
                            queryDatapathPorts(datapath)
                        }
                    })
                }
            }
        )
    }

    /**
     * Called when the netlink library receives a packet in
     *
     * @param packet the received packet
     */
    private case class _PacketIn(packet: Packet)

    private case class _SetLocalDatapathPorts(datapath: Datapath, ports: Set[Port[_, _]])


}
