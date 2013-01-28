/*
 * Copyright 2012 Midokura Europe SARL
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midonet.api.host.rest_api;

import com.google.inject.Inject;
import com.google.inject.servlet.RequestScoped;
import com.midokura.midonet.api.ResourceUriBuilder;
import com.midokura.midonet.api.VendorMediaType;
import com.midokura.midonet.api.auth.AuthRole;
import com.midokura.midonet.api.auth.ForbiddenHttpException;
import com.midokura.midonet.api.host.Host;
import com.midokura.midonet.api.rest_api.AbstractResource;
import com.midokura.midonet.api.rest_api.NotFoundHttpException;
import com.midokura.midonet.api.rest_api.ResourceFactory;
import com.midokura.midonet.api.rest_api.RestApiConfig;
import com.midokura.midolman.state.InvalidStateOperationException;
import com.midokura.midolman.state.StateAccessException;
import com.midokura.midonet.cluster.DataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Mihai Claudiu Toader <mtoader@midokura.com> Date: 1/30/12
 */
@RequestScoped
public class HostResource extends AbstractResource {

    private final static Logger log = LoggerFactory
            .getLogger(HostResource.class);

    private final DataClient dataClient;
    private final ResourceFactory factory;

    @Inject
    public HostResource(RestApiConfig config, UriInfo uriInfo,
                        SecurityContext context,
                        DataClient dataClient,
                        ResourceFactory factory) {
        super(config, uriInfo, context);
        this.dataClient = dataClient;
        this.factory = factory;
    }

    @GET
    @RolesAllowed({AuthRole.ADMIN})
    @Produces({VendorMediaType.APPLICATION_HOST_COLLECTION_JSON,
                  MediaType.APPLICATION_JSON})
    public List<Host> list()
        throws ForbiddenHttpException, StateAccessException {

        List<com.midokura.midonet.cluster.data.host.Host> hostConfigs =
                dataClient.hostsGetAll();
        List<Host> hosts = new ArrayList<Host>();
        for (com.midokura.midonet.cluster.data.host.Host hostConfig :
                hostConfigs) {
            Host host = new Host(hostConfig);
            host.setBaseUri(getBaseUri());
            hosts.add(host);
        }
        return hosts;
    }

    /**
     * Handler to getting a host information.
     *
     * @param id         Host ID from the request.
     * @return A Host object.
     * @throws StateAccessException Data access error.
     * @throws NotFoundHttpException Non existent UUID
     */
    @GET
    @RolesAllowed({AuthRole.ADMIN})
    @Path("{id}")
    @Produces({VendorMediaType.APPLICATION_HOST_JSON,
                  MediaType.APPLICATION_JSON})
    public Host get(@PathParam("id") UUID id)
        throws NotFoundHttpException, StateAccessException {

        com.midokura.midonet.cluster.data.host.Host hostConfig =
                dataClient.hostsGet(id);
        Host host = null;
        if (hostConfig != null) {
            host = new Host(hostConfig);
            host.setBaseUri(getBaseUri());
        } else {
            throw new NotFoundHttpException();
        }

        return host;
    }

    /**
     * Handler to deleting a host.
     *
     * @param id         Host ID from the request.
     * @return Response object with 204 status code set if successful and 403 is
     *         the deletion could not be executed.
     * @throws StateAccessException Data access error.
     */
    @DELETE
    @RolesAllowed({AuthRole.ADMIN})
    @Path("{id}")
    public Response delete(@PathParam("id") UUID id)
            throws StateAccessException, InvalidStateOperationException {

        try {
            dataClient.hostsDelete(id);
        } catch (StateAccessException e) {
            throw new ForbiddenHttpException();
        }
        return Response.noContent().build();

    }

    /**
     * Interface resource locator for hosts.
     *
     * @param hostId Host ID from the request.
     * @return InterfaceResource object to handle sub-resource requests.
     */
    @Path("/{id}" + ResourceUriBuilder.INTERFACES)
    public InterfaceResource getInterfaceResource(
            @PathParam("id") UUID hostId) {

        return factory.getInterfaceResource(hostId);
    }

    /**
     * Interface resource locator for hosts.
     *
     * @param hostId Host ID from the request.
     * @return InterfaceResource object to handle sub-resource requests.
     */
    @Path("/{id}" + ResourceUriBuilder.COMMANDS)
    public HostCommandResource getHostCommandsResource(
        @PathParam("id") UUID hostId) {
        return factory.getHostCommandsResource(hostId);
    }

    @Path("/{id}" + ResourceUriBuilder.PORTS)
    public HostInterfacePortResource getHostInterfacePortResource(
            @PathParam("id") UUID hostId) {
        return factory.getHostInterfacePortResource(hostId);
    }

}
