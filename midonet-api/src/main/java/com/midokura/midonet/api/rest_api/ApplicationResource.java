/*
 * Copyright 2011 Midokura KK
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midonet.api.rest_api;

import com.google.inject.Inject;
import com.google.inject.servlet.RequestScoped;
import com.midokura.midonet.api.Application;
import com.midokura.midonet.api.VendorMediaType;
import com.midokura.midonet.api.filter.rest_api.RuleResource;
import com.midokura.midonet.api.host.rest_api.HostResource;
import com.midokura.midonet.api.network.rest_api.*;
import com.midokura.midonet.api.vpn.rest_api.VpnResource;
import com.midokura.midonet.api.ResourceUriBuilder;
import com.midokura.midonet.api.bgp.rest_api.AdRouteResource;
import com.midokura.midonet.api.bgp.rest_api.BgpResource;
import com.midokura.midonet.api.filter.rest_api.ChainResource;
import com.midokura.midonet.api.host.rest_api.TunnelZoneResource;
import com.midokura.midonet.api.monitoring.rest_api.MonitoringResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.PermitAll;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

/**
 * The top application resource class.
 */
@RequestScoped
@Path(ResourceUriBuilder.ROOT)
public class ApplicationResource extends AbstractResource {

    private final static Logger log =
            LoggerFactory.getLogger(ApplicationResource.class);

    private final ResourceFactory factory;

    @Inject
    public ApplicationResource(RestApiConfig config, UriInfo uriInfo,
                               SecurityContext context,
                               ResourceFactory factory) {
        super(config, uriInfo, context);
        this.factory = factory;
    }

    /**
     * Router resource locator.
     *
     * @return RouterResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.ROUTERS)
    public RouterResource getRouterResource() {
        return factory.getRouterResource();
    }

    /**
     * Bridge resource locator.
     *
     * @return BridgeResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.BRIDGES)
    public BridgeResource getBridgeResource() {
        return factory.getBridgeResource();
    }

    /**
     * Port resource locator.
     *
     * @return PortResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.PORTS)
    public PortResource getPortResource() {
        return factory.getPortResource();
    }

    /**
     * Route resource locator.
     *
     * @return RouteResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.ROUTES)
    public RouteResource getRouteResource() {
        return factory.getRouteResource();
    }

    /**
     * Chain resource locator.
     *
     * @return ChainResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.CHAINS)
    public ChainResource getChainResource() {
        return factory.getChainResource();
    }

    /**
     * PortGroups resource locator.
     *
     * @return ChainResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.PORT_GROUPS)
    public PortGroupResource getPortGroupResource() {
        return factory.getPortGroupResource();
    }

    /**
     * Rule resource locator.
     *
     * @return RuleResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.RULES)
    public RuleResource getRuleResource() {
        return factory.getRuleResource();
    }

    /**
     * BGP resource locator.
     *
     * @return BgpResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.BGP)
    public BgpResource getBgpResource() {
        return factory.getBgpResource();
    }

    /**
     * Ad route resource locator.
     *
     * @return AdRouteResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.AD_ROUTES)
    public AdRouteResource getAdRouteResource() {
        return factory.getAdRouteResource();
    }

    /**
     * VPN resource locator.
     *
     * @return VpnResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.VPN)
    public VpnResource getVpnResource() {
        return factory.getVpnResource();
    }

    /**
     * Host resource locator
     *
     * @return HostResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.HOSTS)
    public HostResource getHostResource() {
        return factory.getHostResource();
    }

    /**
     * Host resource locator
     *
     * @return HostResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.METRICS)
    public MonitoringResource getMonitoringQueryResource() {
        return factory.getMonitoringQueryResource();
    }

    /**
     * Tunnel Zone resource locator
     *
     * @return TunnelZoneResource object to handle sub-resource requests.
     */
    @Path(ResourceUriBuilder.TUNNEL_ZONES)
    public TunnelZoneResource getTunnelZoneResource() {
        return factory.getTunnelZoneResource();
    }

    /**
     * Handler for getting root application resources.
     *
     * @return An Application object.
     */
    @GET
    @PermitAll
    @Produces({ VendorMediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON })
    public Application get() {
        log.debug("ApplicationResource: entered");

        Application a = new Application(getBaseUri());
        a.setVersion(config.getVersion());

        log.debug("ApplicationResource: existing: " + a);
        return a;
    }
}
