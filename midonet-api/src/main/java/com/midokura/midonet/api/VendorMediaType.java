/*
 * Copyright 2011 Midokura KK
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midonet.api;

/**
 * Vendor media types
 */
public class VendorMediaType {

    public static final String APPLICATION_JSON = "application/vnd.com.midokura.midolman.mgmt.Application+json";
    public static final String APPLICATION_ERROR_JSON = "application/vnd.com.midokura.midolman.mgmt.Error+json";
    public static final String APPLICATION_ROUTER_JSON = "application/vnd.com.midokura.midolman.mgmt.Router+json";
    public static final String APPLICATION_ROUTER_COLLECTION_JSON = "application/vnd.com.midokura.midolman.mgmt.collection.Router+json";
    public static final String APPLICATION_BRIDGE_JSON = "application/vnd.com.midokura.midolman.mgmt.Bridge+json";
    public static final String APPLICATION_BRIDGE_COLLECTION_JSON = "application/vnd.com.midokura.midolman.mgmt.collection.Bridge+json";
    public static final String APPLICATION_HOST_JSON = "application/vnd.com.midokura.midolman.mgmt.Host+json";
    public static final String APPLICATION_HOST_COLLECTION_JSON = "application/vnd.com.midokura.midolman.mgmt.collection.Host+json";
    public static final String APPLICATION_INTERFACE_JSON = "application/vnd.com.midokura.midolman.mgmt.Interface+json";
    public static final String APPLICATION_INTERFACE_COLLECTION_JSON = "application/vnd.com.midokura.midolman.mgmt.collection.Interface+json";
    public static final String APPLICATION_HOST_COMMAND_JSON = "application/vnd.com.midokura.midolman.mgmt.HostCommand+json";
    public static final String APPLICATION_HOST_COMMAND_COLLECTION_JSON = "application/vnd.com.midokura.midolman.mgmt.collection.HostCommand+json";
    public static final String APPLICATION_PORT_JSON = "application/vnd.com.midokura.midolman.mgmt.Port+json";
    public static final String APPLICATION_PORT_COLLECTION_JSON = "application/vnd.com.midokura.midolman.mgmt.collection.Port+json";
    public static final String APPLICATION_ROUTE_JSON = "application/vnd.com.midokura.midolman.mgmt.Route+json";
    public static final String APPLICATION_ROUTE_COLLECTION_JSON = "application/vnd.com.midokura.midolman.mgmt.collection.Route+json";
    public static final String APPLICATION_PORTGROUP_JSON = "application/vnd.com.midokura.midolman.mgmt.PortGroup+json";
    public static final String APPLICATION_PORTGROUP_COLLECTION_JSON = "application/vnd.com.midokura.midolman.mgmt.collection.PortGroup+json";
    public static final String APPLICATION_PORTGROUP_PORT_JSON =
            "application/vnd.com.midokura.midolman.mgmt.PortGroupPort+json";
    public static final String APPLICATION_PORTGROUP_PORT_COLLECTION_JSON =
            "application/vnd.com.midokura.midolman.mgmt.collection.PortGroupPort+json";
    public static final String APPLICATION_CHAIN_JSON = "application/vnd.com.midokura.midolman.mgmt.Chain+json";
    public static final String APPLICATION_CHAIN_COLLECTION_JSON = "application/vnd.com.midokura.midolman.mgmt.collection.Chain+json";
    public static final String APPLICATION_RULE_JSON = "application/vnd.com.midokura.midolman.mgmt.Rule+json";
    public static final String APPLICATION_RULE_COLLECTION_JSON = "application/vnd.com.midokura.midolman.mgmt.collection.Rule+json";
    public static final String APPLICATION_BGP_JSON = "application/vnd.com.midokura.midolman.mgmt.Bgp+json";
    public static final String APPLICATION_BGP_COLLECTION_JSON = "application/vnd.com.midokura.midolman.mgmt.collection.Bgp+json";
    public static final String APPLICATION_AD_ROUTE_JSON = "application/vnd.com.midokura.midolman.mgmt.AdRoute+json";
    public static final String APPLICATION_AD_ROUTE_COLLECTION_JSON = "application/vnd.com.midokura.midolman.mgmt.collection.AdRoute+json";
    public static final String APPLICATION_VPN_JSON = "application/vnd.com.midokura.midolman.mgmt.Vpn+json";
    public static final String APPLICATION_VPN_COLLECTION_JSON = "application/vnd.com.midokura.midolman.mgmt.collection.Vpn+json";

    /* DHCP configuration types. */
    public static final String APPLICATION_DHCP_SUBNET_JSON = "application/vnd.com.midokura.midolman.mgmt.DhcpSubnet+json";
    public static final String APPLICATION_DHCP_SUBNET_COLLECTION_JSON = "application/vnd.com.midokura.midolman.mgmt.collection.DhcpSubnet+json";
    public static final String APPLICATION_DHCP_HOST_JSON = "application/vnd.com.midokura.midolman.mgmt.DhcpHost+json";
    public static final String APPLICATION_DHCP_HOST_COLLECTION_JSON = "application/vnd.com.midokura.midolman.mgmt.collection.DhcpHost+json";

    public static final String APPLICATION_MONITORING_QUERY_RESPONSE_COLLECTION_JSON = "application/vnd.com.midokura.midolman.collection.mgmt.MetricQueryResponse+json";
    public static final String APPLICATION_MONITORING_QUERY_COLLECTION_JSON = "application/vnd.com.midokura.midolman.mgmt.collection.MetricQuery+json";
    public static final String APPLICATION_METRICS_COLLECTION_JSON = "application/vnd.com.midokura.midolman.mgmt.collection.Metric+json";
    public static final String APPLICATION_METRIC_TARGET_JSON = "application/vnd.com.midokura.midolman.mgmt.MetricTarget+json";

    // Tunnel Zones
    public static final String APPLICATION_TUNNEL_ZONE_JSON =
            "application/vnd.com.midokura.midolman.mgmt.TunnelZone+json";
    public static final String APPLICATION_TUNNEL_ZONE_COLLECTION_JSON =
            "application/vnd.com.midokura.midolman.mgmt.collection" +
                    ".TunnelZone+json";

    public static final String APPLICATION_TUNNEL_ZONE_HOST_JSON =
        "application/vnd.com.midokura.midolman.mgmt" +
            ".TunnelZoneHost+json";
    public static final String
        APPLICATION_TUNNEL_ZONE_HOST_COLLECTION_JSON =
        "application/vnd.com.midokura.midolman.mgmt.collection" +
            ".TunnelZoneHost+json";
    public static final String APPLICATION_CAPWAP_TUNNEL_ZONE_HOST_JSON =
            "application/vnd.com.midokura.midolman.mgmt" +
                    ".CapwapTunnelZoneHost+json";
    public static final String
            APPLICATION_CAPWAP_TUNNEL_ZONE_HOST_COLLECTION_JSON =
            "application/vnd.com.midokura.midolman.mgmt.collection" +
                    ".CapwapTunnelZoneHost+json";
    public static final String APPLICATION_GRE_TUNNEL_ZONE_HOST_JSON =
            "application/vnd.com.midokura.midolman.mgmt" +
                    ".GreTunnelZoneHost+json";
    public static final String
            APPLICATION_GRE_TUNNEL_ZONE_HOST_COLLECTION_JSON =
            "application/vnd.com.midokura.midolman.mgmt.collection" +
                    ".GreTunnelZoneHost+json";
    public static final String APPLICATION_IPSEC_TUNNEL_ZONE_HOST_JSON =
            "application/vnd.com.midokura.midolman.mgmt" +
                    ".IpsecTunnelZoneHost+json";
    public static final String
            APPLICATION_IPSEC_TUNNEL_ZONE_HOST_COLLECTION_JSON =
            "application/vnd.com.midokura.midolman.mgmt.collection" +
                    ".IpsecTunnelZoneHost+json";

    // Host interface - port mapping
    public static final String APPLICATION_HOST_INTERFACE_PORT_JSON =
            "application/vnd.com.midokura.midolman.mgmt" +
                    ".HostInterfacePort+json";
    public static final String
            APPLICATION_HOST_INTERFACE_PORT_COLLECTION_JSON =
            "application/vnd.com.midokura.midolman.mgmt.collection" +
                    ".HostInterfacePort+json";
}
