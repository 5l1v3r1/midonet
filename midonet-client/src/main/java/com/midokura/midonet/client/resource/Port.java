/*
 * Copyright (c) 2012. Midokura Japan K.K.
 */

package com.midokura.midonet.client.resource;

import com.midokura.midonet.client.VendorMediaType;
import com.midokura.midonet.client.WebResource;
import com.midokura.midonet.client.dto.DtoPort;
import com.midokura.midonet.client.dto.DtoPortGroup;

import java.net.URI;

/**
 * Abstract port resource class for bridge port and router port
 *
 * @param <T> type of the resource
 * @param <U> type of the dto for the resource
 */
public abstract class Port<T extends Port<T, U>, U extends DtoPort>
    extends ResourceBase<T, U> {

    public Port(WebResource resource, URI uriForCreation,
                U principalDto, String mediaType) {
        super(resource, uriForCreation, principalDto, mediaType);
    }

    public ResourceCollection<PortGroup> getPortGroups() {
        return getChildResources(
                principalDto.getPortGroups(),
                null,
                VendorMediaType.APPLICATION_PORTGROUP_COLLECTION_JSON,
                PortGroup.class, DtoPortGroup.class);
    }

}

