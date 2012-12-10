/*
 * Copyright 2011 Midokura KK
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midolman.mgmt.bgp.rest_api;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.servlet.RequestScoped;
import com.midokura.midolman.mgmt.ResourceUriBuilder;
import com.midokura.midolman.mgmt.VendorMediaType;
import com.midokura.midolman.mgmt.auth.AuthAction;
import com.midokura.midolman.mgmt.auth.AuthRole;
import com.midokura.midolman.mgmt.auth.Authorizer;
import com.midokura.midolman.mgmt.auth.ForbiddenHttpException;
import com.midokura.midolman.mgmt.bgp.AdRoute;
import com.midokura.midolman.mgmt.bgp.auth.AdRouteAuthorizer;
import com.midokura.midolman.mgmt.bgp.auth.BgpAuthorizer;
import com.midokura.midolman.mgmt.rest_api.AbstractResource;
import com.midokura.midolman.mgmt.rest_api.NotFoundHttpException;
import com.midokura.midolman.mgmt.rest_api.RestApiConfig;
import com.midokura.midolman.state.InvalidStateOperationException;
import com.midokura.midolman.state.StateAccessException;
import com.midokura.midonet.cluster.DataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.PermitAll;
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
 * Root resource class for advertising routes.
 */
@RequestScoped
public class AdRouteResource extends AbstractResource {

    private final static Logger log = LoggerFactory
            .getLogger(AdRouteResource.class);

    private final Authorizer authorizer;
    private final DataClient dataClient;

    @Inject
    public AdRouteResource(RestApiConfig config, UriInfo uriInfo,
                           SecurityContext context,
                           AdRouteAuthorizer authorizer,
                           DataClient dataClient) {
        super(config, uriInfo, context);
        this.authorizer = authorizer;
        this.dataClient = dataClient;
    }

    /**
     * Handler to deleting an advertised route.
     *
     * @param id
     *            AdRoute ID from the request.
    * @throws StateAccessException
     *             Data access error.
     */
    @DELETE
    @RolesAllowed({AuthRole.ADMIN, AuthRole.TENANT_ADMIN})
    @Path("{id}")
    public void delete(@PathParam("id") UUID id)
            throws StateAccessException, InvalidStateOperationException {

        com.midokura.midonet.cluster.data.AdRoute adRouteData =
                dataClient.adRoutesGet(id);
        if (adRouteData == null) {
            return;
        }

        if (!authorizer.authorize(context, AuthAction.WRITE, id)) {
            throw new ForbiddenHttpException(
                    "Not authorized to delete this ad route.");
        }

        dataClient.adRoutesDelete(id);
    }

    /**
     * Handler to getting BGP advertised route.
     *
     * @param id
     *            Ad route ID from the request.
     * @throws StateAccessException
     *             Data access error.
     * @return An AdRoute object.
     */
    @GET
    @RolesAllowed({AuthRole.ADMIN, AuthRole.TENANT_ADMIN})
    @Path("{id}")
    @Produces({ VendorMediaType.APPLICATION_AD_ROUTE_JSON,
            MediaType.APPLICATION_JSON })
    public AdRoute get(@PathParam("id") UUID id) throws StateAccessException {

        if (!authorizer.authorize(context, AuthAction.READ, id)) {
            throw new ForbiddenHttpException(
                    "Not authorized to view this ad route.");
        }

        com.midokura.midonet.cluster.data.AdRoute adRouteData =
                dataClient.adRoutesGet(id);
        if (adRouteData == null) {
            throw new NotFoundHttpException(
                    "The requested resource was not found.");
        }

        // Convert to the REST API DTO
        AdRoute adRoute = new AdRoute(adRouteData);
        adRoute.setBaseUri(getBaseUri());

        return adRoute;
    }

    /**
     * Sub-resource class for bgp's advertising route.
     */
    @RequestScoped
    public static class BgpAdRouteResource extends AbstractResource {

        private final UUID bgpId;
        private final Authorizer authorizer;
        private final DataClient dataClient;

        @Inject
        public BgpAdRouteResource(RestApiConfig config, UriInfo uriInfo,
                                  SecurityContext context,
                                  BgpAuthorizer authorizer,
                                  DataClient dataClient,
                                  @Assisted UUID bgpId) {
            super(config, uriInfo, context);
            this.bgpId = bgpId;
            this.authorizer = authorizer;
            this.dataClient = dataClient;
        }

        /**
         * Handler for creating BGP advertised route.
         *
         * @param adRoute
         *            AdRoute object.
         * @throws StateAccessException
         *             Data access error.
         * @returns Response object with 201 status code set if successful.
         */
        @POST
        @RolesAllowed({AuthRole.ADMIN, AuthRole.TENANT_ADMIN})
        @Consumes({ VendorMediaType.APPLICATION_AD_ROUTE_JSON,
                MediaType.APPLICATION_JSON })
        public Response create(AdRoute adRoute)
                throws StateAccessException, InvalidStateOperationException {

            adRoute.setBgpId(bgpId);

            if (!authorizer.authorize(context, AuthAction.WRITE, bgpId)) {
                throw new ForbiddenHttpException(
                        "Not authorized to add ad route to this BGP.");
            }

            UUID id = dataClient.adRoutesCreate(adRoute.toData());
            return Response.created(
                    ResourceUriBuilder.getAdRoute(getBaseUri(), id))
                    .build();
        }

        /**
         * Handler to getting a list of BGP advertised routes.
         *
         * @throws StateAccessException
         *             Data access error.
         * @return A list of AdRoute objects.
         */
        @GET
        @PermitAll
        @Produces({ VendorMediaType.APPLICATION_AD_ROUTE_COLLECTION_JSON,
                MediaType.APPLICATION_JSON })
        public List<AdRoute> list() throws StateAccessException {

            if (!authorizer.authorize(context, AuthAction.READ, bgpId)) {
                throw new ForbiddenHttpException(
                        "Not authorized to view these advertised routes.");
            }

            List<com.midokura.midonet.cluster.data.AdRoute> adRouteDataList =
                    dataClient.adRoutesFindByBgp(bgpId);
            List<AdRoute> adRoutes = new ArrayList<AdRoute>();
            if (adRouteDataList != null) {
                for (com.midokura.midonet.cluster.data.AdRoute adRouteData :
                        adRouteDataList) {
                    AdRoute adRoute = new AdRoute(adRouteData);
                    adRoute.setBaseUri(getBaseUri());
                    adRoutes.add(adRoute);
                }

            }
            return adRoutes;
        }
    }
}
