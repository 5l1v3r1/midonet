/*
 * Copyright 2012 Midokura Pte. Ltd.
 */

package com.midokura.midolman.vrn;

import java.util.UUID;

import com.midokura.packets.Ethernet;
import com.midokura.midolman.state.StateAccessException;


public interface VRNControllerIface {

    void continueProcessing(final MockPacketContext fwdInfo);

    void addGeneratedPacket(Ethernet pkt, UUID originPort);

    void subscribePortSet(UUID portSetID) throws StateAccessException;
    void unsubscribePortSet(UUID portSetID) throws StateAccessException;
    void addLocalPortToSet(UUID portSetID, UUID portID);
    void removeLocalPortFromSet(UUID portSetID, UUID portID);

    void invalidateFlowsByElement(UUID id);
}
