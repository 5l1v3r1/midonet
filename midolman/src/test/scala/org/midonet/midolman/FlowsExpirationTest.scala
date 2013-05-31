/*
 * Copyright 2012 Midokura Pte. Ltd.
 */

package org.midonet.midolman

import scala.Predef._
import scala.collection.JavaConversions._
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestKitExtension, TestProbe}
import akka.util.Duration
import akka.util.duration._

import org.apache.commons.configuration.HierarchicalConfiguration
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import org.midonet.midolman.FlowController._
import org.midonet.midolman.PacketWorkflowActor.PacketIn
import org.midonet.midolman.util.TestHelpers
import org.midonet.odp._
import org.midonet.odp.flows.FlowKeys
import org.midonet.packets.{IntIPv4, MAC, Packets}
import org.midonet.sdn.flows.{FlowManager, WildcardFlowBuilder, WildcardMatch}
import org.midonet.midolman.topology.{LocalPortActive, VirtualTopologyActor}
import org.midonet.midolman.topology.VirtualTopologyActor.{BridgeRequest, PortRequest}


@RunWith(classOf[JUnitRunner])
class FlowsExpirationTest extends MidolmanTestCase
       with VirtualConfigurationBuilders {

    var eventProbe: TestProbe = null
    var datapath: Datapath = null

    var timeOutFlow: Long = 500
    var delayAsynchAddRemoveInDatapath: Long = timeOutFlow/3

    val ethPkt = Packets.udp(
        MAC.fromString("02:11:22:33:44:10"),
        MAC.fromString("02:11:22:33:44:11"),
        IntIPv4.fromString("10.0.1.10"),
        IntIPv4.fromString("10.0.1.11"),
        10, 11, "My UDP packet".getBytes)

    val ethPkt1 = Packets.udp(
        MAC.fromString("02:11:22:33:44:10"),
        MAC.fromString("02:11:22:33:44:12"),
        IntIPv4.fromString("10.0.1.10"),
        IntIPv4.fromString("10.0.1.11"),
        10, 11, "My UDP packet 2".getBytes)



    //val log =  Logger.getLogger(classOf[FlowManager])
    //log.setLevel(Level.TRACE)


    override def fillConfig(config: HierarchicalConfiguration) = {
        config.setProperty("midolman.midolman_root_key", "/test/v3/midolman")
        config.setProperty("datapath.max_flow_count", 3)
        config.setProperty("midolman.check_flow_expiration_interval", 10)
        config.setProperty("midolman.enable_monitoring", "false")
        config.setProperty("midolman.idle_flow_tolerance_interval", 1)
        config
    }

    override def beforeTest() {
        val myHost = newHost("myself", hostId())
        eventProbe = newProbe()
        actors().eventStream.subscribe(eventProbe.ref, classOf[FlowUpdateCompleted])

        val bridge = newBridge("bridge")

        val port1 = newExteriorBridgePort(bridge)
        val port2 = newExteriorBridgePort(bridge)

        materializePort(port1, myHost, "port1")
        materializePort(port2, myHost, "port2")

        initializeDatapath() should not be (null)

        flowProbe().expectMsgType[DatapathController.DatapathReady].datapath should not be (null)

        // Now disable sending messages to the DatapathController
        dpProbe().testActor.tell("stop")
        dpProbe().expectMsg("stop")

        wflowAddedProbe.expectMsgClass(classOf[WildcardFlowAdded])
        wflowAddedProbe.expectMsgClass(classOf[WildcardFlowAdded])

        val vta = VirtualTopologyActor.getRef(actors())
        ask(vta, PortRequest(port1.getId, false))
        ask(vta, PortRequest(port2.getId, false))
        ask(vta, BridgeRequest(bridge.getId, false))

        requestOfType[LocalPortActive](portsProbe)
        requestOfType[LocalPortActive](portsProbe)

        drainProbe(eventProbe)
        drainProbes()
    }

    def testHardTimeExpiration() {
        triggerPacketIn("port1", ethPkt)

        val pktInMsg = fishForRequestOfType[PacketIn](packetInProbe)
        val wflow = wflowAddedProbe.expectMsgClass(classOf[WildcardFlowAdded]).f
        val wflowBuilder = new WildcardFlowBuilder(wflow)
        flowProbe().testActor ! RemoveWildcardFlow(wflow.getMatch)
        wflowRemovedProbe.expectMsgClass(classOf[WildcardFlowRemoved])

        val flow = new Flow().setMatch(FlowMatches.fromEthernetPacket(ethPkt))
        dpConn().flowsCreate(datapath, flow)

        wflowBuilder.getMatch.unsetInputPortUUID()
        wflowBuilder.getMatch.unsetInputPortNumber()
        wflowBuilder.setActions(List().toList)
        wflowBuilder.setHardExpirationMillis(getDilatedTime(timeOutFlow).toInt)

        flowProbe().testActor.tell(
            AddWildcardFlow(wflowBuilder.build, Some(flow), Set.empty, Set.empty))

        wflowAddedProbe.expectMsgClass(classOf[WildcardFlowAdded])

        val timeAdded: Long = System.currentTimeMillis()
        // we have to wait because adding the flow into the dp is async
        dilatedSleep(delayAsynchAddRemoveInDatapath)

        dpConn().flowsGet(datapath, flow.getMatch).get should not be (null)
        // we wait for the flow removed message that will be triggered because
        // the flow expired
        wflowRemovedProbe.expectMsgClass(classOf[WildcardFlowRemoved])

        val timeDeleted: Long = System.currentTimeMillis()

        dilatedSleep(delayAsynchAddRemoveInDatapath)

        dpConn().flowsGet(datapath, pktInMsg.dpMatch).get should be (null)

        // check that the flow expired in the correct time range
        (timeDeleted - timeAdded) should (be >= timeOutFlow)
        (timeDeleted - timeAdded) should (be < 2*timeOutFlow)

    }

    def testIdleTimeExpiration() {
        triggerPacketIn("port1", ethPkt)

        val pktInMsg = fishForRequestOfType[PacketIn](packetInProbe)
        wflowAddedProbe.expectMsgClass(classOf[WildcardFlowAdded])

        val timeAdded: Long = System.currentTimeMillis()

        dilatedSleep(delayAsynchAddRemoveInDatapath)

        dpConn().flowsGet(datapath, pktInMsg.dpMatch).get should not be (null)

        // wait to get a FlowRemoved message that will be triggered by invalidation
        wflowRemovedProbe.fishForMessage(Duration(timeOutFlow, TimeUnit.SECONDS),
            "WildcardFlowRemoved")(TestHelpers.getMatchFlowRemovedPacketPartialFunction)

        val timeDeleted: Long = System.currentTimeMillis()

        dilatedSleep(delayAsynchAddRemoveInDatapath)

        dpConn().flowsGet(datapath, pktInMsg.dpMatch).get should be (null)
        // check that the invalidation happened in the right time frame
        (timeDeleted - timeAdded) should (be >= timeOutFlow)

    }


    def testIdleTimeExpirationUpdated() {
        triggerPacketIn("port1", ethPkt)

        val addedFlow = wflowAddedProbe.expectMsgClass(classOf[WildcardFlowAdded]).f
        flowProbe().testActor ! RemoveWildcardFlow(addedFlow.getMatch)
        wflowRemovedProbe.expectMsgClass(classOf[WildcardFlowRemoved])

        val wflow = new WildcardFlowBuilder(addedFlow)

        val flow = new Flow().setMatch(FlowMatches.fromEthernetPacket(ethPkt))
        dpConn().flowsCreate(datapath, flow)

        wflow.getMatch.unsetInputPortUUID()
        wflow actions = Nil
        wflow.setIdleExpirationMillis(getDilatedTime(timeOutFlow).toInt)

        flowProbe().testActor.tell(
            AddWildcardFlow(wflow.build, Some(flow), Set.empty, Set.empty))

        wflowAddedProbe.expectMsgClass(classOf[WildcardFlowAdded])
        val timeAdded: Long = System.currentTimeMillis()

        // this sleep is needed because the flow installation is async. We use a
        // large interval also to execute the following triggerPacketIn and thus
        // causing the flow's LastUsedTime after a reasonable amount of time
        dilatedSleep(timeOutFlow/3)
        dpConn().flowsGet(datapath, flow.getMatch).get should not be (null)

        // Now trigger another packet that matches the flow. This will update
        // the lastUsedTime
        setFlowLastUsedTimeToNow(flow.getMatch)

        eventProbe.expectMsgClass(classOf[FlowUpdateCompleted])
        // wait for FlowRemoval notification
        wflowRemovedProbe.fishForMessage(Duration(timeOutFlow, TimeUnit.SECONDS),
            "WildcardFlowRemoved")(TestHelpers.getMatchFlowRemovedPacketPartialFunction)
        val timeDeleted: Long = System.currentTimeMillis()

        dpConn().flowsGet(datapath, flow.getMatch).get() should be (null)
        // check that the invalidation happened in the right time frame
        (timeDeleted - timeAdded) should be >= (timeOutFlow + timeOutFlow/3)
    }

    def testIdleAndHardTimeOutOfTheSameFlow() {
        triggerPacketIn("port1", ethPkt)


        val addedFlow = wflowAddedProbe.expectMsgClass(classOf[WildcardFlowAdded]).f
        flowProbe().testActor ! RemoveWildcardFlow(addedFlow.getMatch)
        wflowRemovedProbe.expectMsgClass(classOf[WildcardFlowRemoved])

        val wflow = new WildcardFlowBuilder(addedFlow)

        val flow = new Flow().setMatch(FlowMatches.fromEthernetPacket(ethPkt))
        dpConn().flowsCreate(datapath, flow)

        wflow.getMatch.unsetInputPortUUID()
        wflow.setActions(List().toList)
        wflow.setHardExpirationMillis(getDilatedTime(timeOutFlow).toInt)

        flowProbe().testActor.tell(
            AddWildcardFlow(wflow.build, Some(flow), Set.empty, Set.empty))

        wflowAddedProbe.expectMsgClass(classOf[WildcardFlowAdded])
        val timeAdded: Long = System.currentTimeMillis()

        dilatedSleep(delayAsynchAddRemoveInDatapath)
        dpConn().flowsGet(datapath, flow.getMatch).get should not be (null)

        wflowRemovedProbe.fishForMessage(Duration(timeOutFlow, TimeUnit.SECONDS),
            "WildcardFlowRemoved")(TestHelpers.getMatchFlowRemovedPacketPartialFunction)
        val timeDeleted: Long = System.currentTimeMillis()

        dpConn().flowsGet(datapath, flow.getMatch).get() should be (null)

        // check that the invalidation happened in the right time frame
        (timeDeleted - timeAdded) should (be >= timeOutFlow)
        (timeDeleted - timeAdded) should (be < timeOutFlow*2)

    }

    def testIdleTimeExpirationKernelFlowUpdated() {

        triggerPacketIn("port1", ethPkt)

        val pktInMsg = fishForRequestOfType[PacketIn](packetInProbe)

        val addedFlow = wflowAddedProbe.expectMsgClass(classOf[WildcardFlowAdded]).f
        flowProbe().testActor ! RemoveWildcardFlow(addedFlow.getMatch)
        wflowRemovedProbe.expectMsgClass(classOf[WildcardFlowRemoved])

        val wflow = new WildcardFlowBuilder(addedFlow)

        val flow = new Flow().setMatch(FlowMatches.fromEthernetPacket(ethPkt))
        dpConn().flowsCreate(datapath, flow)

        wflow.getMatch.unsetInputPortUUID()
        wflow.setActions(Nil)
        wflow.setIdleExpirationMillis(getDilatedTime(timeOutFlow).toInt)

        flowProbe().testActor.tell(
            AddWildcardFlow(wflow.build, Some(flow), Set.empty, Set.empty))

        wflowAddedProbe.expectMsgClass(classOf[WildcardFlowAdded])
        val timeAdded = System.currentTimeMillis()

        dilatedSleep(timeOutFlow/3)
        dpConn().flowsGet(datapath, flow.getMatch).get should not be (null)
        // update the LastUsedTime of the flow
        setFlowLastUsedTimeToNow(flow.getMatch)
        // expect that the FlowController requests an update for this flow
        // because (timeLived > timeout/2) and that the update will be received
        eventProbe.expectMsgClass(classOf[FlowUpdateCompleted])
        // wait for flow expiration
        wflowRemovedProbe.fishForMessage(Duration(timeOutFlow, TimeUnit.SECONDS),
            "WildcardFlowRemoved")(TestHelpers.getMatchFlowRemovedPacketPartialFunction)
        val timeDeleted = System.currentTimeMillis()

        dpConn().flowsGet(datapath, pktInMsg.dpMatch).get() should be (null)
        // check that the invalidation happened in the right time frame
        (timeDeleted-timeAdded) should (be >= timeOutFlow+timeOutFlow/3)
    }
}

