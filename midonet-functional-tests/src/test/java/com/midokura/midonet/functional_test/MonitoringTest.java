/*
 * Copyright (c) 2012 Midokura Pte. Ltd
 */
package com.midokura.midonet.functional_test;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.Ignore;
import org.junit.Test;

import com.midokura.midolman.monitoring.metrics.VMMetricsCollection;
import com.midokura.midolman.monitoring.metrics.ZookeeperMetricsCollection;
import com.midokura.midolman.monitoring.metrics.vrn.VifMetrics;
import com.midokura.midolman.monitoring.store.CassandraStore;
import com.midokura.midonet.client.resource.Bridge;
import com.midokura.midonet.client.resource.BridgePort;
import com.midokura.midonet.functional_test.utils.TapWrapper;
import com.midokura.packets.IntIPv4;
import com.midokura.packets.MAC;
import com.midokura.util.lock.LockHelper;


import static com.midokura.midonet.functional_test.FunctionalTestsHelper.assertPacketWasSentOnTap;
import static com.midokura.midonet.functional_test.FunctionalTestsHelper.removeTapWrapper;
import static com.midokura.util.Waiters.sleepBecause;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

@Ignore
public class MonitoringTest extends TestBase {

    static int replicationFactor = 1;
    static int ttlInSecs = 1000;
    CassandraStore store;

    private Bridge bridge;
    private BridgePort intBridgePort;
    private BridgePort tapBridgePort;
    private PacketHelper helperTap_int;
    private IntIPv4 ipInt;
    private IntIPv4 ipTap;
    private TapWrapper metricsTap;

    private static LockHelper.Lock lock;


    @Override
    public void setup() {
        bridge = apiClient.addBridge().name("bridge-metrics").create();

        ipInt = IntIPv4.fromString("192.168.231.4");
        MAC macInt = MAC.fromString("02:aa:bb:cc:ee:d1");
        intBridgePort = bridge.addExteriorPort().create();
        //ovsBridge.addInternalPort(intBridgePort.getId(), "metricsInt",
        //        ipInt, 24);

        ipTap = IntIPv4.fromString("192.168.231.4");
        MAC macTap = MAC.fromString("02:aa:bb:cc:ee:d2");

        tapBridgePort = bridge.addExteriorPort().create();
        metricsTap = new TapWrapper("metricsTap");
        //ovsBridge.addSystemPort(tapBridgePort.getId(), metricsTap.getName());

        helperTap_int = new PacketHelper(macTap, ipTap, macInt, ipInt);

        //TODO: fix me.
        store = new CassandraStore(null);
//        store = new CassandraStore("localhost:9171",
//                "midonet",
//                "midonet_monitoring_keyspace",
//                "midonet_monitoring_column_family",
//                replicationFactor, ttlInSecs);

    }

    @Override
    public void teardown() {
        removeTapWrapper(metricsTap);
    }

    @Test
    public void test() throws Exception {

        long startTime = System.currentTimeMillis();
        String hostName = InetAddress.getLocalHost().getHostName();
        Map<String, Long> resZkMetrics = new HashMap<String, Long>();
        Map<String, Long> resVmMetrics = new HashMap<String, Long>();


        sleepBecause("Let's collect metrics", 5);
        List<String> types = store.getMetricsTypeForTarget(hostName);
        assertThat("We didn't save the metric type", types.size(), greaterThan(2));

        List<String> zkMetrics =
                store.getMetricsForType(ZookeeperMetricsCollection.class.getSimpleName());

        assertThat("We didn't initialize some metric for ZooKeeper",
                zkMetrics.size(), greaterThan(0));

        for (String metric : zkMetrics) {
            Map<String, Long> res = store.getTSPoints(
                    ZookeeperMetricsCollection.class.getSimpleName(),
                    hostName, metric, startTime, System.currentTimeMillis());
            assertThat("The ts points for Zk metrics are > 0",
                    res.size(), greaterThan(0));
            Long lastValue = getValueLastKey(res);
            resZkMetrics.put(metric, lastValue);
        }

        List<String> vmMetrics =
                store.getMetricsForType(VMMetricsCollection.class.getSimpleName());

        assertThat("We didn't initialize some metric for the vm",
                vmMetrics.size(), greaterThan(0));

        for (String metric : vmMetrics) {
            Map<String, Long> res =
                    store.getTSPoints(VMMetricsCollection.class.getSimpleName(),
                            hostName, metric, startTime,
                            System.currentTimeMillis());

            assertThat("The ts points for vm metrics aren't > 0", res.size(),
                    greaterThan(0));

            Long lastValue = getValueLastKey(res);
            resVmMetrics.put(metric, lastValue);
        }

        List<String> vifMetrics = store.getMetricsForType(
                VifMetrics.class.getSimpleName());

        assertThat("We didn't initialize some metric for this vif",
                vifMetrics.size(), greaterThan(0));

        Map<String, Long> rxPackets =
                store.getTSPoints(VifMetrics.class.getSimpleName(),
                        tapBridgePort.getId().toString(), "rxPackets",
                        startTime, System.currentTimeMillis());

        // record the current metric value, take last key in the map
        long previousCount = getValueLastKey(rxPackets);
        long timeBeforeSending = System.currentTimeMillis();
        // send a packet
        assertPacketWasSentOnTap(metricsTap,
                helperTap_int.makeIcmpEchoRequest(ipInt));
        sleepBecause("need to wait for metric to update", 2);

        rxPackets =
                store.getTSPoints(VifMetrics.class.getSimpleName(),
                        tapBridgePort.getId().toString(), "rxPackets",
                        timeBeforeSending, System.currentTimeMillis());

        long currentValue = getValueLastKey(rxPackets);
        // check that the counter increased properly
        assertThat("the counter didn't increased properly",
                currentValue, is(previousCount + 1));

        timeBeforeSending = System.currentTimeMillis();

        // send another packet
        assertPacketWasSentOnTap(metricsTap,
                helperTap_int.makeIcmpEchoRequest(ipInt));
        sleepBecause("need to wait for metric to update", 2);

        rxPackets =
                store.getTSPoints(VifMetrics.class.getSimpleName(),
                        tapBridgePort.getId().toString(), "rxPackets",
                        timeBeforeSending, System.currentTimeMillis());

        currentValue = getValueLastKey(rxPackets);

        // check that the counter increased properly
        assertThat("the counter didn't increase properly",
                currentValue, is(previousCount + 2));

        timeBeforeSending = System.currentTimeMillis();

        assertPacketWasSentOnTap(metricsTap,
                helperTap_int.makeIcmpEchoRequest(ipInt));
        sleepBecause("need to wait for metric to update", 2);

        rxPackets =
                store.getTSPoints(VifMetrics.class.getSimpleName(),
                        tapBridgePort.getId().toString(), "rxPackets",
                        timeBeforeSending, System.currentTimeMillis());

        currentValue = getValueLastKey(rxPackets);

        // check that the counter increased properly
        assertThat("the counter didn't increased properly",
                currentValue, is(previousCount + 3));


        timeBeforeSending = System.currentTimeMillis();

        assertPacketWasSentOnTap(metricsTap,
                helperTap_int.makeIcmpEchoRequest(ipInt));
        sleepBecause("need to wait for metric to update", 2);

        rxPackets =
                store.getTSPoints(VifMetrics.class.getSimpleName(),
                        tapBridgePort.getId().toString(), "rxPackets",
                        timeBeforeSending, System.currentTimeMillis());
        currentValue = getValueLastKey(rxPackets);

        // check that the counter increased properly
        assertThat("the counter didn't increased properly",
                currentValue, is(previousCount + 4));

        // Since it's hard to predict these values, we will just check that they have been modified
        boolean succeed = false;
        for (String metric : zkMetrics) {
            Map<String, Long> res = store.getTSPoints(
                    ZookeeperMetricsCollection.class.getSimpleName(),
                    hostName, metric, timeBeforeSending,
                    System.currentTimeMillis());
            // if at least one metric got update we succeed
            if (!(getValueLastKey(res).equals(
                    (Long) resZkMetrics.get(metric)))) {
                succeed = true;
                break;
            }
        }

        assertThat("The values for Zk metrics haven't been updated",
                succeed, is(true));

        succeed = false;
        for (String metric : vmMetrics) {
            Map<String, Long> res =
                    store.getTSPoints(VMMetricsCollection.class.getSimpleName(),
                            hostName, metric, startTime,
                            System.currentTimeMillis());

            // if at least one metric got update we succeed
            if (!(getValueLastKey(res).equals(
                    (Long) resVmMetrics.get(metric)))) {
                succeed = true;
                break;
            }
        }

        assertThat("The values for VM metrics haven't been updated",
                succeed, is(true));
    }

    public static Long getValueLastKey(Map<String, Long> entries) {

        TreeSet<String> keys = new TreeSet<String>(entries.keySet());
        return entries.get(keys.descendingIterator().next());

    }
}
