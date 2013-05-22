/*
 * Copyright 2012 Midokura Europe SARL
 */
package org.midonet.midolman.topology

import collection.{Set => ROSet, immutable, mutable, Iterable}
import collection.JavaConversions._
import java.util.UUID

import org.midonet.cluster.Client
import org.midonet.cluster.client.ArpCache
import org.midonet.midolman.FlowController
import org.midonet.midolman.config.MidolmanConfig
import org.midonet.midolman.layer3.{InvalidationTrie, Route, RoutingTable}
import org.midonet.midolman.simulation.{ArpTable, ArpTableImpl, Router}
import org.midonet.midolman.topology.RouterManager._
import org.midonet.midolman.topology.builders.RouterBuilderImpl
import org.midonet.packets.{IPAddr, IPv4, IPv4Addr, MAC}
import org.midonet.sdn.flows.WildcardMatch
import org.midonet.util.functors.Callback0


class RoutingTableWrapper(val rTable: RoutingTable) {
    import collection.JavaConversions._
    def lookup(wmatch: WildcardMatch): Iterable[Route] =
            // TODO (ipv6) de facto implementation for ipv4, that explains
            // the casts at this point.
            rTable.lookup(wmatch.getNetworkSourceIP.asInstanceOf[IPv4Addr],
                          wmatch.getNetworkDestinationIP.asInstanceOf[IPv4Addr])
}

object RouterManager {
    val Name = "RouterManager"

    case class TriggerUpdate(cfg: RouterConfig, arpCache: ArpCache,
                             rTable: RoutingTableWrapper)
    case class InvalidateFlows(addedRoutes: ROSet[Route],
                               deletedRoutes: ROSet[Route])

    case class AddTag(dstIp: IPAddr)

    case class RemoveTag(dstIp: IPAddr)

    // these msg are used for testing
    case class RouterInvTrieTagCountModified(dstIp: IPAddr, count: Int)
}

case class RouterConfig(inboundFilter: UUID = null,
                        outboundFilter: UUID = null)

trait TagManager {
    def addTag(dstIp: IPAddr)

    def getFlowRemovalCallback(dstIp: IPAddr): Callback0
}

class RouterManager(id: UUID, val client: Client, val config: MidolmanConfig)
        extends DeviceManager(id) {
    private var cfg: RouterConfig = null
    private var rTable: RoutingTableWrapper = null
    private var arpCache: ArpCache = null
    private var arpTable: ArpTable = null
    private var filterChanged = false
    // This trie is to store the tag that represent the ip destination to be
    // able to do flow invalidation properly when a route is added or deleted
    private val dstIpTagTrie: InvalidationTrie = new InvalidationTrie()
    // key is dstIp tag, value is the count
    private val tagToFlowCount: mutable.Map[IPAddr, Int]
                                = new mutable.HashMap[IPAddr, Int]

    override def chainsUpdated() {
        makeNewRouter()
    }

    private def makeNewRouter() {
        if (chainsReady && null != rTable && null != arpTable) {
            log.debug("Send an RCU router to the VTA")
            // Not using context.actorFor("..") because in tests it will
            // bypass the probes and make it harder to fish for these messages
            // Should this need to be decoupled from the VTA, the parent
            // actor reference should be passed in the constructor
            VirtualTopologyActor.getRef().tell(
                new Router(id, cfg, rTable, arpTable, inFilter, outFilter,
                    new TagManagerImpl))
        } else {
            log.debug("The chains aren't ready yet. ")
        }

        if (filterChanged) {
            VirtualTopologyActor.getRef() ! FlowController.InvalidateFlowsByTag(
                FlowTagger.invalidateFlowsByDevice(id))
        }
        filterChanged = false
    }

    override def preStart() {
        client.getRouter(id, new RouterBuilderImpl(id, self))
    }

    override def getInFilterID = {
        cfg match {
            case null => null
            case _ => cfg.inboundFilter
        }
    }

    override def getOutFilterID = {
        cfg match {
            case null => null
            case _ => cfg.outboundFilter
        }
    }

    private def invalidateFlowsByIp(ip: IPv4Addr) {
        FlowController.getRef() ! FlowController.InvalidateFlowsByTag(
            FlowTagger.invalidateByIp(id, ip))
    }

    override def receive = super.receive orElse {
        case TriggerUpdate(newCfg, newArpCache, newRoutingTable) =>
            log.debug("TriggerUpdate with {} {} {}",
                Array(newCfg, newArpCache, newRoutingTable))
            if (newCfg != cfg && cfg != null) {
                // the cfg of this router changed, invalidate all the flows
                filterChanged = true
            }
            cfg = newCfg
            if (arpCache == null && newArpCache != null) {
                arpCache = newArpCache
                arpTable = new ArpTableImpl(arpCache, config,
                    (ip: IPv4Addr, mac: MAC) => invalidateFlowsByIp(ip))
                arpTable.start()
            } else if (arpCache != newArpCache) {
                throw new RuntimeException("Trying to re-set the arp cache")
            }
            rTable = newRoutingTable
            configUpdated()

        case InvalidateFlows(addedRoutes, deletedRoutes) =>
            for (route <- deletedRoutes) {
                FlowController.getRef() ! FlowController.InvalidateFlowsByTag(
                    FlowTagger.invalidateByRoute(id, route.hashCode())
                )
            }
            for (route <- addedRoutes) {
                log.debug("Projecting added route {}", route)
                val subTree = dstIpTagTrie.projectRouteAndGetSubTree(route)
                val ipToInvalidate = InvalidationTrie.getAllDescendantsIpDestination(subTree)
                log.debug("Got the following ip destination to invalidate {}",
                    ipToInvalidate.map(ip => IPv4.fromIPv4Address(ip)))
                val it = ipToInvalidate.iterator()
                it.foreach(ip => FlowController.getRef() !
                    FlowController.InvalidateFlowsByTag(
                        FlowTagger.invalidateByIp(id, IPv4Addr.fromInt(ip))))
                }

        case AddTag(dstIp) =>
            // check if the tag is already in the map
            if (tagToFlowCount contains dstIp) {
                adjustMapValue(tagToFlowCount, dstIp)(_ + 1)
                log.debug("Increased count for tag ip {} count {}", dstIp,
                    tagToFlowCount(dstIp))
            } else {
                tagToFlowCount += (dstIp -> 1)
                dstIpTagTrie.addRoute(createSingleHostRoute(dstIp))
                log.debug("Added IP {} to invalidation trie", dstIp)
            }
            context.system.eventStream.publish(
                new RouterInvTrieTagCountModified(dstIp, tagToFlowCount(dstIp)))


        case RemoveTag(dstIp: IPAddr) =>
            if (!(tagToFlowCount contains dstIp)) {
                log.debug("{} is not in the invalidation trie, cannot remove it!",
                    dstIp)

            } else {
                if (tagToFlowCount(dstIp) == 1) {
                    // we need to remove the tag
                    tagToFlowCount.remove(dstIp)
                    dstIpTagTrie.deleteRoute(createSingleHostRoute(dstIp))
                    log.debug("Removed IP {} from invalidation trie", dstIp)
                } else {
                    adjustMapValue(tagToFlowCount, dstIp)(_ - 1)
                    log.debug("Decreased count for tag IP {} count {}", dstIp,
                        tagToFlowCount(dstIp))
                }
            }
            context.system.eventStream.publish(
                new RouterInvTrieTagCountModified(dstIp,
                    if (tagToFlowCount contains dstIp) tagToFlowCount(dstIp)
                    else 0))

    }

    def adjustMapValue[A, B](m: mutable.Map[A, B], k: A)(f: B => B) {
        m.update(k, f(m(k)))
    }

    def createSingleHostRoute(dstIP: IPAddr): Route = {
        val route: Route = new Route()
        route.setDstNetworkAddr(dstIP.toString)
        route.dstNetworkLength = 32
        route
    }

    private class TagManagerImpl extends TagManager {

        def addTag(dstIp: IPAddr) {
            self ! AddTag(dstIp)
        }

        def getFlowRemovalCallback(dstIp: IPAddr) = {
            new Callback0 {
                def call() {
                    self ! RemoveTag(dstIp)
                }
            }

        }
    }
}
