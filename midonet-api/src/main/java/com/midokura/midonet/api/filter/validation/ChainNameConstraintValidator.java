/*
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midonet.api.filter.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import com.google.inject.Inject;
import com.midokura.midonet.api.filter.Chain;
import com.midokura.midonet.api.validation.MessageProperty;
import com.midokura.midolman.state.StateAccessException;
import com.midokura.midonet.cluster.DataClient;

public class ChainNameConstraintValidator implements
        ConstraintValidator<IsUniqueChainName, Chain> {

    private final DataClient dataClient;

    @Inject
    public ChainNameConstraintValidator(DataClient dataClient) {
        this.dataClient = dataClient;
    }

    @Override
    public void initialize(IsUniqueChainName constraintAnnotation) {
    }

    @Override
    public boolean isValid(Chain value, ConstraintValidatorContext context) {

        // Guard against a DTO that cannot be validated
        String tenantId = value.getTenantId();
        if (tenantId == null || value.getName() == null) {
            throw new IllegalArgumentException("Invalid Chain passed in.");
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                MessageProperty.IS_UNIQUE_CHAIN_NAME).addNode("name")
                .addConstraintViolation();

        com.midokura.midonet.cluster.data.Chain chain = null;
        try {
            chain = dataClient.chainsGetByName(value.getTenantId(),
                    value.getName());
        } catch (StateAccessException e) {
            throw new RuntimeException(
                    "State access exception occurred in validation");
        }

        // It's valid if the duplicate named chain does not exist, or
        // exists but it's the same chain.
        return (chain == null || chain.getId().equals(value.getId()));
    }
}
