/*
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midonet.api.rest_api;

import com.midokura.midonet.api.filter.rest_api.RuleResource;
import com.midokura.midonet.api.host.rest_api.*;
import com.midokura.midonet.api.network.rest_api.*;
import com.midokura.midonet.api.bgp.rest_api.AdRouteResource;
import com.midokura.midonet.api.bgp.rest_api.BgpResource;
import com.midokura.midonet.api.dhcp.rest_api.BridgeDhcpResource;
import com.midokura.midonet.api.dhcp.rest_api.BridgeFilterDbResource;
import com.midokura.midonet.api.dhcp.rest_api.DhcpHostsResource;
import com.midokura.midonet.api.filter.rest_api.ChainResource;
import com.midokura.midonet.api.monitoring.rest_api.MonitoringResource;
import com.midokura.midonet.api.vpn.rest_api.VpnResource;
import com.midokura.packets.IntIPv4;

import java.util.UUID;

/**
 * Resource factory used by Guice to inject resource classes.
 */
public interface ResourceFactory {

    RouterResource getRouterResource();

    BridgeResource getBridgeResource();

    PortResource getPortResource();

    RouteResource getRouteResource();

    ChainResource getChainResource();

    PortGroupResource getPortGroupResource();

    RuleResource getRuleResource();

    BgpResource getBgpResource();

    AdRouteResource getAdRouteResource();

    VpnResource getVpnResource();

    HostResource getHostResource();

    TunnelZoneResource getTunnelZoneResource();

    TunnelZoneHostResource getTunnelZoneHostResource(UUID id);

    HostInterfacePortResource getHostInterfacePortResource(UUID id);

    MonitoringResource getMonitoringQueryResource();

    AdRouteResource.BgpAdRouteResource getBgpAdRouteResource(UUID id);

    DhcpHostsResource getDhcpAssignmentsResource(UUID bridgeId, IntIPv4 addr);

    PortResource.BridgePortResource getBridgePortResource(UUID id);

    BridgeFilterDbResource getBridgeFilterDbResource(UUID id);

    BridgeDhcpResource getBridgeDhcpResource(UUID id);

    PortResource.BridgePeerPortResource getBridgePeerPortResource(UUID id);

    RuleResource.ChainRuleResource getChainRuleResource(UUID id);

    InterfaceResource getInterfaceResource(UUID id);

    HostCommandResource getHostCommandsResource(UUID id);

    BgpResource.PortBgpResource getPortBgpResource(UUID id);

    VpnResource.PortVpnResource getPortVpnResource(UUID id);

    PortResource.RouterPortResource getRouterPortResource(UUID id);

    RouteResource.RouterRouteResource getRouterRouteResource(UUID id);

    PortResource.RouterPeerPortResource getRouterPeerPortResource(UUID id);

    PortResource.PortGroupPortResource getPortGroupPortResource(UUID id);

}
