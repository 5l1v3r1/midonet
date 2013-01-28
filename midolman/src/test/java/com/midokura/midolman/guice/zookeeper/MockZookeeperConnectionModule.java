/*
* Copyright 2012 Midokura Europe SARL
*/
package com.midokura.midolman.guice.zookeeper;

import javax.inject.Singleton;

import com.midokura.midolman.state.Directory;
import com.midokura.midolman.state.MockDirectory;

public class MockZookeeperConnectionModule  extends ZookeeperConnectionModule {

    Directory directory;

    public MockZookeeperConnectionModule() {
        this(null);
    }

    public MockZookeeperConnectionModule(Directory directory) {
        this.directory = directory;
    }

    @Override
    protected void bindZookeeperConnection() {
        // no binding since we are mocking
    }

    @Override
    protected void bindDirectory() {
        if (directory == null) {
            bind(Directory.class)
                .to(MockDirectory.class)
                .in(Singleton.class);
        } else {
            bind(Directory.class)
                .toInstance(directory);
        }
    }
}
