/*
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midonet.api.bgp.auth;

import com.google.inject.Inject;
import com.midokura.midonet.api.auth.AuthAction;
import com.midokura.midonet.api.auth.Authorizer;
import com.midokura.midolman.state.StateAccessException;
import com.midokura.midonet.cluster.DataClient;
import com.midokura.midonet.cluster.data.AdRoute;
import com.midokura.midonet.cluster.data.BGP;
import com.midokura.midonet.cluster.data.Port;
import com.midokura.midonet.cluster.data.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.SecurityContext;
import java.util.UUID;

/**
 * Authorizer for AdRoute
 */
public class AdRouteAuthorizer extends Authorizer<UUID> {

    private final static Logger log = LoggerFactory
            .getLogger(AdRouteAuthorizer.class);

    private final DataClient dataClient;

    @Inject
    public AdRouteAuthorizer(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public boolean authorize(SecurityContext context, AuthAction action,
                             UUID id) throws StateAccessException {
        log.debug("authorize entered: id=" + id + ", action=" + action);

        if (isAdmin(context)) {
            return true;
        }

        AdRoute adRoute = dataClient.adRoutesGet(id);
        if (adRoute == null) {
            log.warn("Attempted to authorize a non-existent resource: {}", id);
            return false;
        }

        BGP bgp = dataClient.bgpGet(adRoute.getBgpId());
        Port port = dataClient.portsGet(bgp.getPortId());

        // Must be a router port
        Router router = dataClient.routersGet(port.getDeviceId());

        String tenantId = router.getProperty(Router.Property.tenant_id);
        if (tenantId == null) {
            log.warn("Attempted to authorize ad route {} of a router {} with " +
                    "no tenant data", id, router.getId());
            return false;
        }

        return isOwner(context, tenantId);
    }
}
