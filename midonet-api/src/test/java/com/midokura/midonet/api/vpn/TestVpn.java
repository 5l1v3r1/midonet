/*
 * Copyright 2011 Midokura KK
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midonet.api.vpn;

import com.midokura.midonet.api.VendorMediaType;
import com.midokura.midonet.api.zookeeper.StaticMockDirectory;
import com.midokura.midonet.api.rest_api.FuncTest;
import com.midokura.midolman.state.zkManagers.VpnZkManager;
import com.midokura.midonet.client.dto.DtoApplication;
import com.midokura.midonet.client.dto.DtoExteriorRouterPort;
import com.midokura.midonet.client.dto.DtoRouter;
import com.midokura.midonet.client.dto.DtoVpn;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.test.framework.JerseyTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class TestVpn extends JerseyTest {

    private final static Logger log = LoggerFactory.getLogger(TestVpn.class);
    private final String testRouterName = "TEST-ROUTER";

    private WebResource resource;
    private ClientResponse response;
    private URI testRouterUri;
    private URI portUri;

    private UUID privatePortId;

    DtoRouter router = new DtoRouter();

    public TestVpn() {
        super(FuncTest.appDesc);
    }

    @Before
    public void before() {

        DtoApplication app = resource().path("").accept(VendorMediaType.APPLICATION_JSON)
                .get(DtoApplication.class);

        // Create a router.
        router.setName(testRouterName);
        router.setTenantId("tenant1-id");
        resource = resource().uri(app.getRouters());
        response = resource.type(VendorMediaType.APPLICATION_ROUTER_JSON).post(
                ClientResponse.class, router);

        log.debug("router location: {}", response.getLocation());
        testRouterUri = response.getLocation();
        router = resource().uri(testRouterUri).accept(
                VendorMediaType.APPLICATION_ROUTER_JSON).get(DtoRouter.class);

        DtoExteriorRouterPort port = new DtoExteriorRouterPort();
        String portAddress = "180.214.47.66";
        port.setNetworkAddress("180.214.47.64");
        port.setNetworkLength(30);
        port.setPortAddress(portAddress);
        resource = resource().uri(router.getPorts());
        log.debug("port JSON {}", port.toString());

        response = resource.type(VendorMediaType.APPLICATION_PORT_JSON).post(
                ClientResponse.class, port);
        portUri = response.getLocation();
        log.debug("port location: {}", portUri);

        // Add a Exterior router port for private port of VPN.
        port = new DtoExteriorRouterPort();
        portAddress = "192.168.10.1";
        port.setNetworkAddress("192.168.10.0");
        port.setNetworkLength(30);
        port.setPortAddress(portAddress);
        resource = resource().uri(router.getPorts());
        response = resource.type(VendorMediaType.APPLICATION_PORT_JSON).post(
                ClientResponse.class, port);
        log.debug("port JSON {}", port.toString());

        response = resource.type(VendorMediaType.APPLICATION_PORT_JSON).post(
                ClientResponse.class, port);
        log.debug("port location: {}", response.getLocation());

        privatePortId = FuncTest.getUuidFromLocation(response.getLocation());
    }

    @After
    public void resetDirectory() throws Exception {
        StaticMockDirectory.clearDirectoryInstance();
    }

    @Test
    public void testCreateGetListDelete() {

        // create a vpn entry
        DtoVpn vpn = new DtoVpn();
        int vpnPort = 1234;
        vpn.setPort(vpnPort);
        vpn.setPrivatePortId(privatePortId);
        vpn.setVpnType(DtoVpn.VpnType.OPENVPN_SERVER);

        resource = resource().uri(
                UriBuilder.fromUri(portUri).path("vpns").build());
        response = resource.type(VendorMediaType.APPLICATION_VPN_JSON).post(
                ClientResponse.class, vpn);
        URI vpnUri = response.getLocation();

        log.debug("vpn location: {}", vpnUri);
        log.debug("status {}", response.getLocation());

        // Get the vpn
        resource = resource().uri(vpnUri);
        response = resource.type(VendorMediaType.APPLICATION_VPN_JSON)
                .get(ClientResponse.class);
        vpn = response.getEntity(DtoVpn.class);
        log.debug("vpn port: {}", vpn.getPort());
        assertEquals(200, response.getStatus());
        assertEquals(vpnPort, vpn.getPort());

        // List vpns
        resource = resource().uri(
                UriBuilder.fromUri(portUri).path("vpns").build());
        response = resource.type(VendorMediaType.APPLICATION_VPN_JSON)
                .get(ClientResponse.class);
        assertEquals(200, response.getStatus());
        log.debug("body: {}", response.getEntity(String.class));

        // Delete the vpn
        response = resource().uri(vpnUri).type(VendorMediaType.APPLICATION_VPN_JSON)
                .delete(ClientResponse.class);
        assertEquals(204, response.getStatus());
    }

    @Test
    public void testConvertVpnType() {
        assertEquals(DtoVpn.VpnType.OPENVPN_CLIENT, Enum.valueOf(
                DtoVpn.VpnType.class,
                VpnZkManager.VpnType.OPENVPN_CLIENT.name()));
        assertEquals(DtoVpn.VpnType.OPENVPN_SERVER, Enum.valueOf(
                DtoVpn.VpnType.class,
                VpnZkManager.VpnType.OPENVPN_SERVER.name()));
        assertEquals(DtoVpn.VpnType.OPENVPN_TCP_CLIENT, Enum.valueOf(
                DtoVpn.VpnType.class,
                VpnZkManager.VpnType.OPENVPN_TCP_CLIENT.name()));
        assertEquals(DtoVpn.VpnType.OPENVPN_TCP_SERVER, Enum.valueOf(
                DtoVpn.VpnType.class,
                VpnZkManager.VpnType.OPENVPN_TCP_SERVER.name()));

        assertEquals(VpnZkManager.VpnType.OPENVPN_CLIENT, Enum.valueOf(
                VpnZkManager.VpnType.class,
                DtoVpn.VpnType.OPENVPN_CLIENT.name()));
        assertEquals(VpnZkManager.VpnType.OPENVPN_SERVER, Enum.valueOf(
                VpnZkManager.VpnType.class,
                DtoVpn.VpnType.OPENVPN_SERVER.name()));
        assertEquals(VpnZkManager.VpnType.OPENVPN_TCP_CLIENT, Enum.valueOf(
                VpnZkManager.VpnType.class,
                DtoVpn.VpnType.OPENVPN_TCP_CLIENT.name()));
        assertEquals(VpnZkManager.VpnType.OPENVPN_TCP_SERVER, Enum.valueOf(
                VpnZkManager.VpnType.class,
                DtoVpn.VpnType.OPENVPN_TCP_SERVER.name()));
    }
}
