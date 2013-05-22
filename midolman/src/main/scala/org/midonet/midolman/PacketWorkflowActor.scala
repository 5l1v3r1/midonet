// Copyright 2013 Midokura Inc.

package org.midonet.midolman

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.collection.{Set => ROSet}
import scala.compat.Platform
import akka.actor._
import akka.dispatch.{Promise, Future}
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import akka.util.duration._
import java.lang.{Integer => JInteger}
import java.util.UUID

import org.midonet.cache.Cache
import org.midonet.midolman.DeduplicationActor._
import org.midonet.midolman.FlowController.FlowAdded
import org.midonet.midolman.FlowController.AddWildcardFlow
import org.midonet.midolman.datapath.{FlowActionOutputToVrnPortSet,
        ErrorHandlingCallback}
import org.midonet.midolman.logging.ActorLogWithoutPath
import org.midonet.midolman.simulation.{DhcpImpl, Coordinator}
import org.midonet.midolman.topology.VirtualToPhysicalMapper.
        PortSetForTunnelKeyRequest
import org.midonet.midolman.topology.VirtualTopologyActor.PortRequest
import org.midonet.midolman.topology.rcu.PortSet
import org.midonet.midolman.topology.{
        FlowTagger, VirtualTopologyActor, VirtualToPhysicalMapper}
import org.midonet.cluster.{DataClient, client}
import org.midonet.netlink.{Callback => NetlinkCallback}
import org.midonet.netlink.exceptions.NetlinkException
import org.midonet.netlink.exceptions.NetlinkException.ErrorCode
import org.midonet.packets.{DHCP, UDP, IPv4, Ethernet, Unsigned}
import org.midonet.odp._
import org.midonet.odp.flows.FlowAction
import org.midonet.odp.protos.OvsDatapathConnection
import org.midonet.odp.Packet.Reason.FlowActionUserspace
import org.midonet.util.functors.Callback0
import org.midonet.util.throttling.ThrottlingGuard
import org.midonet.sdn.flows.{WildcardFlow, WildcardMatch, WildcardFlowBuilder}


object PacketWorkflowActor {
    case class Start()

    case class PacketIn(wMatch: WildcardMatch,
                        eth: Ethernet,
                        dpMatch: FlowMatch,
                        reason: Packet.Reason,
                        cookie: Int)

    sealed trait SimulationAction

    case class NoOp() extends SimulationAction

    case class ErrorDrop() extends SimulationAction

    case class SendPacket(actions: List[FlowAction[_]]) extends SimulationAction

    case class AddVirtualWildcardFlow(flow: WildcardFlowBuilder,
                                      flowRemovalCallbacks: ROSet[Callback0],
                                      tags: ROSet[Any]) extends SimulationAction
}

class PacketWorkflowActor(
        protected val datapathConnection: OvsDatapathConnection,
        protected val dpState: DatapathState,
        datapath: Datapath,
        dataClient: DataClient,
        connectionCache: Cache,
        packet: Packet,
        cookieOrEgressPort: Either[Int, UUID],
        throttlingGuard: ThrottlingGuard) extends Actor with
            ActorLogWithoutPath with FlowTranslatingActor with
            UserspaceFlowActionTranslator {

    import PacketWorkflowActor._
    import context._

    // TODO marc get config from parent actors.
    def timeout = 5000 //config.getArpTimeoutSeconds * 1000

    val cookie = cookieOrEgressPort match {
        case Left(c) => Some(c)
        case Right(_) => None
    }
    val egressPort = cookieOrEgressPort match {
        case Right(e) => Some(e)
        case Left(_) => None
    }

    implicit val requestReplyTimeout = new Timeout(1 second)

    val cookieStr: String = "[cookie:" + cookie.getOrElse("No Cookie") + "]"

    override def preStart() {
        throttlingGuard.tokenIn()
    }

    override def postStop() {
        throttlingGuard.tokenOut()
    }

    def receive = LoggingReceive {
        case Start() =>
            log.debug("Initiating processing of packet {}", cookieStr)
            val workflowFuture = cookie match {
                case Some(cook) if (packet.getReason == FlowActionUserspace) =>
                    log.debug("Simulating packet addressed to userspace {}",
                              cookieStr)
                    doSimulation()

                case Some(cook) =>
                    FlowController.queryWildcardFlowTable(packet.getMatch) match {
                        case Some(wildFlow) =>
                            log.debug("Packet {} matched a wildcard flow", cookieStr)
                            handleWildcardTableMatch(wildFlow, cook)
                        case None =>
                            val wildMatch = WildcardMatch.fromFlowMatch(packet.getMatch)
                            Option(wildMatch.getTunnelID) match {
                                case Some(tunnelId) =>
                                    log.debug("Packet {} addressed to a port set", cookieStr)
                                    handlePacketToPortSet(cook)
                                case None =>
                                    /** QUESTION: do we need another de-duplication
                                     *  stage here to avoid e.g. two micro-flows that
                                     *  differ only in TTL from going to the simulation
                                     *  stage? */
                                    log.debug("Simulating packet {}", cookieStr)
                                    doSimulation()
                            }
                    }

                case None =>
                    log.debug("Simulating generated packet")
                    doSimulation()
            }
            workflowFuture onComplete {
                case _ =>
                    log.debug("Packet with {} processed, stopping actor", cookieStr)
                    self ! PoisonPill
            }
    }

    private def noOpCallback = new NetlinkCallback[Flow] {
        def onSuccess(dpFlow: Flow) {}
        def onTimeout() {}
        def onError(ex: NetlinkException) {}
    }

    private def flowAddedCallback(cookie: Int,
                                  promise: Promise[Boolean],
                                  flow: Flow,
                                  newWildFlow: Option[WildcardFlowBuilder] = None,
                                  tags: ROSet[Any] = Set.empty,
                                  removalCallbacks: ROSet[Callback0] = Set.empty) =
        new NetlinkCallback[Flow] {
            def onSuccess(dpFlow: Flow) {
                log.debug("Successfully created flow for {}", cookieStr)
                newWildFlow match {
                    case None =>
                        FlowController.getRef() ! FlowAdded(dpFlow)
                    case Some(wf) =>
                        FlowController.getRef() !
                            AddWildcardFlow(wf.build, Some(dpFlow), removalCallbacks, tags)
                }
                DeduplicationActor.getRef() ! ApplyFlow(dpFlow.getActions, Some(cookie))
                promise.success(true)
            }

            def onTimeout() {
                log.warning("Flow creation for {} timed out, deleting", cookieStr)
                datapathConnection.flowsDelete(datapath, flow, noOpCallback)
                promise.success(true)
            }

            def onError(ex: NetlinkException) {
                if (ex.getErrorCodeEnum == ErrorCode.EEXIST) {
                    log.info("File exists while adding flow for {}", cookieStr)
                    DeduplicationActor.getRef() !
                        ApplyFlow(flow.getActions, Some(cookie))
                    promise.success(true)
                } else {
                    // NOTE(pino) - it'd be more correct to execute the
                    // packets with the actions found in the flow that
                    // failed to install  ...but, if the cause of the error
                    // is a busy netlink channel then this policy is more
                    // sensible.
                    log.error("Error {} while adding flow for {}. Dropping packets.",
                        ex, cookieStr)
                    DeduplicationActor.getRef() ! ApplyFlow(Seq.empty, Some(cookie))
                    promise.failure(ex)
                }
            }
        }

    private def addTranslatedFlow(wildFlow: WildcardFlowBuilder,
                                  tags: ROSet[Any] = Set.empty,
                                  removalCallbacks: ROSet[Callback0] = Set.empty,
                                  expiration: Long = 3000,
                                  priority: Short = 0): Future[Boolean] = {

        val flowPromise = Promise[Boolean]()(system.dispatcher)
        cookie match {
            case Some(cook) if (packet.getMatch.isUserSpaceOnly) =>
                val dpFlow = new Flow().setActions(wildFlow.getActions).
                                        setMatch(packet.getMatch)
                FlowController.getRef() !
                    AddWildcardFlow(wildFlow.build, Some(dpFlow), removalCallbacks, tags)
                DeduplicationActor.getRef() ! ApplyFlow(dpFlow.getActions, cookie)
                flowPromise.success(true)
            case Some(cook) if (!packet.getMatch.isUserSpaceOnly) =>
                val dpFlow = new Flow().setActions(wildFlow.getActions).
                                        setMatch(packet.getMatch)
                log.debug("Adding wildcard flow {} for {}", wildFlow, cookieStr)
                datapathConnection.flowsCreate(datapath, dpFlow,
                    flowAddedCallback(cook, flowPromise, dpFlow,
                                      Some(wildFlow), tags, removalCallbacks))
            case _ =>
                log.debug("Adding wildcard flow only for {}: {}", cookieStr, wildFlow)
                FlowController.getRef() !
                    AddWildcardFlow(wildFlow.build, None, removalCallbacks, tags)
                flowPromise.success(true)
        }

        log.debug("Executing packet {}", cookieStr)
        val execPromise = executePacket(wildFlow.getActions)

        val futures = Future.sequence(List(flowPromise, execPromise))
        futures map { _ => true } fallbackTo { Promise.successful(false) }
    }

    private def addTranslatedFlowForActions(actions: Seq[FlowAction[_]],
                                            tags: ROSet[Any] = Set.empty,
                                            removalCallbacks: ROSet[Callback0] = Set.empty,
                                            expiration: Int = 3000,
                                            priority: Short = 0): Future[Boolean] = {

        val wildFlow = new WildcardFlowBuilder().
            setMatch(WildcardMatch.fromFlowMatch(packet.getMatch)).
            setIdleExpirationMillis(expiration).
            setActions(actions.toList).
            setPriority(priority)

        addTranslatedFlow(wildFlow, tags, removalCallbacks, expiration, priority)
    }

    private def executePacket(actions: Seq[FlowAction[_]]): Future[Boolean] = {
        packet.setActions(actions.asJava)
        if (packet.getMatch.isUserSpaceOnly) {
            log.debug("Applying userspace actions to packet {}", cookieStr)
            applyActionsAfterUserspaceMatch(packet)
        }

        if (actions != null && actions.size > 0) {
            val promise = Promise[Boolean]()(system.dispatcher)
            datapathConnection.packetsExecute(
                datapath, packet,
                new ErrorHandlingCallback[java.lang.Boolean] {
                    def onSuccess(data: java.lang.Boolean) {
                        log.debug("Packet execute success {}", cookieStr)
                        promise.success(true)
                    }

                    def handleError(ex: NetlinkException, timeout: Boolean) {
                        log.error(ex,
                            "Failed to send a packet {} {} due to {}",
                            cookieStr, packet,
                            if (timeout) "timeout" else "error")
                        promise.failure(ex)
                    }
                })
            promise
        } else {
            Promise.successful(true)
        }
    }

    private def handleWildcardTableMatch(wildFlow: WildcardFlow, cookie: Int):
            Future[Boolean] = {

        val dpFlow = new Flow().
            setActions(wildFlow.getActions).
            setMatch(packet.getMatch)

        val flowPromise = Promise[Boolean]()(system.dispatcher)
        if (packet.getMatch.isUserSpaceOnly) {
            log.debug("Won't add flow with userspace match {}", packet.getMatch)
            DeduplicationActor.getRef() ! ApplyFlow(dpFlow.getActions, Some(cookie))
            flowPromise.success(true)
        } else {
            log.debug("Creating dp flow for wildcard table match {}", cookieStr);
            datapathConnection.flowsCreate(datapath, dpFlow,
                flowAddedCallback(cookie, flowPromise, dpFlow))
        }
        val execPromise = executePacket(wildFlow.getActions)
        val futures = Future.sequence(List(flowPromise, execPromise))
        futures map { _ => true } fallbackTo { Promise.successful(false) }
    }


    /** The packet arrived on a tunnel but didn't match in the WFT. It's either
     * addressed (by the tunnel key) to a local PortSet or it was mistakenly
     * routed here. Map the tunnel key to a port set (through the
     * VirtualToPhysicalMapper).
     */
    private def handlePacketToPortSet(cookie: Int): Future[Boolean] = {

        log.debug("Packet {} came from a tunnel port", cookieStr)
        // We currently only handle packets ingressing on tunnel ports if they
        // have a tunnel key. If the tunnel key corresponds to a local virtual
        // port then the pre-installed flow rules should have matched the
        // packet. So we really only handle cases where the tunnel key exists
        // and corresponds to a port set.
        val wMatch = WildcardMatch.fromFlowMatch(packet.getMatch)
        if (wMatch.getTunnelID == null) {
            log.error("SCREAM: dropping a flow from tunnel port {} because " +
                " it has no tunnel key.", wMatch.getInputPortNumber)
            return addTranslatedFlowForActions(Nil)
        }

        val portSetFuture = VirtualToPhysicalMapper.getRef() ?
            PortSetForTunnelKeyRequest(wMatch.getTunnelID)

        portSetFuture.mapTo[PortSet] flatMap { portSet =>
            if (portSet != null) {
                val action = new FlowActionOutputToVrnPortSet(portSet.id)
                log.debug("tun => portSet, action: {}, portSet: {}",
                    action, portSet)
                // egress port filter simulation
                val localPortFutures =
                    portSet.localPorts.toSeq map {
                        portID => ask(VirtualTopologyActor.getRef(),
                            PortRequest(portID, update = false))
                            .mapTo[client.Port[_]]
                    }
                Future.sequence(localPortFutures) flatMap { localPorts =>
                    // Take the outgoing filter for each port
                    // and apply it, checking for Action.ACCEPT.
                    val tags = mutable.Set[Any]()
                    applyOutboundFilters(localPorts, portSet.id, wMatch, Some(tags),
                        { portIDs =>
                            addTranslatedFlowForActions(
                                translateToDpPorts(List(action),
                                                   portSet.id,
                                                   portsForLocalPorts(portIDs),
                                                   None, Nil, tags),
                                tags)
                        })
                } recoverWith { case e =>
                    log.error("Error getting configurations of local ports "+
                              "of PortSet {}", portSet)
                    Promise.failed[Boolean](e)
                }
            } else {
                Promise.failed[Boolean](new Exception())(system.dispatcher)
            }
        } recoverWith {
            case e =>
                // for now, install a drop flow. We will invalidate
                // it if the port comes up later on.
                log.debug("PacketIn came from a tunnel port but " +
                    "the key does not map to any PortSet")
                val wildFlow = new WildcardFlowBuilder()
                wildFlow.setMatch(new WildcardMatch().
                    setTunnelID(wMatch.getTunnelID).
                    setInputPort(wMatch.getInputPort))
                wildFlow.setActions(Nil)
                addTranslatedFlow(wildFlow,
                    Set(FlowTagger.invalidateByTunnelKey(wMatch.getTunnelID)),
                    Set.empty)
        }
    }

    private def doSimulation(): Future[Boolean] = {
        val actionFuture = cookieOrEgressPort match {
            case Left(haveCookie) =>
                simulatePacketIn()
            case Right(haveEgress) =>
                simulateGeneratedPacket()
        }
        actionFuture fallbackTo { Promise.successful(ErrorDrop()) } flatMap {
            case AddVirtualWildcardFlow(flow, callbacks, tags) =>
                log.debug("Simulation phase returned: AddVirtualWildcardFlow")
                addVirtualWildcardFlow(flow, callbacks, tags)
            case SendPacket(actions) =>
                log.debug("Simulation phase returned: SendPacket")
                sendPacket(actions)
            case NoOp() =>
                log.debug("Simulation phase returned: NoOp")
                DeduplicationActor.getRef() ! ApplyFlow(Nil, cookie)
                Promise.successful(true)
            case ErrorDrop() =>
                log.debug("Simulation phase returned: ErrorDrop")
                addTranslatedFlowForActions(Nil, expiration = 5000)
        }
    }

    private def simulateGeneratedPacket(): Future[SimulationAction] = {
        // FIXME (guillermo) - The launching of the coordinator is missing
        // the connectionCache and parentCookie params. They will need
        // to be given to the PacketWorkFlowActor.
        val coordinator = new Coordinator(
            WildcardMatch.fromEthernetPacket(packet.getPacket),
            packet.getPacket,
            None,
            egressPort,
            Platform.currentTime + timeout,
            connectionCache, None)
        coordinator.simulate()
    }

    private def simulatePacketIn(): Future[SimulationAction] = {
        val inPortNo = WildcardMatch.fromFlowMatch(packet.getMatch).getInputPortNumber

        log.debug("Pass packet to simulation layer {}", cookieStr)
        if (inPortNo == null) {
            log.error("SCREAM: got a PacketIn with no inPort number {}.", cookieStr)
            return Promise.successful(NoOp())
        }

        // translate
        val wMatch = WildcardMatch.fromFlowMatch(packet.getMatch)
        val port: JInteger = Unsigned.unsign(inPortNo)
        log.debug("PacketIn on port #{}", port)
        dpState.vportResolver.getVportForDpPortNumber(port) match {
            case Some(vportId) =>
                wMatch.setInputPortUUID(vportId)
                system.eventStream.publish(
                    PacketIn(wMatch, packet.getPacket, packet.getMatch,
                        packet.getReason, cookie getOrElse 0))

                handleDHCP(vportId) flatMap {
                    case true =>
                        Promise.successful(NoOp())
                    case false =>
                        val coordinator: Coordinator = new Coordinator(
                            wMatch, packet.getPacket, cookie,
                            None, Platform.currentTime + timeout,
                            connectionCache, None)
                        coordinator.simulate()
                }

            case None =>
                wMatch.setInputPort(port.toShort)
                Promise.successful(ErrorDrop())
        }
    }

    def addVirtualWildcardFlow(flow: WildcardFlowBuilder,
                               flowRemovalCallbacks: ROSet[Callback0] = Set.empty,
                               tags: ROSet[Any] = Set.empty): Future[Boolean] = {
        translateVirtualWildcardFlow(flow, tags) flatMap {
            case (finalFlow, finalTags) =>
                addTranslatedFlow(finalFlow, finalTags, flowRemovalCallbacks)
        }
    }

    private def handleDHCP(inPortId: UUID): Future[Boolean] = {
        // check if the packet is a DHCP request
        val eth = packet.getPacket
        if (eth.getEtherType == IPv4.ETHERTYPE) {
            val ipv4 = eth.getPayload.asInstanceOf[IPv4]
            if (ipv4.getProtocol == UDP.PROTOCOL_NUMBER) {
                val udp = ipv4.getPayload.asInstanceOf[UDP]
                if (udp.getSourcePort == 68
                    && udp.getDestinationPort == 67) {
                    val dhcp = udp.getPayload.asInstanceOf[DHCP]
                    if (dhcp.getOpCode == DHCP.OPCODE_REQUEST) {
                        return new DhcpImpl(
                            dataClient, inPortId, dhcp,
                            eth.getSourceMACAddress, cookie).handleDHCP
                    }
                }
            }
        }
        Promise.successful(false)
    }

    def sendPacket(origActions: List[FlowAction[_]]): Future[Boolean] = {
        log.debug("Sending packet {} {} with action list {}",
                  cookieStr, packet, origActions)
        // Empty action list drops the packet. No need to send to DP.
        if (null == origActions || origActions.size == 0)
            return Promise.successful(true)

        val wildMatch = WildcardMatch.fromEthernetPacket(packet.getPacket)
        packet.setMatch(FlowMatches.fromEthernetPacket(packet.getPacket))
        translateActions(origActions, None, None, wildMatch) flatMap {
            actions =>
                log.debug("Translated actions to action list {} for {}",
                          actions, cookieStr)
                executePacket(actions)
        }
    }
}
