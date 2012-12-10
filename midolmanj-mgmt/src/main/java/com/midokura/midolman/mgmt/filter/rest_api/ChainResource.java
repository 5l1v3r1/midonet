/*
 * Copyright 2011 Midokura KK
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midolman.mgmt.filter.rest_api;

import com.google.inject.Inject;
import com.google.inject.servlet.RequestScoped;
import com.midokura.midolman.mgmt.ResourceUriBuilder;
import com.midokura.midolman.mgmt.VendorMediaType;
import com.midokura.midolman.mgmt.auth.AuthAction;
import com.midokura.midolman.mgmt.auth.AuthRole;
import com.midokura.midolman.mgmt.auth.Authorizer;
import com.midokura.midolman.mgmt.auth.ForbiddenHttpException;
import com.midokura.midolman.mgmt.filter.Chain;
import com.midokura.midolman.mgmt.filter.Chain.ChainGroupSequence;
import com.midokura.midolman.mgmt.filter.auth.ChainAuthorizer;
import com.midokura.midolman.mgmt.filter.rest_api.RuleResource.ChainRuleResource;
import com.midokura.midolman.mgmt.rest_api.*;
import com.midokura.midolman.state.InvalidStateOperationException;
import com.midokura.midolman.state.StateAccessException;
import com.midokura.midonet.cluster.DataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Root resource class for chains.
 */
@RequestScoped
public class ChainResource extends AbstractResource {

    private final static Logger log = LoggerFactory
            .getLogger(ChainResource.class);

    private final Authorizer authorizer;
    private final Validator validator;
    private final DataClient dataClient;
    private final ResourceFactory factory;

    @Inject
    public ChainResource(RestApiConfig config, UriInfo uriInfo,
                         SecurityContext context,
                         ChainAuthorizer authorizer, Validator validator,
                         DataClient dataClient, ResourceFactory factory) {
        super(config, uriInfo, context);
        this.authorizer = authorizer;
        this.validator = validator;
        this.dataClient = dataClient;
        this.factory = factory;
    }

    /**
     * Handler to deleting a chain.
     *
     * @param id
     *            Chain ID from the request.
     * @throws StateAccessException
     *             Data access error.
     */
    @DELETE
    @RolesAllowed({ AuthRole.ADMIN, AuthRole.TENANT_ADMIN })
    @Path("{id}")
    public void delete(@PathParam("id") UUID id)
            throws StateAccessException, InvalidStateOperationException {

        com.midokura.midonet.cluster.data.Chain chainData =
                dataClient.chainsGet(id);
        if (chainData == null) {
            return;
        }

        if (!authorizer.authorize(context, AuthAction.WRITE, id)) {
            throw new ForbiddenHttpException(
                    "Not authorized to delete this chain.");
        }

        dataClient.chainsDelete(id);
    }

    /**
     * Handler to getting a chain.
     *
     * @param id
     *            Chain ID from the request.
     * @throws StateAccessException
     *             Data access error.
     * @return A Chain object.
     */
    @GET
    @PermitAll
    @Path("{id}")
    @Produces({ VendorMediaType.APPLICATION_CHAIN_JSON,
            MediaType.APPLICATION_JSON })
    public Chain get(@PathParam("id") UUID id)
            throws StateAccessException {

        if (!authorizer.authorize(context, AuthAction.READ, id)) {
            throw new ForbiddenHttpException(
                    "Not authorized to view this chain.");
        }

        com.midokura.midonet.cluster.data.Chain chainData =
                dataClient.chainsGet(id);
        if (chainData == null) {
            throw new NotFoundHttpException(
                    "The requested resource was not found.");
        }

        // Convert to the REST API DTO
        Chain chain = new Chain(chainData);
        chain.setBaseUri(getBaseUri());

        return chain;
    }

    /**
     * Rule resource locator for chains.
     *
     * @param id
     *            Chain ID from the request.
     * @returns ChainRuleResource object to handle sub-resource requests.
     */
    @Path("/{id}" + ResourceUriBuilder.RULES)
    public ChainRuleResource getRuleResource(@PathParam("id") UUID id) {
        return factory.getChainRuleResource(id);
    }

    /**
     * Handler for creating a tenant chain.
     *
     * @param chain
     *            Chain object.
     * @throws StateAccessException
     *             Data access error.
     * @returns Response object with 201 status code set if successful.
     */
    @POST
    @RolesAllowed({ AuthRole.ADMIN, AuthRole.TENANT_ADMIN })
    @Consumes({ VendorMediaType.APPLICATION_CHAIN_JSON,
            MediaType.APPLICATION_JSON })
    public Response create(Chain chain)
            throws StateAccessException, InvalidStateOperationException {

        Set<ConstraintViolation<Chain>> violations = validator.validate(
                chain, ChainGroupSequence.class);
        if (!violations.isEmpty()) {
            throw new BadRequestHttpException(violations);
        }

        if (!Authorizer.isAdminOrOwner(context, chain.getTenantId())) {
            throw new ForbiddenHttpException(
                    "Not authorized to add chain to this tenant.");
        }

        UUID id = dataClient.chainsCreate(chain.toData());
        return Response.created(
                ResourceUriBuilder.getChain(getBaseUri(), id))
                .build();
    }

    @GET
    @Path("/name")
    @PermitAll
    @Produces({ VendorMediaType.APPLICATION_CHAIN_JSON,
            MediaType.APPLICATION_JSON })
    public Chain getByName(@QueryParam("tenant_id") String tenantId,
                           @QueryParam("name") String name)
            throws StateAccessException{
        if (tenantId == null || name == null) {
            throw new BadRequestHttpException(
                    "Currently tenant_id and name are required for search.");
        }

        com.midokura.midonet.cluster.data.Chain chainData =
                dataClient.chainsGetByName(tenantId, name);
        if (chainData == null) {
            throw new NotFoundHttpException(
                    "The requested resource was not found.");
        }

        if (!authorizer.authorize(
                context, AuthAction.READ, chainData.getId())) {
            throw new ForbiddenHttpException(
                    "Not authorized to view this chain.");
        }

        // Convert to the REST API DTO
        Chain chain = new Chain(chainData);
        chain.setBaseUri(getBaseUri());
        return chain;
    }

    /**
     * Handler to getting a collection of chains.
     *
     * @throws StateAccessException
     *             Data access error.
     * @return A list of Chain objects.
     */
    @GET
    @PermitAll
    @Produces({ VendorMediaType.APPLICATION_CHAIN_COLLECTION_JSON,
            MediaType.APPLICATION_JSON })
    public List<Chain> list(@QueryParam("tenant_id") String tenantId)
            throws StateAccessException {

        if (tenantId == null) {
            throw new BadRequestHttpException(
                    "Tenant_id is required for search.");
        }

        // Tenant ID query string is a special parameter that is used to check
        // authorization.
        if (!Authorizer.isAdminOrOwner(context, tenantId)) {
            throw new ForbiddenHttpException(
                    "Not authorized to view chains of this request.");
        }

        List<com.midokura.midonet.cluster.data.Chain> dataChains =
                dataClient.chainsFindByTenant(tenantId);
        List<Chain> chains = new ArrayList<Chain>();
        if (dataChains != null) {
            for (com.midokura.midonet.cluster.data.Chain dataChain :
                    dataChains) {
                Chain chain = new Chain(dataChain);
                chain.setBaseUri(getBaseUri());
                chains.add(chain);
            }
        }

        return chains;
    }

}
