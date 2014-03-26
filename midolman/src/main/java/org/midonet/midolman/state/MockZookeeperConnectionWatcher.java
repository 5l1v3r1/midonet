/*
 * Copyright 2012 Midokura Pte. Ltd.
 */

package org.midonet.midolman.state;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.midonet.midolman.config.MidolmanConfig;
import org.midonet.midolman.guice.zookeeper.ZKConnectionProvider;
import org.midonet.util.eventloop.Reactor;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MockZookeeperConnectionWatcher implements ZkConnectionAwareWatcher {

    @Override
    public ZkConnection getZkConnection() {
        return null;
    }

    @Override
    public void setZkConnection(ZkConnection conn) { }

    @Override
    public synchronized void process(WatchedEvent event) { }

    @Override
    public void scheduleOnReconnect(Runnable runnable) { }

    @Override
    public void scheduleOnDisconnect(Runnable runnable) { }

    @Override
    public void handleError(String operationDesc, Runnable retry, KeeperException e) {}

    @Override
    public void handleError(String operationDesc, Runnable retry, StateAccessException e) {}

    @Override
    public void handleTimeout(Runnable runnable) {}

    @Override
    public void handleDisconnect(Runnable runnable) {}
}
