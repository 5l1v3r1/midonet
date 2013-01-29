/*
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midonet.api.network.validation;

import com.google.inject.Inject;
import com.midokura.midonet.api.network.PortGroup;
import com.midokura.midonet.api.validation.MessageProperty;
import com.midokura.midolman.state.StateAccessException;
import com.midokura.midonet.cluster.DataClient;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class PortGroupNameConstraintValidator implements
        ConstraintValidator<IsUniquePortGroupName, PortGroup> {

    private final DataClient dataClient;

    @Inject
    public PortGroupNameConstraintValidator(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public void initialize(IsUniquePortGroupName constraintAnnotation) {
    }

    @Override
    public boolean isValid(PortGroup value,
                           ConstraintValidatorContext context) {

        // Guard against a DTO that cannot be validated
        String tenantId = value.getTenantId();
        if (tenantId == null || value.getName() == null) {
            throw new IllegalArgumentException("Invalid port group passed in.");
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                MessageProperty.IS_UNIQUE_PORT_GROUP_NAME).addNode("name")
                .addConstraintViolation();

        com.midokura.midonet.cluster.data.PortGroup portGroup = null;
        try {
            portGroup = dataClient.portGroupsGetByName(tenantId,
                    value.getName());
        } catch (StateAccessException e) {
            throw new RuntimeException(
                    "State access exception occurred in validation");
        }

        // It's valid if the duplicate named portGroup does not exist, or
        // exists but it's the same portGroup.
        return (portGroup == null || portGroup.getId().equals(value.getId()));
    }
}
