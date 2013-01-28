/*
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midonet.cluster.data.ports;

import java.util.UUID;

/**
 * Interface for logical ports
 */
public interface LogicalPort <T> {

    T setPeerId(UUID peerId);

    UUID getPeerId();
}
