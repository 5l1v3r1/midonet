// Copyright 2012 Midokura Inc.

package com.midokura.midolman.rules;

import java.util.Set;
import java.util.UUID;

import com.midokura.util.functors.Callback0;

public interface ChainPacketContext {
    UUID getInPortId();
    UUID getOutPortId();
    Set<UUID> getPortGroups();
    void addTraversedElementID(UUID id);
    void addFlowRemovedCallback(Callback0 cb);
    void addFlowTag(Object tag);
    boolean isConnTracked();
    boolean isForwardFlow();
    Integer getFlowCookie();
    Integer getParentCookie();
}
