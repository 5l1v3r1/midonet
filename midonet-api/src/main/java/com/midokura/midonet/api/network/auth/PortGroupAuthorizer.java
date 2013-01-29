/*
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midonet.api.network.auth;

import com.google.inject.Inject;
import com.midokura.midonet.api.auth.AuthAction;
import com.midokura.midonet.api.auth.Authorizer;
import com.midokura.midolman.state.StateAccessException;
import com.midokura.midonet.cluster.DataClient;
import com.midokura.midonet.cluster.data.PortGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.SecurityContext;
import java.util.UUID;

/**
 * Authorizer for port group
 */
public class PortGroupAuthorizer extends Authorizer<UUID> {

    private final static Logger log = LoggerFactory
            .getLogger(PortGroupAuthorizer.class);

    private final DataClient dataClient;

    @Inject
    public PortGroupAuthorizer(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public boolean authorize(SecurityContext context, AuthAction action,
                             UUID id) throws StateAccessException {
        log.debug("authorize entered: id=" + id + ",action=" + action);

        if (isAdmin(context)) {
            return true;
        }

        PortGroup portGroup = dataClient.portGroupsGet(id);
        if (portGroup == null) {
            log.warn("Attempted to authorize a non-existent resource: {}", id);
            return false;
        } else {
            return isOwner(context, portGroup.getProperty(
                    PortGroup.Property.tenant_id));
        }
    }
}
