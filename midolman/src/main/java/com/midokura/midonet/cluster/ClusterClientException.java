/*
 * Copyright 2012 Midokura Pte. Ltd.
 */

package com.midokura.midonet.cluster;

public class ClusterClientException extends Exception {


    public ClusterClientException() {
        super();
    }

    public ClusterClientException(String message) {
        super(message);
    }

    public ClusterClientException(Throwable cause) {
        super(cause);
    }
}
