/*
* Copyright 2012 Midokura Europe SARL
*/
package org.midonet.midolman

import akka.util.duration._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.slf4j.LoggerFactory

import org.midonet.midolman.FlowController.{InvalidateFlowsByTag,
    WildcardFlowRemoved, WildcardFlowAdded}
import org.midonet.midolman.topology.FlowTagger
import rules.{RuleResult, Condition}
import org.midonet.packets._
import util.SimulationHelper

@RunWith(classOf[JUnitRunner])
class L2FilteringTestCase extends MidolmanTestCase with VMsBehindRouterFixture
        with SimulationHelper {
    private final val log = LoggerFactory.getLogger(classOf[L2FilteringTestCase])

    def testAddAndModifyJumpChain() {
        drainProbes()
        log.info("creating inbound chain, assigning the chain to the bridge")
        val brInChain = newInboundChainOnBridge("brInFilter", bridge)

        // this is a chain that will be set as jump chain for brInChain
        val jumpChain = createChain("jumpRule", None)

        // add rule that drops everything return flows
        newLiteralRuleOnChain(brInChain, 1, new Condition(), RuleResult.Action.DROP)
        expectPacketDropped(vmPortNumbers(0), vmPortNumbers(3), icmpBetweenPorts)
        drainProbes()

        // add rule that accepts everything
        newLiteralRuleOnChain(brInChain, 1, new Condition(), RuleResult.Action.ACCEPT)
        drainProbes()
        drainProbe(packetsEventsProbe)
        drainProbe(wflowAddedProbe)
        drainProbe(wflowRemovedProbe)

        expectPacketAllowed(vmPortNumbers(0), vmPortNumbers(3), icmpBetweenPorts)
        drainProbes()
        drainProbe(packetsEventsProbe)
        drainProbe(wflowAddedProbe)
        drainProbe(wflowRemovedProbe)

        // add a rule that drops the packets from 0 to 3 in the jump chain
        val cond1 = new Condition()
        cond1.nwSrcIp = IPv4Addr.fromString(vmIps(0).toUnicastString).subnet()
        cond1.nwDstIp = IPv4Addr.fromString(vmIps(3).toUnicastString).subnet()
        val jumpRule = newLiteralRuleOnChain(jumpChain, 1, cond1, RuleResult.Action.DROP)
        newJumpRuleOnChain(brInChain, 1, cond1, jumpChain.getId)
        log.info("The flow should be invalidated")
        fishForRequestOfType[WildcardFlowRemoved](wflowRemovedProbe)

        log.info("sending a packet that should be dropped by jump rule")
        expectPacketDropped(0, 3, icmpBetweenPorts)
        var flow = fishForRequestOfType[WildcardFlowAdded](wflowAddedProbe)
        flow.f.actions.size should be (0)

        log.info("removing a rule from the jump rule itself (inner chain)")
        deleteRule(jumpRule.getId)
        fishForRequestOfType[WildcardFlowRemoved](wflowRemovedProbe)
        expectPacketAllowed(vmPortNumbers(0), vmPortNumbers(3), icmpBetweenPorts)
        flow = fishForRequestOfType[WildcardFlowAdded](wflowAddedProbe)
        flow.f.actions.size should (be > 0)

        log.info("adding back rule from the jump rule itself (inner chain)")
        newLiteralRuleOnChain(jumpChain, 1, cond1, RuleResult.Action.DROP)
        // expect invalidation
        fishForRequestOfType[WildcardFlowRemoved](wflowRemovedProbe)
        // expect that packet is dropped
        expectPacketDropped(0, 3, icmpBetweenPorts)
        flow = fishForRequestOfType[WildcardFlowAdded](wflowAddedProbe)
        flow.f.actions.size should be (0)

    }

    def testFloodTagging() {
        val chain = newOutboundChainOnPort("p1OutChain", vmPorts(0))
        val cond = new Condition()
        val rule = newLiteralRuleOnChain(chain, 1, cond,
            RuleResult.Action.ACCEPT)
        clusterDataClient().bridgesUpdate(bridge)
        expectPacketAllowed(1, 2, icmpBetweenPorts)
        requestOfType[WildcardFlowAdded](wflowAddedProbe)
        drainProbes()
        drainProbe(wflowRemovedProbe)
        FlowController.getRef(actors()).tell(InvalidateFlowsByTag(
                FlowTagger.invalidateFlowsByDevice(chain.getId)))
        requestOfType[WildcardFlowRemoved](wflowRemovedProbe)
    }

    def testV4ruleV6pktMatch() {
        val fromPort = 1
        val toPort = 2

        val chain = newInboundChainOnPort("p1InChain", vmPorts(fromPort))
        val cond = new Condition()
        cond.nwSrcIp = new IPv4Subnet(
                IPv4Addr.fromString(vmIps(fromPort).toUnicastString), 32)
        val rule = newLiteralRuleOnChain(chain, 1, cond,
            RuleResult.Action.DROP)

        expectPacketDropped(fromPort, toPort, icmpBetweenPorts)
        expectPacketAllowed(fromPort, toPort, ipv6BetweenPorts)
    }

    def testV6ruleV4pktMatch() {
        val fromPort = 1
        val toPort = 2

        val chain = newInboundChainOnPort("p1InChain", vmPorts(fromPort))
        val cond = new Condition()
        cond.nwSrcIp = new IPv6Subnet(v6VmIps(fromPort), 128)
        val rule = newLiteralRuleOnChain(chain, 1, cond,
            RuleResult.Action.DROP)

        expectPacketDropped(fromPort, toPort, ipv6BetweenPorts)
        expectPacketAllowed(fromPort, toPort, icmpBetweenPorts)
    }

    def test() {
        flowController().underlyingActor.flowManager.getNumWildcardFlows should be === vmPorts.size

        log.info("populating the mac learning table with an arp request from each port")
        (vmPortNames, vmMacs, vmIps).zipped foreach {
            (name, mac, ip) => arpVmToRouterAndCheckReply(name, mac, ip, routerIp, routerMac)
        }

        log.info("sending icmp echoes between every pair of ports")
        for (pair <- (0 to (vmPorts.size-1)).toList.combinations(2)) {
            expectPacketAllowed(pair.head, pair.last, icmpBetweenPorts)
            requestOfType[WildcardFlowAdded](wflowAddedProbe)
            expectPacketAllowed(pair.last, pair.head, icmpBetweenPorts)
            requestOfType[WildcardFlowAdded](wflowAddedProbe)
        }
        drainProbes()

        log.info("creating chain")
        val brInChain = newInboundChainOnBridge("brInFilter", bridge)
        val cond1 = new Condition()
        cond1.matchReturnFlow = true
        val rule1 = newLiteralRuleOnChain(brInChain, 1, cond1,
                                          RuleResult.Action.ACCEPT)

        log.info("adding first rule: drop by ip from port0 to port3")
        val cond2 = new Condition()
        cond2.nwSrcIp = new IPv4Subnet(
            IPv4Addr.fromString(vmIps(0).toUnicastString), 32)
        cond2.nwDstIp = new IPv4Subnet(
            IPv4Addr.fromString(vmIps(3).toUnicastString), 32)
        val rule2 = newLiteralRuleOnChain(brInChain, 2, cond2,
                                          RuleResult.Action.DROP)
        clusterDataClient().bridgesUpdate(bridge)

        log.info("checking that the creation of the chain invalidates all flows")
        for (pair <- (0 to (vmPorts.size-1)).toList.combinations(2)) {
            fishForRequestOfType[WildcardFlowRemoved](wflowRemovedProbe)
            fishForRequestOfType[WildcardFlowRemoved](wflowRemovedProbe)
        }
        flowController().underlyingActor.flowManager.getNumWildcardFlows should be === vmPorts.size
        drainProbe(packetsEventsProbe)
        drainProbe(wflowAddedProbe)
        drainProbe(wflowRemovedProbe)

        log.info("sending a packet that should be dropped by rule 2")
        expectPacketDropped(0, 3, icmpBetweenPorts)
        fishForRequestOfType[WildcardFlowAdded](wflowAddedProbe)
        log.info("sending a packet that should be allowed by rule 2")
        expectPacketAllowed(4, 1, icmpBetweenPorts)
        fishForRequestOfType[WildcardFlowAdded](wflowAddedProbe)
        log.info("sending a packet that should be allowed by rule 2")
        expectPacketAllowed(0, 3, lldpBetweenPorts)
        fishForRequestOfType[WildcardFlowAdded](wflowAddedProbe)

        log.info("adding a second rule: drop by mac from port4 to port1")
        val cond3 = new Condition()
        cond3.dlSrc = vmMacs(4)
        cond3.dlDst = vmMacs(1)
        val rule3 = newLiteralRuleOnChain(brInChain, 3, cond3,
                                          RuleResult.Action.DROP)

        1 to 3 foreach { _ => fishForRequestOfType[WildcardFlowRemoved](wflowRemovedProbe) }
        flowController().underlyingActor.flowManager.getNumWildcardFlows should be === vmPorts.size

        log.info("sending two packets that should be dropped by rule 3")
        expectPacketDropped(4, 1, icmpBetweenPorts)
        fishForRequestOfType[WildcardFlowAdded](wflowAddedProbe)
        expectPacketDropped(4, 1, lldpBetweenPorts)
        fishForRequestOfType[WildcardFlowAdded](wflowAddedProbe)
        log.info("sending a packet that should be allowed by rules 2,3")
        expectPacketAllowed(4, 3, icmpBetweenPorts)
        fishForRequestOfType[WildcardFlowAdded](wflowAddedProbe)
        log.info("sending an lldp packet that should be allowed by rules 2,3")
        expectPacketAllowed(4, 3, lldpBetweenPorts)
        fishForRequestOfType[WildcardFlowAdded](wflowAddedProbe)

        log.info("adding a third rule: drop if ether-type == LLDP")
        val cond4 = new Condition()
        cond4.dlType = Unsigned.unsign(LLDP.ETHERTYPE)
        val rule4 = newLiteralRuleOnChain(brInChain, 4, cond4,
                                          RuleResult.Action.DROP)
        1 to 4 foreach { _ => fishForRequestOfType[WildcardFlowRemoved](wflowRemovedProbe) }
        flowController().underlyingActor.flowManager.getNumWildcardFlows should be === vmPorts.size

        log.info("sending an lldp packet that should be dropped by rule 4")
        expectPacketDropped(4, 3, lldpBetweenPorts)
        fishForRequestOfType[WildcardFlowAdded](wflowAddedProbe)
        log.info("sending an icmp packet that should be allowed by rule 4")
        expectPacketAllowed(4, 3, icmpBetweenPorts)
        fishForRequestOfType[WildcardFlowAdded](wflowAddedProbe)

        log.info("deleting rule 4")
        clusterDataClient().rulesDelete(rule4.getId)
        fishForRequestOfType[WildcardFlowRemoved](wflowRemovedProbe)
        fishForRequestOfType[WildcardFlowRemoved](wflowRemovedProbe)
        flowController().underlyingActor.flowManager.getNumWildcardFlows should be === vmPorts.size

        log.info("sending an lldp packet that should be allowed by the " +
                 "removal of rule 4")
        expectPacketAllowed(4, 3, lldpBetweenPorts)
        requestOfType[WildcardFlowAdded](wflowAddedProbe)

        log.info("sending two packets that should be dropped with the same " +
                 "match as the return packets that will be sent later on")
        expectPacketDropped(4, 1, udpBetweenPorts)
        requestOfType[WildcardFlowAdded](wflowAddedProbe)
        expectPacketDropped(0, 3, udpBetweenPorts)
        requestOfType[WildcardFlowAdded](wflowAddedProbe)

        log.info("waiting for the return drop flows to timeout")
        // Flow expiration is checked every 10 seconds. The DROP flows should
        // expire in 3 seconds, but we wait 11 seconds for expiration to run.
        wflowRemovedProbe.within (15 seconds) {
            requestOfType[WildcardFlowRemoved](wflowRemovedProbe)
            requestOfType[WildcardFlowRemoved](wflowRemovedProbe)
        }
        // The remaining (allowed) LLDP flow has an idle expiration of 60
        // seconds. We don't bother waiting for it because we're not testing
        // expiration here. The flow won't conflict with the following UDPs.

        drainProbe(wflowRemovedProbe)
        drainProbe(wflowAddedProbe)
        log.info("sending two packets that should install conntrack entries")
        expectPacketAllowed(1, 4, udpBetweenPorts)
        fishForRequestOfType[WildcardFlowAdded](wflowAddedProbe)
        expectPacketAllowed(3, 0, udpBetweenPorts)
        fishForRequestOfType[WildcardFlowAdded](wflowAddedProbe)

        log.info("sending two return packets that should be accepted due to " +
                 "conntrack")
        expectPacketAllowed(4, 1, udpBetweenPorts)
        expectPacketAllowed(0, 3, udpBetweenPorts)
    }
}
