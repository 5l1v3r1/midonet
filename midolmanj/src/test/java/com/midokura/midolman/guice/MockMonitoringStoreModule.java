/*
* Copyright 2012 Midokura Europe SARL
*/
package com.midokura.midolman.guice;

import com.google.inject.PrivateModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.midokura.midolman.monitoring.store.MockStoreProvider;
import com.midokura.midolman.monitoring.store.Store;

public class MockMonitoringStoreModule extends PrivateModule {

    @Override
    protected void configure() {

        // expose the mock cassandra stuff instead of the real one for the tests.
        bind(Store.class).toProvider(MockStoreProvider.class)
                .asEagerSingleton();
        expose(Store.class);
    }
}
