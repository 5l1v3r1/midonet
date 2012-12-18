/*
 * Copyright (c) 2012. Midokura Japan K.K.
 */

package com.midokura.midonet.client.resource;

import java.net.URI;
import java.util.ArrayList;
import java.util.UUID;

import javax.ws.rs.core.MultivaluedMap;

import com.midokura.midonet.client.VendorMediaType;
import com.midokura.midonet.client.WebResource;
import com.midokura.midonet.client.dto.DtoInteriorBridgePort;
import com.midokura.midonet.client.dto.DtoInteriorRouterPort;
import com.midokura.midonet.client.dto.DtoExteriorRouterPort;
import com.midokura.midonet.client.dto.DtoPort;
import com.midokura.midonet.client.dto.DtoRoute;
import com.midokura.midonet.client.dto.DtoRouter;
import com.midokura.midonet.client.dto.DtoRouterPort;

public class Router extends ResourceBase<Router, DtoRouter> {


    public Router(WebResource resource, URI uriForCreation, DtoRouter r) {
        super(resource, uriForCreation, r,
              VendorMediaType.APPLICATION_ROUTER_JSON);
    }

    /**
     * Gets URI for this router.
     *
     * @return URI for this resource
     */
    @Override
    public URI getUri() {
        return principalDto.getUri();
    }

    /**
     * Gets ID of this router.
     *
     * @return UUID of t
     */
    public UUID getId() {
        return principalDto.getId();
    }

    /**
     * Gets ID of the inbound filter on this router.
     *
     * @return UUID of the inbound filter
     */
    public UUID getInboundFilterId() {
        return principalDto.getInboundFilterId();
    }

    /**
     * Gets name of this router.
     *
     * @return name
     */
    public String getName() {
        return principalDto.getName();
    }

    /**
     * Gets ID of oubbound filter on this router.
     *
     * @return UUID of the outbound filter
     */
    public UUID getOutboundFilterId() {
        return principalDto.getOutboundFilterId();
    }

    /**
     * Gets tenant ID for this router.
     *
     * @return tenant ID string
     */
    public String getTenantId() {
        return principalDto.getTenantId();
    }

    /**
     * Sets name.
     *
     * @param name
     * @return this
     */
    public Router name(String name) {
        principalDto.setName(name);
        return this;
    }

    /**
     * Sets tenantID
     *
     * @param tenantId
     * @return this
     */
    public Router tenantId(String tenantId) {
        principalDto.setTenantId(tenantId);
        return this;
    }

    public Router outboundFilterId(UUID outboundFilterId) {
        principalDto.setOutboundFilterId(outboundFilterId);
        return this;
    }

    public Router inboundFilterId(UUID inboundFilterId) {
        principalDto.setInboundFilterId(inboundFilterId);
        return this;
    }

    /**
     * Gets ports under the router.
     *
     * @return collection of router ports
     */
    public ResourceCollection<RouterPort> getPorts(MultivaluedMap queryParams) {
        return getChildResources(
            principalDto.getPorts(),
            queryParams,
            VendorMediaType.APPLICATION_PORT_COLLECTION_JSON,
            RouterPort.class,
            DtoRouterPort.class);
    }

    /**
     * Gets routes under the router.
     *
     * @return collection of routes
     */
    public ResourceCollection<Route> getRoutes(MultivaluedMap queryParams) {
        return getChildResources(
            principalDto.getRoutes(),
            queryParams,
            VendorMediaType.APPLICATION_ROUTE_COLLECTION_JSON,
            Route.class,
            DtoRoute.class);
    }

    /**
     * Gets peer ports under the router.
     *
     * @return collection of ports
     */
    public ResourceCollection<Port> getPeerPorts(MultivaluedMap queryParams) {
        ResourceCollection<Port> peerPorts =
            new ResourceCollection<Port>(new ArrayList<Port>());

        DtoPort[] dtoPeerPorts = resource
            .get(principalDto.getPeerPorts(),
                 queryParams,
                 DtoPort[].class,
                 VendorMediaType.APPLICATION_PORT_COLLECTION_JSON);

        for (DtoPort pp : dtoPeerPorts) {
            Port p = null;
            if (pp instanceof DtoInteriorRouterPort) {
                p = new RouterPort<DtoInteriorRouterPort>(
                    resource,
                    principalDto.getPorts(),
                    (DtoInteriorRouterPort) pp);
            } else if (pp instanceof DtoInteriorBridgePort) {
                p = new BridgePort<DtoInteriorBridgePort>(
                    resource,
                    principalDto.getPorts(),
                    (DtoInteriorBridgePort) pp);

            }
            peerPorts.add(p);
        }
        return peerPorts;
    }

    /**
     * Returns Exterior port resource for creation.
     *
     * @return Exterior port resource
     */
    public RouterPort<DtoExteriorRouterPort> addExteriorRouterPort() {
        return new RouterPort<DtoExteriorRouterPort>(
            resource,
            principalDto.getPorts(),
            new DtoExteriorRouterPort());
    }

    /**
     * Returns Interior port resource for creation.
     *
     * @return Interior port resource
     */

    public RouterPort<DtoInteriorRouterPort> addInteriorRouterPort() {
        return new RouterPort<DtoInteriorRouterPort>(
            resource,
            principalDto.getPorts(),
            new DtoInteriorRouterPort());
    }

    /**
     * Returns route resource for creation.
     *
     * @return route resource
     */
    public Route addRoute() {
        return new Route(resource, principalDto.getRoutes(), new DtoRoute());
    }

    @Override
    public String toString() {
        return String.format("Router{id=%s, name=%s}", principalDto.getId(),
                             principalDto.getName());
    }
}
