package com.midokura.midonet.client;

import java.net.URI;
import java.util.UUID;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;

import com.midokura.midonet.client.dto.*;
import com.midokura.midonet.client.resource.*;


/**
 * Midonet API wrapping class.
 */
public class MidonetMgmt {

    private static final String DEFAULT_MIDONET_URI =
        "http://localhost:8080/midolmanj-mgmt";

    private final URI midonetUri;
    private final WebResource resource;
    private Application application;

    public MidonetMgmt(String midonetUriStr) {
        this.midonetUri = URI.create(midonetUriStr);
        resource = new WebResource(midonetUri);
    }

    public MidonetMgmt() {
        this(DEFAULT_MIDONET_URI);
    }

    public void enableLogging() {
        resource.enableLogging();
    }

    public void disableLogging() {
        resource.disableLogging();
    }

    /**
     * Adds a Bridge.
     *
     * @return a bridge resource
     */
    public Bridge addBridge() {
        ensureApplication();
        return application.addBridge();
    }

    /**
     * Adds a Router.
     *
     * @return a router resource
     */
    public Router addRouter() {
        ensureApplication();
        return application.addRouter();
    }

    /**
     * Adds a Chain.
     *
     * @return chain resource
     */
    public RuleChain addChain() {
        ensureApplication();
        return application.addChain();
    }

    /**
     * Adds a PortGroup.
     *
     * @return port group resource
     */
    public PortGroup addPortGroup() {
        ensureApplication();
        return application.addPortGroup();
    }

    /**
     * Adds a GRE tunnel zone
     *
     * @return gre tunnel zone resource
     */
    public TunnelZone<DtoGreTunnelZone> addGreTunnelZone() {
        ensureApplication();
        return application.addGreTunnelZone();
    }

    /**
     * Adds a CAPWAP tunnel zone
     *
     * @return capwap tunnel zone resource
     */
    public TunnelZone<DtoCapwapTunnelZone> addCapwapTunnelZone() {
        ensureApplication();
        return application.addCapwapTunnelZone();
    }

    /**
     * Gets Bridges.
     *
     * @return collection of bridge
     */
    public ResourceCollection<Bridge> getBridges(MultivaluedMap queryParams) {
        ensureApplication();
        return application.getBridges(queryParams);
    }

    /**
     * Gets Routers.
     *
     * @return collection of router
     */
    public ResourceCollection<Router> getRouters(MultivaluedMap queryParams) {
        ensureApplication();
        return application.getRouters(queryParams);
    }

    /**
     * Gets Chains.
     *
     * @return collection of chain
     */
    public ResourceCollection<RuleChain> getChains(MultivaluedMap queryParams) {
        ensureApplication();
        return application.getChains(queryParams);
    }

    /**
     * Gets PortGroups.
     *
     * @return collection of port group
     */
    public ResourceCollection<PortGroup> getPortGroups(
        MultivaluedMap queryParams) {
        ensureApplication();
        return application.getPortGroups(queryParams);
    }

    /**
     * Gets Hosts
     *
     * @return collection of host
     */
    public ResourceCollection<Host> getHosts() {
        ensureApplication();
        return application.getHosts(null);
    }

    /**
     * Gets Tunnel Zones
     *
     * @return collection of tunnel zone
     */
    public ResourceCollection<TunnelZone>
        getTunnelZones() {
        ensureApplication();
        return application.getTunnelZones(null);
    }

    /**
     * Returns an ad route
     *
     * @param id ID of ad route
     * @return AdRoute
     */
    public AdRoute getAdRoute(UUID id) {
        ensureApplication();
        return application.getAdRoute(id);
    }

    /**
     * Returns BGP object
     *
     * @param id ID of BGP
     * @return BGP
     */
    public Bgp getBgp(UUID id) {
        ensureApplication();
        return application.getBgp(id);
    }

    /**
     * Returns Bridge object
     *
     * @param id ID of bridge
     * @return Bridge
     */
    public Bridge getBridge(UUID id) {
        ensureApplication();
        return application.getBridge(id);
    }

    /**
     * Returns RuleChain object
     *
     * @param id ID of chain
     * @return RuleChain
     */
    public RuleChain getChain(UUID id) {
        ensureApplication();
        return application.getRuleChain(id);
    }

    /**
     * Returns Host object
     *
     * @param id ID of host
     * @return Host
     */
    public Host getHost(UUID id) {
        ensureApplication();
        return application.getHost(id);
    }

    /**
     * Returns Port object
     *
     * @param id ID of port
     * @return Port
     */
    public Port getPort(UUID id) {
        ensureApplication();
        return application.getPort(id);
    }

    /**
     * Returns PortGroup object
     *
     * @param id ID of port group
     * @return PortGroup
     * */
    public PortGroup getPortGroup(UUID id) {
        ensureApplication();
        return application.getPortGroup(id);
    }

    /**
     * Returns Route object
     *
     * @param id ID of route
     * @return Route
     * */
    public Route getRoute(UUID id) {
        ensureApplication();
        return application.getRoute(id);
    }

    /**
     * Returns Router object
     *
     * @param id ID of router
     * @return Router
     * */
    public Router getRouter(UUID id) {
        ensureApplication();
        return application.getRouter(id);
    }

    /**
     * Returns Rule object
     *
     * @param id ID of rule
     * @return Rule
     */
    public Rule getRule(UUID id) {
        ensureApplication();
        return application.getRule(id);
    }

    /**
     * Returns TunnelZone object
     *
     * @param id ID of tunnel zone
     * @return TunnelZone
     */
    public TunnelZone getTunnelZone(UUID id) {
        ensureApplication();
        return application.getTunnelZone(id);
    }

    private void ensureApplication() {
        if (application == null) {
            DtoApplication dtoApplication = resource
                .get("",
                     DtoApplication.class,
                     VendorMediaType.APPLICATION_JSON);
            application = new Application(resource, dtoApplication);
        }
    }
}
