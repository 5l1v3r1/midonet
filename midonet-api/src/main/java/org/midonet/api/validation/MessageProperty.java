/*
 * Copyright 2012 Midokura PTE LTD.
 */
package org.midonet.api.validation;

import java.util.ResourceBundle;

public class MessageProperty {

    public static final String ALLOWED_VALUES =
            "{midokura.javarx.AllowedValue.message}";
    public static final String BGP_NOT_UNIQUE =
            "{midokura.javarx.IsUniqueBgpInPort.message}";
    public static final String BRIDGE_EXISTS =
            "{midokura.javarx.BridgeExists.message}";
    public static final String BRIDGE_HAS_MAC_PORT =
            "{midokura.javarx.BridgeHasMacPort.message}";
    public static final String BRIDGE_HAS_VLAN =
            "{midokura.javarx.BridgeHasVlan.message}";
    public static final String HOST_ID_IS_INVALID =
            "{midokura.javarx.HostIdIsInvalid.message}";
    public static final String HOST_INTERFACE_IS_USED =
            "{midokura.javarx.HostInterfaceIsAlreadyUsed.message}";
    public static final String IS_UNIQUE_BRIDGE_NAME =
            "{midokura.javarx.IsUniqueBridgeName.message}";
    public static final String IS_UNIQUE_CHAIN_NAME =
            "{midokura.javarx.IsUniqueChainName.message}";
    public static final String IS_UNIQUE_PORT_GROUP_NAME =
            "{midokura.javarx.IsUniquePortGroupName.message}";
    public static final String IS_UNIQUE_ROUTER_NAME =
            "{midokura.javarx.IsUniqueRouterName.message}";
    public static final String IS_UNIQUE_VLAN_BRIDGE_NAME =
        "{midokura.javarx.IsUniqueVlanBridgeName.message}";
    public static final String MAC_PORT_ON_BRIDGE =
            "{midokura.javarx.MacPortOnBridge.message}";
    public static final String MAC_URI_FORMAT =
            "{midokura.javarx.MacUriFormat.message}";
    public static final String PORT_ID_IS_INVALID =
            "{midokura.javarx.PortIdIsInvalid.message}";
    public static final String PORT_GROUP_ID_IS_INVALID =
            "{midokura.javarx.PortGroupIdIsInvalid.message}";
    public static final String PORTS_LINKABLE =
            "{midokura.javarx.PortsLinkable.message}";
    public static final String RESOURCE_NOT_FOUND =
            "{midokura.javarx.ResourceNotFound.message}";
    public static final String ROUTE_NEXT_HOP_PORT_NOT_NULL =
            "{midokura.javarx.RouteNextHopPortValid.message}";
    public static final String TUNNEL_ZONE_ID_IS_INVALID =
            "{midokura.javarx.TunnelZoneIdIsInvalid.message}";
    public static final String UNIQUE_TUNNEL_ZONE_NAME_TYPE =
        "{midokura.javarx.TunnelZoneNameExists.message}";
    public static final String TUNNEL_ZONE_MEMBER_EXISTS =
            "{midokura.javarx.TunnelZoneMemberExists.message}";
    public static final String VLAN_ID_MATCHES_PORT_VLAN_ID =
            "{midokura.javarx.VlanIdMatchesPortVlanId.message}";

    private static ResourceBundle resourceBundle =
            ResourceBundle.getBundle("ValidationMessages");

    /**
     * Loads a message from the ValidationMessages properties file and
     * interpolates the specified arguments using String.format().
     * @param key Key of message to load. Possible values are enumerated as
     *            static members of MessageProperty.
     * @param args Arguments to interpolate.
     * @return Requested message, with args interpolated.
     */
    public static String getMessage(String key, Object... args) {
        if (key.startsWith("{") && key.endsWith("}"))
            key = key.substring(1, key.length() - 1);
        String template = resourceBundle.getString(key);
        return (args.length == 0) ?
                template : String.format(template, args);
    }
}
