/*
 * Copyright 3012 Midokura Europe SARL
 */
package org.midonet.midolman.simulation

import java.util.UUID

import akka.dispatch.{Promise, Future, ExecutionContext}
import akka.actor.{ActorContext, ActorSystem}

import org.midonet.cluster.client.RouterPort
import org.midonet.midolman.layer3.Route
import org.midonet.midolman.logging.LoggerFactory
import org.midonet.midolman.rules.RuleResult
import org.midonet.midolman.topology.VirtualTopologyActor.PortRequest
import org.midonet.midolman.topology.VirtualTopologyActor.expiringAsk
import org.midonet.midolman.topology.{FlowTagger, RoutingTableWrapper, TagManager, RouterConfig}
import org.midonet.packets.{MAC, Unsigned, Ethernet, IPAddr}
import org.midonet.sdn.flows.WildcardMatch

/**
 * Defines the base Router device that is meant to be extended with specific
 * implementations for IPv4 and IPv6 that deal with version specific details
 * such as ARP vs. NDP.
 */
abstract class RouterBase[IP <: IPAddr]()(implicit context: ActorContext)
    extends Coordinator.Device {

    import Coordinator._

    val id: UUID
    val cfg: RouterConfig
    val rTable: RoutingTableWrapper[IP]
    val inFilter: Chain
    val outFilter: Chain
    val routerMgrTagger: TagManager

    val validEthertypes: Set[Short]
    val log = LoggerFactory.getSimulationAwareLog(this.getClass)(context.system.eventStream)

    val loadBalancer = new LoadBalancer(rTable)

    protected def unsupportedPacketAction: Action

    /**
     * Process the packet. Will validate first the ethertype and ensure that
     * traffic is not vlan-tagged.
     *
     * @param pktContext The context for the simulation of this packet's
     *                   traversal of the virtual network. Use the context to
     *                   subscribe for notifications on the removal of any
     *                   resulting flows, or to tag any resulting flows for
     *                   indexing.
     * @param ec         (implicit)
     * @param actorSystem (implicit)
     * @return An instance of Action that reflects what the device would do
     *         after handling this packet (e.g. drop it, consume it, forward it)
     */
    override def process(pktContext: PacketContext)
                        (implicit ec: ExecutionContext,
                                  actorSystem: ActorSystem): Future[Action] = {
        implicit val packetContext = pktContext

        if (!pktContext.wcmatch.getVlanIds.isEmpty) {
            log.info("Dropping VLAN tagged traffic")
            return Promise.successful(DropAction())
        }

        if (!validEthertypes.contains(pktContext.wcmatch.getEtherType)) {
            log.info("Dropping unsupported EtherType {}",
                      pktContext.wcmatch.getEtherType)
            return Promise.successful(unsupportedPacketAction)
        }

        getRouterPort(pktContext.inPortId, pktContext.expiry) flatMap {
            case null =>
                log.debug("Router - in port {} was null", pktContext.inPortId)
                Promise.successful(DropAction())
            case inPort =>
                preRouting(inPort)
        }
    }

    private def applyIngressChain(inPort: RouterPort[_])
                                 (implicit ec: ExecutionContext,
                                  pktContext: PacketContext,
                                  actorSystem: ActorSystem): Option[Action] = {

        // Apply the pre-routing (ingress) chain
        pktContext.outPortId = null // input port should be set already
        val preRoutingResult = Chain.apply(inFilter, pktContext,
                pktContext.wcmatch, id, false)

        preRoutingResult.action match {
            case RuleResult.Action.ACCEPT => // pass through
            case RuleResult.Action.DROP =>
                return Some(DropAction())
            case RuleResult.Action.REJECT =>
                sendIcmpUnreachableProhibError(inPort, pktContext.wcmatch,
                    pktContext.frame)
                return Some(DropAction())
            case other =>
                log.error("Pre-routing for {} returned an action which was {}, " +
                    "not ACCEPT, DROP, or REJECT.", id, preRoutingResult.action)
                return Some(ErrorDropAction)
        }

        if (preRoutingResult.pmatch ne pktContext.wcmatch) {
            log.error("Pre-routing for {} returned a different match obj.", id)
            Some(ErrorDropAction)
        }
        None
    }


    private def preRouting(inPort: RouterPort[_])
                          (implicit pktContext: PacketContext,
                                    ec: ExecutionContext,
                                    actorSystem: ActorSystem): Future[Action] ={

        val hwDst = pktContext.wcmatch.getEthernetDestination
        if (Ethernet.isBroadcast(hwDst)) {
            log.debug("Received an L2 broadcast packet.")
            return Promise.successful(handleL2Broadcast(inPort))
        }

        if (hwDst != inPort.portMac) { // Not addressed to us, log.warn and drop
            log.warning("{} neither broadcast nor inPort's MAC ({})", hwDst,
                inPort.portMac)
            return Promise.successful(DropAction())
        }

        pktContext.addFlowTag(FlowTagger.invalidateFlowsByDevice(id))
        handleNeighbouring(inPort) match {
            case Some(a: Action) => return Promise.successful(a)
            case None =>
        }

        applyIngressChain(inPort) match {
            case Some(a: Action) => return Promise.successful(a)
            case None =>
        }

        routing(inPort)

    }

    private def routing(inPort: RouterPort[_])
                       (implicit ec: ExecutionContext,
                                 context: PacketContext,
                                 actorSystem: ActorSystem): Future[Action] = {

        val frame = context.frame
        val wcmatch = context.wcmatch
        val dstIP = context.wcmatch.getNetworkDestinationIP

        def applyTimeToLive: Option[Action] = {
            /* TODO(guillermo, pino): Have WildcardMatch take a DecTTLBy instead,
             * so that there need only be one sim. run for different TTLs.  */
            if (wcmatch.getNetworkTTL != null) {
                val ttl = Unsigned.unsign(wcmatch.getNetworkTTL)
                if (ttl <= 1) {
                    sendIcmpTimeExceededError(inPort, wcmatch, frame)
                    return Some(DropAction())
                } else {
                    context.wcmatch.setNetworkTTL((ttl - 1).toByte)
                    return None
                }
            }

            None
        }

        def applyRoutingTable: (Route, Action) = {
            val rt: Route = loadBalancer.lookup(wcmatch)

            if (rt == null) {
                // No route to network
                log.debug("Route lookup: No route to network (dst:{}), {}",
                    dstIP, rTable.rTable)
                sendIcmpUnreachableNetError(inPort, wcmatch, frame)
                return (rt, DropAction())
            }

            val action = rt.nextHop match {
                case Route.NextHop.LOCAL =>
                    if (isIcmpEchoRequest(wcmatch)) {
                        log.debug("got ICMP echo")
                        sendIcmpEchoReply(wcmatch, frame, context.expiry)
                        ConsumedAction
                    } else {
                        DropAction(temporary = true)
                    }

                case Route.NextHop.BLACKHOLE =>
                    log.debug("Dropping packet, BLACKHOLE route (dst:{})",
                        wcmatch.getNetworkDestinationIP)
                    DropAction(temporary = true)

                case Route.NextHop.REJECT =>
                    sendIcmpUnreachableProhibError(inPort, wcmatch, frame)
                    log.debug("Dropping packet, REJECT route (dst:{})",
                        wcmatch.getNetworkDestinationIP)
                    DropAction()

                case Route.NextHop.PORT =>
                    if (rt.nextHopPort == null) {
                        log.error("Routing table lookup for {} forwarded to port " +
                            "null.", dstIP)
                        // TODO(pino): should we remove this route?
                        DropAction()
                    } else {
                        ToPortAction(rt.nextHopPort)
                    }

                case _ =>
                    log.error("Routing table lookup for {} returned invalid " +
                        "nextHop of {}", dstIP, rt.nextHop)
                    // rt.nextHop is invalid. The only way the simulation result
                    // would change is if there are other matching routes that are
                    // 'sane'. If such routes were created, this flow will be
                    // invalidated. Thus, we can return DropAction and not
                    // ErrorDropAction
                    DropAction()
            }

            (rt, action)
        }

        def applyTagsForRoute(routeResult: (Route, Action)) = routeResult match {
            case (route, DropAction(true)) =>
                routeResult
            //case (route, ConsumedAction) =>
            //    routeResult
            case (route, action) =>
                /* We don't want to tag a temporary flow (e.g. created by a
                 * BLACKHOLE route), and we do that to avoid excessive interaction
                 * with the RouterManager, who needs to keep track of every
                 * IP address the router gives to it.
                 */

                // tag using this route
                if (route != null)
                    context.addFlowTag(FlowTagger.invalidateByRoute(id, route.hashCode()))

                // tag using the destination IP
                context.addFlowTag(FlowTagger.invalidateByIp(id, dstIP))
                // pass tag to the RouterManager so it'll be able to invalidate the flow
                routerMgrTagger.addTag(dstIP)
                // register the tag removal callback
                context.addFlowRemovedCallback(
                    routerMgrTagger.getFlowRemovalCallback(dstIP))
                routeResult

            case _ =>
                routeResult
        }

        (applyTimeToLive match {
            case Some(action) =>
                (null, action)
            case None =>
                applyTagsForRoute(applyRoutingTable)
        }) match {
            case (rt, ToPortAction(outPortId)) =>
                getRouterPort(outPortId, context.expiry) flatMap {
                    case null =>
                        Promise.successful(ErrorDropAction)
                    case outPort =>
                        postRouting(inPort, outPort, rt, context)
                }

            case (rt, action) =>
                Promise.successful(action)
        }
    }

    // POST ROUTING
    private def postRouting(inPort: RouterPort[_], outPort: RouterPort[_],
                            rt: Route, pktContext: PacketContext)
                           (implicit ec: ExecutionContext,
                            actorSystem: ActorSystem): Future[Action] = {

        implicit val packetContext = pktContext

        val pMatch = pktContext.wcmatch
        val pFrame = pktContext.frame

        pktContext.outPortId = outPort.id
        val postRoutingResult =
            Chain.apply(outFilter, pktContext, pMatch, id, false)

        postRoutingResult.action match {
            case RuleResult.Action.ACCEPT => // pass through
            case RuleResult.Action.DROP =>
                log.debug("PostRouting DROP rule")
                return Promise.successful(DropAction())
            case RuleResult.Action.REJECT =>
                log.debug("PostRouting REJECT rule")
                sendIcmpUnreachableProhibError(inPort, pMatch, pFrame)
                return Promise.successful(DropAction())
            case other =>
                log.error("Post-routing for {} returned {} which was not " +
                    "ACCEPT, DROP or REJECT.", id, other)
                return Promise.successful(ErrorDropAction)
        }

        if (postRoutingResult.pmatch ne pMatch) {
            log.error("Post-routing for {} returned a different match obj.", id)
            return Promise.successful(ErrorDropAction)
        }

        if (pMatch.getNetworkDestinationIP == outPort.portAddr.getAddress) {
            log.error("Got a packet addressed to a port without a LOCAL route")
            return Promise.successful(DropAction())
        }

        // Set HWDst
        val macFuture = getNextHopMac(outPort, rt,
                              pMatch.getNetworkDestinationIP.asInstanceOf[IP],
                              pktContext.expiry)

        macFuture map {
            case null =>
                if (rt.nextHopGateway == 0 || rt.nextHopGateway == -1) {
                    log.debug("icmp host unreachable, host mac unknown")
                    sendIcmpUnreachableHostError(inPort, pMatch, pFrame)
                } else {
                    log.debug("icmp net unreachable, gw mac unknown")
                    sendIcmpUnreachableNetError(inPort, pMatch, pFrame)
                }
                ErrorDropAction
            case nextHopMac =>
                log.debug("routing packet to {}", nextHopMac)
                pMatch.setEthernetSource(outPort.portMac)
                pMatch.setEthernetDestination(nextHopMac)
                new ToPortAction(rt.nextHopPort)
        }

    }

    final protected def getRouterPort(portID: UUID, expiry: Long)
                                     (implicit actorSystem: ActorSystem,
                                      pktContext: PacketContext):
                                     Future[RouterPort[_]] =
        expiringAsk(PortRequest(portID, update = false), expiry)
            .mapTo[RouterPort[_]]

    // Auxiliary, IP version specific abstract methods.

    /**
     * Given a route and a destination address, return the MAC address of
     * the next hop (or the destination's if it's a link-local destination)
     *
     * @param rt Route that the packet will be sent through
     * @param ipDest Final destination of the packet to be sent
     * @param expiry
     * @param ec
     * @return
     */
    protected def getNextHopMac(outPort: RouterPort[_], rt: Route,
                                         ipDest: IP, expiry: Long)
                                        (implicit ec: ExecutionContext,
                                         actorSystem: ActorSystem,
                                         pktContext: PacketContext): Future[MAC]

    /**
     * Will be called whenever an ICMP unreachable is needed for the given
     * IP version.
     */
    protected def sendIcmpUnreachableProhibError(inPort: RouterPort[_],
                                                          wMatch: WildcardMatch,
                                                          frame: Ethernet)
                                                (implicit ec: ExecutionContext,
                                                 actorSystem: ActorSystem,
                                                 originalPktContex: PacketContext)

    /**
     * Will be called whenever an ICMP Unreachable network is needed for the
     * given IP version.
     */
    protected def sendIcmpUnreachableNetError(inPort: RouterPort[_],
                                                       wMatch: WildcardMatch,
                                                       frame: Ethernet)
                                             (implicit ec: ExecutionContext,
                                              actorSystem: ActorSystem,
                                              originalPktContex: PacketContext)

    /**
     * Will be called whenever an ICMP Unreachable host is needed for the
     * given IP version.
     */
    protected def sendIcmpUnreachableHostError(inPort: RouterPort[_],
                                                        wMatch: WildcardMatch,
                                                        frame: Ethernet)
                                              (implicit ec: ExecutionContext,
                                               actorSystem: ActorSystem,
                                               originalPktContex: PacketContext)
    /**
     * Will be called whenever an ICMP Time Exceeded is needed for the given
     * IP version.
     */
    protected def sendIcmpTimeExceededError(inPort: RouterPort[_],
                                                     wMatch: WildcardMatch,
                                                     frame: Ethernet)
                                           (implicit ec: ExecutionContext,
                                            actorSystem: ActorSystem,
                                            originalPktContex: PacketContext)

    /**
     * Will be called to construct an ICMP echo reply for an ICMP echo reply
     * contained in the given packet.
     */
    protected def sendIcmpEchoReply(ingressMatch: WildcardMatch,
                                             packet: Ethernet, expiry: Long)
                                            (implicit ec: ExecutionContext,
                                             actorSystem: ActorSystem,
                                             packetContext: PacketContext)

    /**
     * Will be called from the pre-routing process immediately after receiving
     * the frame, if Ethernet.isBroadcast(hwDst).
     */
    protected def handleL2Broadcast(inPort: RouterPort[_])
                                   (implicit pktContext: PacketContext,
                                    ec: ExecutionContext,
                                    actorSystem: ActorSystem): Action

    /**
     * This method will be executed after basic L2 processing is done,
     * including handling broadcasts and reacting to frames not addressed to
     * our MAC.
     */
    protected def handleNeighbouring(inPort: RouterPort[_])
                                 (implicit ec: ExecutionContext,
                                  pktContext: PacketContext,
                                  actorSystem: ActorSystem): Option[Action]

    protected def isIcmpEchoRequest(mmatch: WildcardMatch): Boolean

}

