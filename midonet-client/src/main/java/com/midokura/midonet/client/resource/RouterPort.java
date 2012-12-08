/*
 * Copyright (c) 2012. Midokura Japan K.K.
 */

package com.midokura.midonet.client.resource;

import java.net.URI;
import java.util.UUID;

import javax.ws.rs.core.MultivaluedMap;

import com.midokura.midonet.client.VendorMediaType;
import com.midokura.midonet.client.WebResource;
import com.midokura.midonet.client.dto.DtoBgp;
import com.midokura.midonet.client.dto.DtoInteriorRouterPort;
import com.midokura.midonet.client.dto.DtoExteriorRouterPort;
import com.midokura.midonet.client.dto.DtoRouterPort;
import com.midokura.midonet.client.dto.PortType;

public class RouterPort<T extends DtoRouterPort> extends
                                                 Port<RouterPort<T>, T> {


    public RouterPort(WebResource resource, URI uriForCreation, T p) {
        super(resource, uriForCreation, p,
              VendorMediaType.APPLICATION_PORT_JSON);
    }

    /**
     * Gets URI for this router port.
     *
     * @return URI for this router port
     */
    @Override
    public URI getUri() {
        return principalDto.getUri();
    }

    /**
     * Gets ID of the device(router) for this router port.
     *
     * @return UUID of the device
     */
    public UUID getDeviceId() {
        return principalDto.getDeviceId();
    }

    /**
     * Gets ID of this router port.
     *
     * @return UUID of this router port
     */
    public UUID getId() {
        return principalDto.getId();
    }

    /**
     * Gets ID of inbound filter id for the router port.
     *
     * @return
     */
    public UUID getInboundFilterId() {
        return principalDto.getInboundFilterId();
    }

    /**
     * Gets network address of this router port.
     *
     * @return network address
     */
    public String getNetworkAddress() {
        return principalDto.getNetworkAddress();
    }

    /**
     * Gets network length.
     *
     * @return network length
     */
    public int getNetworkLength() {
        return principalDto.getNetworkLength();
    }

    /**
     * Gets ID of outbound filter for this router port.
     *
     * @return UUID of the outbound filter
     */
    public UUID getOutboundFilterId() {
        return principalDto.getOutboundFilterId();
    }

    /**
     * Gets address of this router port.
     *
     * @return address of the port
     */
    public String getPortAddress() {
        return principalDto.getPortAddress();
    }

    /**
     * Gets arrays of ID of port groups for this router ports
     *
     * @return array of port group UUIDs
     */
    public UUID[] getPortGroupIDs() {
        return principalDto.getPortGroupIDs();
    }

    /**
     * Gets mac address of the port.
     *
     * @return mac address
     */
    public String getPortMac() {
        return principalDto.getPortMac();
    }

    /**
     * Gets type of the port.
     *
     * @return type
     */
    public String getType() {
        return principalDto.getType();
    }

    /**
     * Gets ID of VIF that is attached to this router port.
     *
     * @return Vif UUID
     */
    public UUID getVifId() {
        return principalDto.getVifId();
    }

    /**
     * Gets ID of the port that is connected to this router port.
     *
     * @return port UUID
     */
    public UUID getPeerId() {
        return ((DtoInteriorRouterPort) principalDto).getPeerId();
    }

    /**
     * Sets network length to the local DTO
     *
     * @param networkLength length of network address mask
     * @return this
     */
    public RouterPort<T> networkLength(int networkLength) {
        ((DtoRouterPort) principalDto).setNetworkLength(networkLength);
        return this;
    }

    /**
     * Sets port group IDs to the local DTO.
     *
     * @param portGroupIDs
     * @return this
     */
    public RouterPort<T> portGroupIDs(UUID[] portGroupIDs) {
        principalDto.setPortGroupIDs(portGroupIDs);
        return this;
    }

    /**
     * Sets outbout filter ID to the local DTO.
     *
     * @param outboundFilterId
     * @return this
     */
    public RouterPort<T> outboundFilterId(UUID outboundFilterId) {
        principalDto.setOutboundFilterId(outboundFilterId);
        return this;
    }

    /**
     * Sets port address to the local DTO.
     *
     * @param portAddress
     * @return this
     */
    public RouterPort<T> portAddress(String portAddress) {
        ((DtoRouterPort) principalDto).setPortAddress(portAddress);
        return this;
    }

    /**
     * Sets Vif ID to the local DTO.
     *
     * @param vifId
     * @return this
     */
    public RouterPort<T> vifId(UUID vifId) {
        principalDto.setVifId(vifId);
        return this;
    }

    /**
     * Sets port mac address to the local DTO.
     *
     * @param portMac
     * @return this
     */
    public RouterPort<T> portMac(String portMac) {
        principalDto.setPortMac(portMac);
        return this;
    }

    /**
     * Sets inbound filter ID to the local DTO.
     *
     * @param inboundFilterId
     * @return this
     */
    public RouterPort<T> inboundFilterId(UUID inboundFilterId) {
        principalDto.setInboundFilterId(inboundFilterId);
        return this;
    }

    /**
     * Sets network address to the local DTO
     *
     * @param networkAddress
     * @return
     */
    public RouterPort<T> networkAddress(String networkAddress) {
        principalDto.setNetworkAddress(networkAddress);
        return this;
    }

    /**
     * Sets peer port ID to the local DTO.
     *
     * @param id
     * @return this
     */
    public RouterPort<T> peerId(UUID id) {
        ((DtoInteriorRouterPort) principalDto).setPeerId(id);
        return this;
    }

    /**
     * Gets collection of bgp resources.
     *
     * @return collection of bgps
     */
    public ResourceCollection<Bgp> getBgps(MultivaluedMap queryParams) {
        if (!principalDto.getType().equals(PortType.EXTERIOR_ROUTER)) {
            throw new IllegalArgumentException("bgp must be added to " +
                                                   "exterior router port");
        }
        return getChildResources(
            ((DtoExteriorRouterPort) principalDto).getBgps(),
            queryParams,
            VendorMediaType.APPLICATION_BGP_COLLECTION_JSON,
            Bgp.class, DtoBgp.class);
    }

    /**
     * Adds BGP resources under this router port for creation
     *
     * @return new Bgp()
     */
    public Bgp addBgp() {
        if (!principalDto.getType().equals(PortType.EXTERIOR_ROUTER)) {
            throw new IllegalArgumentException("bgp must be added to " +
                                                   "exterior router port");
        }
        return new Bgp(resource,
                       ((DtoExteriorRouterPort) principalDto).getBgps(),
                       new DtoBgp());
    }

    /**
     * Creates a link to a interior port
     *
     * @param id the interior port id to connect
     * @return this
     */
    // TODO(pino): this should be defined in a interior port subtype.
    public RouterPort link(UUID id) {
        peerId(id);
        resource.post(((DtoInteriorRouterPort) principalDto).getLink(),
                      principalDto, VendorMediaType.APPLICATION_PORT_JSON);
        get(getUri());
        return this;
    }

    /**
     * Remove the link on the interior port
     *
     * @return this
     */
    public RouterPort unlink() {
        peerId(null);
        resource.post(((DtoInteriorRouterPort) principalDto).getLink(),
                      principalDto, VendorMediaType.APPLICATION_PORT_JSON);
        get(getUri());
        return this;
    }

    @Override
    public String toString() {
        return String.format("RouterPort{id=%s, type=%s}", principalDto.getId(),
                             principalDto.getType());
    }
}
