/**
 * TestCassandraCache.java - Tests for CassandraCache class.
 *
 * Copyright (c) 2012 Midokura KK. All rights reserved.
 */

package com.midokura.midolman.util;

import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import com.midokura.cassandra.CassandraCache;
import com.midokura.util.Waiters;

public class TestCassandraCache extends TestCache {

    @BeforeClass
    public static void setUpCassandra() throws Exception {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra();
        Waiters.sleepBecause("Cassandra server must come up.", 2);
    }

    @Before
    public void setUp() throws Exception {
        lifetime = 1000;
        cache = new CassandraCache("localhost:9171", "TestCluster",
                                   "midolmanj", "nat", 1, (int)lifetime/1000);
    }

    @AfterClass
    public static void tearDownCassandra() throws Exception {
        EmbeddedCassandraServerHelper.cleanEmbeddedCassandra();
    }
}
