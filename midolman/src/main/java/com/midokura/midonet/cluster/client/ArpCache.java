/*
 * Copyright 2012 Midokura Europe SARL
 */
package com.midokura.midonet.cluster.client;

import com.midokura.midolman.state.ArpCacheEntry;
import com.midokura.packets.IntIPv4;
import com.midokura.packets.MAC;
import com.midokura.util.functors.Callback1;
import com.midokura.util.functors.Callback2;


/*
 * Non-blocking.
 */
public interface ArpCache {
    void get(IntIPv4 ipAddr, Callback1<ArpCacheEntry> cb, Long expirationTime);
    void add(IntIPv4 ipAddr, ArpCacheEntry entry);
    void remove(IntIPv4 ipAddr);
    void notify(Callback2<IntIPv4, MAC> cb);
    void unsubscribe(Callback2<IntIPv4, MAC> cb);
}
