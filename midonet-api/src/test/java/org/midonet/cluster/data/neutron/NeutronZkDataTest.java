/*
 * Copyright 2014 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.midonet.cluster.data.neutron;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.midonet.cluster.data.Port;
import org.midonet.cluster.data.Rule;
import org.midonet.cluster.data.rules.ForwardNatRule;
import org.midonet.cluster.data.rules.ReverseNatRule;
import org.midonet.midolman.rules.Condition;
import org.midonet.midolman.rules.FragmentPolicy;
import org.midonet.midolman.rules.NatTarget;
import org.midonet.midolman.rules.RuleBuilder;
import org.midonet.midolman.rules.RuleResult;
import org.midonet.midolman.serialization.SerializationException;
import org.midonet.midolman.state.PathBuilder;
import org.midonet.midolman.state.StateAccessException;
import org.midonet.packets.IPv4Subnet;

public final class NeutronZkDataTest extends NeutronPluginTest {

    private DirectoryVerifier dirVerifier;
    private PathBuilder pathBuilder;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        pathBuilder = getPathBuilder();
        dirVerifier = new DirectoryVerifier(getDirectory());
    }

    private void verifyRoute(UUID routerId, String addr, String type,
                             int expectedMatchCnt) {

        String routesPath = pathBuilder.getRoutesPath();

        Map<String, Object> matches = new HashMap<>();
        if (routerId != null) {
            matches.put("routerId", routerId);
        }

        if (type != null) {
            matches.put("nextHop", type);
        }

        matches.put("dstNetworkAddr", addr);
        matches.put("dstNetworkLength", 32);
        matches.put("srcNetworkAddr", "0.0.0.0");
        matches.put("srcNetworkLength", 0);

        dirVerifier.assertChildrenFieldsMatch(routesPath, matches,
                                              expectedMatchCnt);
    }

    private void verifyMetadataRoute(UUID routerId, String srcCidr,
                                     int expectedMatchCnt) {

        String routesPath = pathBuilder.getRoutesPath();
        IPv4Subnet srcSubnet = IPv4Subnet.fromCidr(srcCidr);

        Map<String, Object> matches = new HashMap<>();
        matches.put("srcNetworkAddr", srcSubnet.toNetworkAddress().toString());
        matches.put("srcNetworkLength", srcSubnet.getPrefixLen());
        matches.put("dstNetworkAddr",
                    MetaDataService.IPv4_SUBNET.toNetworkAddress().toString());
        matches.put("dstNetworkLength", 32);
        matches.put("routerId", routerId);

        dirVerifier.assertChildrenFieldsMatch(routesPath, matches,
                                              expectedMatchCnt);
    }

    private void verifyFipDnatRule(int expectedMatchCnt) {

        String rulesPath = pathBuilder.getRulesPath();

        String floatingIpAddr = floatingIp.floatingIpAddress;
        String fixedIpAddr = floatingIp.fixedIpAddress;

        Map<String, Object> matches = new HashMap<>();
        matches.put("type", "ForwardNat");
        matches.put("condition.nwDstIp.address", floatingIpAddr);
        matches.put("condition.fragmentPolicy", "ANY");
        matches.put("natTargets[0].nwStart", fixedIpAddr);
        matches.put("natTargets[0].nwEnd", fixedIpAddr);

        dirVerifier.assertChildrenFieldsMatch(rulesPath, matches,
                                              expectedMatchCnt);
    }

    private void verifyFipSnatRule(int expectedMatchCnt) {

        String rulesPath = pathBuilder.getRulesPath();

        String floatingIpAddr = floatingIp.floatingIpAddress;
        String fixedIpAddr = floatingIp.fixedIpAddress;

        Map<String, Object> matches = new HashMap<>();
        matches.put("type", "ForwardNat");
        matches.put("condition.nwSrcIp.address", fixedIpAddr);
        matches.put("condition.fragmentPolicy", "ANY");
        matches.put("natTargets[0].nwStart", floatingIpAddr);
        matches.put("natTargets[0].nwEnd", floatingIpAddr);

        dirVerifier.assertChildrenFieldsMatch(rulesPath, matches,
                                              expectedMatchCnt);
    }

    private void verifyFloatingIpRules() {

        verifyFipSnatRule(1);
        verifyFipDnatRule(1);

    }

    public void verifyNoFloatingIpRules() {

        verifyFipSnatRule(0);
        verifyFipDnatRule(0);
    }

    private void verifyLocalSnatRules(String ipAddr)
        throws SerializationException, StateAccessException {
        org.midonet.cluster.data.Router r = dataClient.routersGet(
            router.id);
        Port bPort = dataClient.portsGet(routerPort.id);
        ForwardNatRule snatRule = (ForwardNatRule) dataClient.rulesGet(
            L3ZkManager.downlinkSnatRuleId(r.getOutboundFilter(),
                                           bPort.getPeerId()));
        ReverseNatRule revSnatRule = (ReverseNatRule) dataClient.rulesGet(
            L3ZkManager.downlinkSnatRuleId(r.getInboundFilter(),
                                           bPort.getPeerId()));

        Port p = dataClient.portsGet(routerInterface.portId);
        Condition cond = snatRule.getCondition();
        Assert.assertTrue(cond.inPortIds.contains(p.getPeerId()));
        Assert.assertTrue(cond.outPortIds.contains(p.getPeerId()));
        Assert.assertEquals(snatRule.getAction(), RuleResult.Action.RETURN);
        Assert.assertEquals(snatRule.getTargets().size(), 1);

        // Verify that metadata IP is skipped
        Assert.assertEquals(MetaDataService.IPv4_SUBNET, cond.nwDstIp);
        Assert.assertTrue(cond.nwDstInv);

        NatTarget target = snatRule.getTargets().iterator().next();
        Assert.assertEquals(target.nwStart.toString(), ipAddr);
        Assert.assertEquals(target.nwEnd.toString(), ipAddr);
        Assert.assertEquals(target.tpStart, DYN_SNAT_PORT_START);
        Assert.assertEquals(target.tpEnd, DYN_SNAT_PORT_END);

        Assert.assertTrue(cond.inPortIds.contains(p.getPeerId()));
        Assert.assertEquals(revSnatRule.getAction(), RuleResult.Action.ACCEPT);
    }

    @Test
    public void testFloatingIp()
        throws SerializationException, StateAccessException,
               Rule.RuleIndexOutOfBoundsException {

        verifyFloatingIpRules();

        plugin.deleteNetwork(extNetwork.id);

        verifyNoFloatingIpRules();
    }

    @Test
    public void testMetadataRouteDhcpPortDelete()
        throws Rule.RuleIndexOutOfBoundsException, SerializationException,
               StateAccessException {

        // First test the normal case
        verifyMetadataRoute(router.id, subnet.cidr, 1);

        // Delete the DHCP port
        plugin.deletePort(dhcpPort.id);

        // Verify no metadata route
        verifyMetadataRoute(router.id, subnet.cidr, 0);

        // Add a new DHCP port
        plugin.createPort(dhcpPort);

        // Verify that the metadata is re-added
        verifyMetadataRoute(router.id, subnet.cidr, 1);
    }
    private Subnet updateSubnetGatewayIp(String newGatewayIp)
        throws Rule.RuleIndexOutOfBoundsException, SerializationException,
               StateAccessException {
        Subnet subnet2 = new Subnet(subnet.id, subnet.networkId,
                                    subnet.tenantId, subnet.name,
                                    subnet.cidr, subnet.ipVersion,
                                    newGatewayIp, subnet.allocationPools,
                                    subnet.dnsNameservers, subnet.hostRoutes,
                                    subnet.enableDhcp);
        plugin.updateSubnet(subnet.id, subnet2);
        return subnet2;
    }

    @Test
    public void testLocalSnatRules()
        throws StateAccessException, SerializationException,
               Rule.RuleIndexOutOfBoundsException {

        verifyLocalSnatRules(subnet.gatewayIp);

        // Verify that the local SNAT rules are updated when the gateway IP of
        // the subnet is updated
        String newGatewayIp = "10.0.0.100";
        updateSubnetGatewayIp(newGatewayIp);
        verifyLocalSnatRules(newGatewayIp);

        // Delete the SNAT rules to emulate how it looked previously
        org.midonet.cluster.data.Router r = dataClient.routersGet(
            router.id);
        Port bPort = dataClient.portsGet(routerPort.id);
        UUID snatRuleId = L3ZkManager.downlinkSnatRuleId(r.getOutboundFilter(),
                                                         bPort.getPeerId());
        UUID revSnatRuleId = L3ZkManager.downlinkSnatRuleId(
            r.getInboundFilter(), bPort.getPeerId());
        dataClient.rulesDelete(snatRuleId);
        dataClient.rulesDelete(revSnatRuleId);

        // Now try updating the subnet gateway IP again.
        String newGatewayIp2 = "10.0.1.100";
        updateSubnetGatewayIp(newGatewayIp2);
        verifyLocalSnatRules(newGatewayIp2);

        // Try deleting the router interface
        plugin.deletePort(routerInterface.portId);
        Assert.assertNull(dataClient.rulesGet(snatRuleId));
        Assert.assertNull(dataClient.rulesGet(revSnatRuleId));
    }

    @Test
    public void testLocalSnatRuleDeletion()
        throws StateAccessException, SerializationException,
               Rule.RuleIndexOutOfBoundsException {

        verifyLocalSnatRules(subnet.gatewayIp);

        // Delete the SNAT rules to emulate how it looked previously
        org.midonet.cluster.data.Router r = dataClient.routersGet(
            router.id);
        Port bPort = dataClient.portsGet(routerPort.id);

        UUID snatRuleId = L3ZkManager.downlinkSnatRuleId(r.getOutboundFilter(),
                                                         bPort.getPeerId());
        UUID revSnatRuleId = L3ZkManager.downlinkSnatRuleId(
            r.getInboundFilter(), bPort.getPeerId());
        dataClient.rulesDelete(snatRuleId);
        dataClient.rulesDelete(revSnatRuleId);

        // Try deleting the router interface.  This should still work
        plugin.deletePort(routerInterface.portId);
        Assert.assertNull(dataClient.rulesGet(snatRuleId));
        Assert.assertNull(dataClient.rulesGet(revSnatRuleId));
    }

    @Test
    public void testSubnetUpdateGatewayIp()
        throws SerializationException, StateAccessException,
               Rule.RuleIndexOutOfBoundsException {

        String newGatewayIp = "10.0.0.100";

        // Verify the gateway IP and the ports local routes
        verifyRoute(router.id, subnet.gatewayIp, "LOCAL", 1);
        verifyRoute(router.id, newGatewayIp, "LOCAL", 0);

        // Update the subnet's gateway IP
        updateSubnetGatewayIp(newGatewayIp);

        // Verify that the port local routes are updated
        verifyRoute(router.id, subnet.gatewayIp, "LOCAL", 0);
        verifyRoute(router.id, newGatewayIp, "LOCAL", 1);
    }

    @Test
    public void testExternalSubnetUpdateGatewayIp()
        throws SerializationException, StateAccessException,
               Rule.RuleIndexOutOfBoundsException {

        String newGatewayIp = "200.200.200.100";

        // Verify the gateway IP and the ports local routes
        verifyRoute(null, extSubnet.gatewayIp, "LOCAL", 1);
        verifyRoute(null, newGatewayIp, "LOCAL", 0);

        // Update the subnet's gateway IP
        Subnet subnet2 = new Subnet(extSubnet.id, extSubnet.networkId,
                                    extSubnet.tenantId, extSubnet.name,
                                    extSubnet.cidr, extSubnet.ipVersion,
                                    newGatewayIp, extSubnet.allocationPools,
                                    extSubnet.dnsNameservers,
                                    extSubnet.hostRoutes, extSubnet.enableDhcp);
        plugin.updateSubnet(extSubnet.id, subnet2);

        // Verify that the port local routes are updated
        verifyRoute(null, extSubnet.gatewayIp, "LOCAL", 0);
        verifyRoute(null, newGatewayIp, "LOCAL", 1);
    }

    @Test
    public void testPoolHeathMonitorMapping()
        throws SerializationException, StateAccessException {

        String mappingPath = pathBuilder.getPoolHealthMonitorMappingsPath(
            pool.id, healthMonitor.id);
        dirVerifier.assertPathExists(mappingPath);

        plugin.deleteHealthMonitor(healthMonitor.id);
        dirVerifier.assertPathNotExists(mappingPath);
    }

    private void verifySecurityGroupRule()
        throws SerializationException, StateAccessException {
        Boolean hasL4 = (securityGroupRule.portRangeMax != null ||
                         securityGroupRule.portRangeMin != null);
        UUID nonHeaderRuleId =
            RuleBuilder.nonHeaderRuleId(securityGroupRule.id);
        Rule mr = dataClient.rulesGet(securityGroupRule.id);

        if (securityGroupRule.isEgress() &&
            securityGroupRule.remoteGroupId != null) {
            Assert.assertTrue(mr.getCondition()
                                .ipAddrGroupIdDst
                                .equals(securityGroupRule.remoteGroupId));
        }

        if (securityGroupRule.isIngress() &&
            securityGroupRule.remoteGroupId != null) {
            Assert.assertTrue(mr.getCondition()
                                .ipAddrGroupIdSrc
                                .equals(securityGroupRule.remoteGroupId));
        }

        if (securityGroupRule.ethertype != null) {
            Assert.assertTrue(mr.getCondition().etherType.equals(
                    securityGroupRule.ethertype()));
        }

        if (securityGroupRule.protocol != null) {
            Assert.assertTrue(securityGroupRule.protocol.number() ==
                              mr.getCondition().nwProto);
        }

        if (securityGroupRule.portRangeMax != null &&
            securityGroupRule.isIngress()) {
            Assert.assertTrue(
                securityGroupRule.portRangeMax.equals(
                        mr.getCondition().tpSrc.end()));
        }
        if (hasL4) {
            Assert.assertEquals(mr.getCondition().fragmentPolicy,
                               FragmentPolicy.HEADER);
            Rule mr2 = dataClient.rulesGet(nonHeaderRuleId);
            Assert.assertNull(mr2.getCondition().tpDst);
            Assert.assertEquals(mr2.getCondition().fragmentPolicy,
                               FragmentPolicy.NONHEADER);
        } else {
            Assert.assertEquals(mr.getCondition().fragmentPolicy,
                               FragmentPolicy.ANY);
            Assert.assertNull(mr.getCondition().tpDst);
            Assert.assertNull(dataClient.rulesGet(nonHeaderRuleId));
        }
    }

    @Test
    public void testSecurityGroupCreateRemoteGroup()
        throws SerializationException, StateAccessException,
               Rule.RuleIndexOutOfBoundsException {
        securityGroupRule.remoteGroupId = securityGroup.id;
        plugin.createSecurityGroupRule(securityGroupRule);
        verifySecurityGroupRule();
    }

    @Test
    public void testSecurityGroupCreateNullEthertype()
        throws SerializationException, StateAccessException,
               Rule.RuleIndexOutOfBoundsException {
        securityGroupRule.ethertype = null;
        plugin.createSecurityGroupRule(securityGroupRule);
        verifySecurityGroupRule();
    }

    @Test
    public void testSecurityGroupCreateNullProtocol()
        throws SerializationException, StateAccessException,
               Rule.RuleIndexOutOfBoundsException {
        securityGroupRule.protocol = null;
        plugin.createSecurityGroupRule(securityGroupRule);
        verifySecurityGroupRule();
    }

    @Test
    public void testSecurityGroupCreateNullMin()
        throws SerializationException, StateAccessException,
               Rule.RuleIndexOutOfBoundsException {
        securityGroupRule.portRangeMin = null;
        securityGroupRule.portRangeMax = 100;
        plugin.createSecurityGroupRule(securityGroupRule);
        verifySecurityGroupRule();
    }

    @Test
    public void testSecurityGroupCreateNullMax()
        throws SerializationException, StateAccessException,
               Rule.RuleIndexOutOfBoundsException {
        securityGroupRule.portRangeMax = null;
        securityGroupRule.portRangeMin = 100;
        plugin.createSecurityGroupRule(securityGroupRule);
        verifySecurityGroupRule();
    }

    @Test
    public void testSecurityGroupCreateNullDirection()
        throws SerializationException, StateAccessException,
               Rule.RuleIndexOutOfBoundsException {
        securityGroupRule.direction = null;
        plugin.createSecurityGroupRule(securityGroupRule);
        verifySecurityGroupRule();
    }

    @Test
    public void testFloatingIpArpEntry()
        throws SerializationException, StateAccessException,
               Rule.RuleIndexOutOfBoundsException {
        // The default setup of the test already set up a floating ip
        String bridgeArpPath =
                pathBuilder.getBridgeIP4MacMapPath(extNetwork.id);
        Set<String> arpEntries = zk.getChildren(bridgeArpPath);
        Assert.assertTrue(arpEntries.size() == 1);
        for (String entry: arpEntries) {
            Assert.assertTrue(entry.contains(floatingIp.floatingIpAddress));
        }
        plugin.deleteFloatingIp(floatingIp.id);
        arpEntries = zk.getChildren(bridgeArpPath);
        Assert.assertTrue(arpEntries.size() == 0);

        floatingIp.portId = null;
        floatingIp.fixedIpAddress = null;
        floatingIp.floatingNetworkId = null;

        plugin.createFloatingIp(floatingIp);

        arpEntries = zk.getChildren(bridgeArpPath);
        Assert.assertTrue(arpEntries.size() == 0);

        floatingIp.portId = port.id;
        floatingIp.fixedIpAddress = "10.0.0.5";
        floatingIp.floatingNetworkId = extNetwork.id;

        plugin.updateFloatingIp(floatingIp.id, floatingIp);

        arpEntries = zk.getChildren(bridgeArpPath);
        Assert.assertTrue(arpEntries.size() == 1);

        for (String entry: arpEntries) {
            Assert.assertTrue(entry.contains(floatingIp.floatingIpAddress));
        }

        floatingIp.portId = null;
        floatingIp.fixedIpAddress = null;
        floatingIp.floatingNetworkId = null;

        plugin.updateFloatingIp(floatingIp.id, floatingIp);

        arpEntries = zk.getChildren(bridgeArpPath);
        Assert.assertTrue(arpEntries.size() == 0);
    }

    @Test
    public void testVifPortIpAddrUpdate()
        throws Rule.RuleIndexOutOfBoundsException, SerializationException,
               StateAccessException {

        // Assert the initial condition that there is one entry in the IP
        // address group matching this security group
        String iagPath = pathBuilder.getIpAddrGroupAddrsPath(securityGroup.id);
        Assert.assertTrue(zk.exists(iagPath));
        Set<String> ipAddrs = zk.getChildren(iagPath);
        Assert.assertEquals(1, ipAddrs.size());
        Assert.assertEquals("10.0.0.5", ipAddrs.iterator().next());

        // Update the SG association and IP address
        UUID newSgId = UUID.randomUUID();
        SecurityGroup newSecurityGroup = new SecurityGroup(
            newSgId, TENANT_ID, "sg_new", "sg_new_desc",
            new ArrayList<SecurityGroupRule>());
        plugin.createSecurityGroup(newSecurityGroup);

        org.midonet.cluster.data.neutron.Port updatedPort =
            new org.midonet.cluster.data.neutron.Port(
            port.id, network.id, TENANT_ID, port.name, port.macAddress,
            Collections.singletonList(new IPAllocation("10.0.0.6", subnet.id)),
            port.deviceOwner, port.deviceId,
            Collections.singletonList(newSgId));
        plugin.updatePort(port.id, updatedPort);

        // Verify that the new IP address group was created with a new IP
        // address.  Also verify that the old IP address group is updated
        // correctly.
        String newIagPath = pathBuilder.getIpAddrGroupAddrsPath(newSgId);
        Assert.assertTrue(zk.exists(newIagPath));
        ipAddrs = zk.getChildren(newIagPath);
        Assert.assertEquals(1, ipAddrs.size());
        Assert.assertEquals("10.0.0.6", ipAddrs.iterator().next());

        Assert.assertTrue(zk.exists(iagPath));
        ipAddrs = zk.getChildren(iagPath);
        Assert.assertEquals(0, ipAddrs.size());
    }
}
