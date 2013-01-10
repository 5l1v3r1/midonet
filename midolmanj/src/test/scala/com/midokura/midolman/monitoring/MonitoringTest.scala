/*
* Copyright 2012 Midokura Europe SARL
*/
package com.midokura.midolman.monitoring

import collection.JavaConversions._
import collection.{mutable, immutable}
import java.util._

import com.yammer.metrics.Metrics
import com.yammer.metrics.core.Metric
import com.yammer.metrics.core.MetricName
import com.yammer.metrics.core.MetricsRegistry
import org.apache.commons.configuration.HierarchicalConfiguration
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.is
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test

import com.midokura.midolman._
import com.midokura.midolman.monitoring.store.{MockStore, Store}
import com.midokura.midonet.cluster.data.host.Host
import com.midokura.midonet.cluster.data.{Bridge => ClusterBridge,
    Ports => ClusterPorts}
import com.midokura.tools.timed.Timed
import com.midokura.util.Waiters.waitFor
import com.midokura.util.functors.Callback0


class MonitoringTest extends MidolmanTestCase {

  private final val log: Logger = LoggerFactory.getLogger(classOf[MonitoringTest])

  var monitoringAgent: MonitoringAgent = null
  var registry: MetricsRegistry = null

  private def assertTrue(msg: String, flag: Boolean) =
      assertThat(msg, flag, is(true))

  override protected def fillConfig(config: HierarchicalConfiguration): HierarchicalConfiguration = {
    config.setProperty("midolman.midolman_root_key", "/test/v3/midolman")
    config.setProperty("midolman.enable_monitoring", "true")

    config
  }

  @Test def testActualMonitoring {

    val jvmNames = immutable.Set(
      "OpenFileDescriptorCount", "ThreadCount", "FreePhysicalMemorySize",
      "TotalPhysicalMemorySize", "ProcessCPUTime", "TotalSwapSpaceSize", "MaxHeapMemory", "UsedHeapMemory",
      "CommittedHeapMemory", "AvailableProcessors", "FreeSwapSpaceSize", "SystemLoadAverage", "txBytes", "rxBytes",
      "rxPackets","txPackets")

    val store : Store = injector.getInstance(classOf[Store])
    val cassandraStore = store.asInstanceOf[MockStore]
    assertTrue("Initial ports are empty", cassandraStore.getTargets.isEmpty);

    var called : Boolean = false;
    val host = new Host(hostId()).setName("myself")
    clusterDataClient().hostsCreate(hostId(), host)

    initializeDatapath()

    // make a bridge, and a port.
    val bridge = new ClusterBridge().setName("test")
    bridge.setId(clusterDataClient().bridgesCreate(bridge))
    val port = ClusterPorts.materializedBridgePort(bridge)


    port.setId(clusterDataClient().portsCreate(port))

    cassandraStore.subscribeToChangesRegarding(port.getId.toString, new Callback0 {
      def call() {
        called = true;
      }
    })

    materializePort(port, host, "tapDevice")

    // make sure the monitoring agent receives data for the expected port.
    probeByName(MonitoringActor.Name).expectMsgType[DatapathController.PortStats].portID.toString should be (port.getId.toString)


    waitFor("Wait for the MidoReporter to write to Cassandra.",10000, 500,  new Timed.Execution[Boolean]{
      protected def _runOnce() {
          setCompleted(called);
          setResult(called)
      }
    })

    assertTrue("Cassandra contains data about the port", called)

    // get all the metrics names
    registry = Metrics.defaultRegistry;
    val allMetrics: Map[MetricName, Metric] = registry.allMetrics
    val names = mutable.Set.empty[String]

    for (metricName <- allMetrics.keySet()) {
      names += metricName.getName
    }

    assertTrue("The registry contains all the expected metrics.",
               (names.diff(jvmNames)).isEmpty)

    // clean stuff.
    registry.allMetrics().keys.foreach((arg: MetricName) => registry.removeMetric(arg))
  }



}
