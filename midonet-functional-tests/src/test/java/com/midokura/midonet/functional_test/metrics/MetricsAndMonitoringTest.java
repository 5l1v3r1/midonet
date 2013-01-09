/*
* Copyright 2012 Midokura Europe SARL
*/
package com.midokura.midonet.functional_test.metrics;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;

import org.junit.Ignore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.midokura.util.Waiters.sleepBecause;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.midokura.midolman.monitoring.metrics.vrn.VifMetrics;
import com.midokura.midonet.client.resource.Bridge;
import com.midokura.midonet.client.resource.BridgePort;
import com.midokura.midonet.functional_test.TestBase;
import com.midokura.packets.IntIPv4;
import com.midokura.packets.MAC;
import com.midokura.midonet.functional_test.FunctionalTestsHelper;
import com.midokura.midonet.functional_test.PacketHelper;
import com.midokura.midonet.functional_test.utils.TapWrapper;
import com.midokura.util.jmx.JMXAttributeAccessor;
import com.midokura.util.jmx.JMXHelper;
import com.midokura.util.lock.LockHelper;
import static com.midokura.midonet.functional_test.FunctionalTestsHelper.assertPacketWasSentOnTap;
import static com.midokura.midonet.functional_test.FunctionalTestsHelper.fixQuaggaFolderPermissions;
import static com.midokura.midonet.functional_test.FunctionalTestsHelper.removeTapWrapper;
import static com.midokura.midonet.functional_test.FunctionalTestsHelper.stopMidolman;

/**
 * Functional tests for the metrics and monitoring.
 */
@Ignore
public class MetricsAndMonitoringTest extends TestBase {

    private Bridge bridge;
    private BridgePort intBridgePort;
    private BridgePort tapBridgePort;
    private PacketHelper helperTap_int;
    private IntIPv4 ipInt;
    private IntIPv4 ipTap;
    private TapWrapper metricsTap;

    @Override
    public void setup() {

        //fixQuaggaFolderPermissions();

        bridge = apiClient.addBridge().name("bridge-metrics").create();
        ipInt = IntIPv4.fromString("192.168.231.4");
        MAC macInt = MAC.fromString("02:aa:bb:cc:ee:d1");
        intBridgePort = bridge.addExteriorPort().create();
        //ovsBridge.addInternalPort(intBridgePort.getId(), "metricsInt",
                                  //ipInt, 24);

        ipTap = IntIPv4.fromString("192.168.231.4");
        MAC macTap = MAC.fromString("02:aa:bb:cc:ee:d2");
        tapBridgePort = bridge.addExteriorPort().create();
        metricsTap = new TapWrapper("metricsTap");
        //ovsBridge.addSystemPort(tapBridgePort.getId(), metricsTap.getName());

        helperTap_int = new PacketHelper(macTap, ipTap, macInt, ipInt);
    }

    @Override
    public void teardown() {
        removeTapWrapper(metricsTap);
    }

    @Test
    public void testRxMetrics() throws Exception {

        JMXConnector connector =
            JMXHelper.newJvmJmxConnectorForPid(1 /*midolman.getPid()*/);

        assertThat("The JMX connection should have been non-null.",
                   connector, notNullValue());

        MBeanServerConnection connection = connector.getMBeanServerConnection();

        JMXAttributeAccessor<Long> rxPkts =
            JMXHelper.<Long>newAttributeAccessor(connection, "Count")
                     .withNameDomain(VifMetrics.class)
                     .withNameKey("type", VifMetrics.class)
                     .withNameKey("scope", tapBridgePort.getId())
                     .withNameKey("name", "rxPackets")
                     .build();

        // record the current metric value
        long previousCount = rxPkts.getValue();

        // send a packet
        assertPacketWasSentOnTap(metricsTap,
                                 helperTap_int.makeIcmpEchoRequest(ipInt));
        sleepBecause("need to wait for metric to update", 1);

        // check that the counter increased properly
        assertThat("the counter didn't increased properly",
                   rxPkts.getValue(), is(previousCount + 1));

        // send another packet
        assertPacketWasSentOnTap(metricsTap,
                                 helperTap_int.makeIcmpEchoRequest(ipInt));
        sleepBecause("need to wait for metric to update", 1);

        // check that the counter increased properly
        assertThat("the counter didn't increase properly",
                   rxPkts.getValue(), is(previousCount + 2));

        assertPacketWasSentOnTap(metricsTap,
                                 helperTap_int.makeIcmpEchoRequest(ipInt));
        sleepBecause("need to wait for metric to update", 1);

        // check that the counter increased properly
        assertThat("the counter didn't increased properly",
                   rxPkts.getValue(), is(previousCount + 3));

        assertPacketWasSentOnTap(metricsTap,
                                 helperTap_int.makeIcmpEchoRequest(ipInt));
        sleepBecause("need to wait for metric to update", 1);

        // check that the counter increased properly
        assertThat("the counter didn't increased properly",
                   rxPkts.getValue(), is(previousCount + 4));

        connector.close();
    }
}
