/*
 * Copyright 2012 Midokura Europe SARL
 */
package org.midonet.cluster.client;

import org.midonet.midolman.state.ArpCacheEntry;
import org.midonet.packets.IPv4Addr;
import org.midonet.packets.MAC;
import org.midonet.util.functors.Callback2;

public interface ArpCache {
    /*
     * Allowing a blocking call, but implementations should ensure that they
     * serve the request off a local cache, not causing blocking IO.
     */
    ArpCacheEntry get(IPv4Addr ipAddr);
    void add(IPv4Addr ipAddr, ArpCacheEntry entry);
    void remove(IPv4Addr ipAddr);
    void notify(Callback2<IPv4Addr, MAC> cb);
    void unsubscribe(Callback2<IPv4Addr, MAC> cb);
}
