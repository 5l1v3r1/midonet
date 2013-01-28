/**
 * MockServiceFlowController.java - A mock class for port service flows.
 *
 * Copyright (c) 2012 Midokura KK. All rights reserved.
 */

package com.midokura.midolman.layer3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.midokura.midolman.util.Net;

public class MockServiceFlowController implements ServiceFlowController {

    private static final Logger log = LoggerFactory
            .getLogger(MockServiceFlowController.class);

    @Override
    public void setServiceFlows(short localPortNum, short remotePortNum,
                                int localAddr, int remoteAddr,
                                short localTport, short remoteTport) {
        log.debug("setServiceFlows: localPortNum {} remotePortNum {}" +
                  "localAddr {} remoteAddr {} localTport {} remoteTport {}",
                  new Object[] {localPortNum, remotePortNum,
                                Net.convertIntAddressToString(localAddr),
                                Net.convertIntAddressToString(remoteAddr),
                                localTport, remoteTport});
    }
}
