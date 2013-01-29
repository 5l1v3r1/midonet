/*
 * Copyright 2011 Midokura KK
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midonet.api.network;

import com.midokura.midonet.api.ResourceUriBuilder;
import com.midokura.midonet.api.UriResource;
import com.midokura.midonet.api.network.validation.IsUniqueBridgeName;
import com.midokura.midonet.api.network.Bridge.BridgeExtended;
import com.midokura.midonet.cluster.data.Bridge.Property;

import javax.validation.GroupSequence;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.validation.groups.Default;
import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;
import java.util.UUID;

/**
 * Class representing Virtual Bridge.
 */
@IsUniqueBridgeName(groups = BridgeExtended.class)
@XmlRootElement
public class Bridge extends UriResource {

    public static final int MIN_BRIDGE_NAME_LEN = 1;
    public static final int MAX_BRIDGE_NAME_LEN = 255;

    @NotNull(groups = BridgeUpdateGroup.class)
    private UUID id;

    @NotNull
    private String tenantId;

    @NotNull
    @Size(min = MIN_BRIDGE_NAME_LEN, max = MAX_BRIDGE_NAME_LEN)
    private String name;

    private UUID inboundFilterId;
    private UUID outboundFilterId;

    /**
     * Constructor.
     */
    public Bridge() {
    }

    /**
     * Constructor
     *
     * @param id
     *            ID of the bridge.
     * @param name
     *            Name of the bridge.
     * @param tenantId
     *            ID of the tenant that owns the bridge.
     */
    public Bridge(UUID id, String name, String tenantId) {
        super();
        this.id = id;
        this.name = name;
        this.tenantId = tenantId;
    }

    /**
     * Bridge constructor
     *
     * @param bridgeData
     *            Bridge data object
     */
    public Bridge(com.midokura.midonet.cluster.data.Bridge bridgeData) {
        this(bridgeData.getId(), bridgeData.getName(),
                bridgeData.getProperty(Property.tenant_id));
        this.inboundFilterId = bridgeData.getInboundFilter();
        this.outboundFilterId = bridgeData.getOutboundFilter();
    }

    /**
     * Get bridge ID.
     *
     * @return Bridge ID.
     */
    public UUID getId() {
        return id;
    }

    /**
     * Set bridge ID.
     *
     * @param id
     *            ID of the bridge.
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Get bridge name.
     *
     * @return Bridge name.
     */
    public String getName() {
        return name;
    }

    /**
     * Set bridge name.
     *
     * @param name
     *            Name of the bridge.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get tenant ID.
     *
     * @return Tenant ID.
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Set tenant ID.
     *
     * @param tenantId
     *            Tenant ID of the bridge.
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getInboundFilterId() {
        return inboundFilterId;
    }

    public URI getInboundFilter() {
        if (getBaseUri() != null && inboundFilterId != null) {
            return ResourceUriBuilder.getChain(getBaseUri(), inboundFilterId);
        } else {
            return null;
        }
    }

    public void setInboundFilterId(UUID inboundFilterId) {
        this.inboundFilterId = inboundFilterId;
    }

    public UUID getOutboundFilterId() {
        return outboundFilterId;
    }

    public void setOutboundFilterId(UUID outboundFilterId) {
        this.outboundFilterId = outboundFilterId;
    }

    public URI getOutboundFilter() {
        if (getBaseUri() != null && outboundFilterId != null) {
            return ResourceUriBuilder.getChain(getBaseUri(), outboundFilterId);
        } else {
            return null;
        }
    }

    /**
     * @return the ports URI
     */
    public URI getPorts() {
        if (getBaseUri() != null && id != null) {
            return ResourceUriBuilder.getBridgePorts(getBaseUri(), id);
        } else {
            return null;
        }
    }

    /**
     * @return the peer ports URI
     */
    public URI getPeerPorts() {
        if (getBaseUri() != null && id != null) {
            return ResourceUriBuilder.getBridgePeerPorts(getBaseUri(), id);
        } else {
            return null;
        }
    }

    /**
     * @return the Filtering Database URI
     */
    public URI getFilteringDb() {
        if (getBaseUri() != null && id != null) {
            return ResourceUriBuilder.getFilteringDb(getBaseUri(), id);
        } else {
            return null;
        }
    }

    /**
     * @return the DHCP server configuration URI
     */
    public URI getDhcpSubnets() {
        if (getBaseUri() != null && id != null) {
            return ResourceUriBuilder.getBridgeDhcps(getBaseUri(), id);
        } else {
            return null;
        }
    }

    /**
     * @return the self URI
     */
    @Override
    public URI getUri() {
        if (getBaseUri() != null && id != null) {
            return ResourceUriBuilder.getBridge(getBaseUri(), id);
        } else {
            return null;
        }
    }

    /**
     * Convert this object to bridge data object
     *
     * @return Bridge data object
     */
    public com.midokura.midonet.cluster.data.Bridge toData() {

        return new com.midokura.midonet.cluster.data.Bridge()
                .setId(this.id)
                .setName(this.name)
                .setInboundFilter(this.inboundFilterId)
                .setOutboundFilter(this.outboundFilterId)
                .setProperty(Property.tenant_id, this.tenantId);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "id=" + id + ", name=" + name + ", tenantId=" + tenantId;
    }

    /**
     * Interface used for a Validation group. This group gets triggered after
     * the default validations.
     */
    public interface BridgeExtended {
    }

    /**
     * Interface used for validating a bridge on updates.
     */
    public interface BridgeUpdateGroup {
    }

    /**
     * Interface used for validating a bridge on creates.
     */
    public interface BridgeCreateGroup {
    }

    /**
     * Interface that defines the ordering of validation groups for bridge
     * create.
     */
    @GroupSequence({ Default.class, BridgeCreateGroup.class,
            BridgeExtended.class })
    public interface BridgeCreateGroupSequence {
    }

    /**
     * Interface that defines the ordering of validation groups for bridge
     * update.
     */
    @GroupSequence({ Default.class, BridgeUpdateGroup.class,
            BridgeExtended.class })
    public interface BridgeUpdateGroupSequence {
    }
}
