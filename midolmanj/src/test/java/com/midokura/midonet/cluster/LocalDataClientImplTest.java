/*
 * Copyright 2012 Midokura Europe SARL
 */

package com.midokura.midonet.cluster;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.junit.Before;
import org.junit.Test;

import com.midokura.midolman.Setup;
import com.midokura.midolman.config.MidolmanConfig;
import com.midokura.midolman.config.ZookeeperConfig;
import com.midokura.midolman.guice.MockMonitoringStoreModule;
import com.midokura.midolman.guice.cluster.DataClusterClientModule;
import com.midokura.midolman.guice.config.MockConfigProviderModule;
import com.midokura.midolman.guice.config.TypedConfigModule;
import com.midokura.midolman.guice.reactor.ReactorModule;
import com.midokura.midolman.guice.zookeeper.MockZookeeperConnectionModule;
import com.midokura.midolman.layer3.Route.NextHop;
import com.midokura.midolman.state.Directory;
import com.midokura.midolman.state.StateAccessException;
import com.midokura.midolman.state.zkManagers.RouteZkManager;
import com.midokura.midolman.state.zkManagers.RouterZkManager;
import com.midokura.midonet.cluster.data.Route;
import com.midokura.midonet.cluster.data.Router;
import com.midokura.midonet.cluster.data.ports.MaterializedRouterPort;
import com.midokura.packets.MAC;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

public class LocalDataClientImplTest {

    @Inject
    DataClient client;
    Injector injector = null;
    String zkRoot = "/test/v3/midolman";


    HierarchicalConfiguration fillConfig(HierarchicalConfiguration config) {
        config.addNodes(ZookeeperConfig.GROUP_NAME,
            Arrays.asList(new HierarchicalConfiguration.Node
                ("midolman_root_key", zkRoot)));
        return config;

    }

    RouteZkManager getRouteZkManager() {
        return injector.getInstance(RouteZkManager.class);
    }

    RouterZkManager getRouterZkManager() {
        return injector.getInstance(RouterZkManager.class);
    }

    Directory zkDir() {
        return injector.getInstance(Directory.class);
    }

    @Before
    public void initialize() throws InterruptedException, KeeperException {
        HierarchicalConfiguration config = fillConfig(
            new HierarchicalConfiguration());
        injector = Guice.createInjector(
            new MockConfigProviderModule(config),
            new MockZookeeperConnectionModule(),
            new TypedConfigModule<MidolmanConfig>(MidolmanConfig.class),

            new ReactorModule(),
            new MockMonitoringStoreModule(),
            new DataClusterClientModule()
        );
        injector.injectMembers(this);
        String[] nodes = zkRoot.split("/");
        String path = "/";

        for (String node : nodes) {
            if (!node.isEmpty()) {
                zkDir().add(path + node, null, CreateMode.PERSISTENT);
                path += node;
                path += "/";
            }
        }
        Setup.createZkDirectoryStructure(zkDir(), zkRoot);
    }

    @Test
    public void routerPortLifecycleTest() throws StateAccessException {
        // Create a materialized router port.
        UUID routerId = client.routersCreate(new Router());
        UUID portId = client.portsCreate(
            new MaterializedRouterPort().setDeviceId(routerId)
                .setHwAddr(MAC.fromString("02:BB:EE:EE:FF:01"))
                .setPortAddr("10.0.0.3").setNwAddr("10.0.0.0")
                .setNwLength(24)
        );
        // Verify that this automatically creates one route.
        List<Route> routes = client.routesFindByRouter(routerId);
        assertThat(routes, hasSize(1));
        Route rt = routes.get(0);
        // Verify that the route is type LOCAL and forwards to the new port.
        assertThat(rt.getNextHop(), equalTo(NextHop.LOCAL));
        assertThat(rt.getNextHopPort(), equalTo(portId));
        // Now delete the port and verify that the route is deleted.
        client.portsDelete(portId);
        routes = client.routesFindByRouter(routerId);
        assertThat(routes, hasSize(0));
    }
}
