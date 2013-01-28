/**
 * Copyright 2012 Midokura Europe SARL
 * User: Rossella Sblendido <rossella@midokura.com>
 * Date: 2/6/12
 */

package com.midokura.midolman.host;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.midokura.config.ConfigProvider;
import com.midokura.midolman.host.config.HostConfig;
import com.midokura.midolman.host.state.HostZkManager;
import com.midokura.midolman.state.Directory;
import com.midokura.midolman.state.MockDirectory;

public class HostIdGeneratorTest {

    String localPropertiesFile = "host_uuid.properties";
    String uuidPropertyName = "host_uuid";
    String hostId1 = "e3f9adc0-5175-11e1-b86c-0800200c9a66";
    String hostId2 = "e3f9adc0-5175-11e1-b86c-0800200c9a67";
    HostZkManager zkManager;
    File propFile;
    String basePath = "/midolman";
    HostConfig config;
    HostConfig configFake;

    @After
    public void tearDown() throws Exception {
        if (propFile.exists())
            propFile.delete();
    }

    @Before
    public void setUp() throws Exception {
        Directory dir = new MockDirectory();
        zkManager = new HostZkManager(dir, basePath);
        zkManager.addPersistent(basePath, null);
        zkManager.addPersistent(basePath + "/hosts", null);

        final HierarchicalConfiguration configuration =
                new HierarchicalConfiguration();

        // this configuration will use the default hostid properties location.
        configuration.addNodes(HostConfig.GROUP_NAME,
                Arrays.asList(new HierarchicalConfiguration.Node
                        (uuidPropertyName, hostId1)
                ));

        config =
                ConfigProvider
                        .providerForIniConfig(configuration)
                        .getConfig(HostConfig.class);


        final HierarchicalConfiguration fakeConfiguration =
                new HierarchicalConfiguration();
        fakeConfiguration.addNodes(HostConfig.GROUP_NAME,
                Arrays.asList(new HierarchicalConfiguration.Node
                        ("properties_file", localPropertiesFile)));

        configFake =
                ConfigProvider
                        .providerForIniConfig(
                                fakeConfiguration)
                        .getConfig(HostConfig.class);

        Properties properties = new Properties();
        properties.setProperty(uuidPropertyName, hostId2);
        propFile = new File(localPropertiesFile);
        properties.store(new FileOutputStream(localPropertiesFile), null);
    }

    @Test
    public void getIdFromConfFile() throws Exception {
        UUID id = HostIdGenerator.getHostId(config, zkManager);
        Assert.assertEquals(id.toString(), hostId1);
    }

    @Test
    public void getIdFromPropertyFile() throws Exception {
        // Write the host ID in ZK so the call makeAlive() won't fail.
        // When we read from the properties file we expect that the host
        // has already been created
        zkManager.addPersistent(basePath + "/hosts/" + hostId2, null);
        // pass a conf file with no ID specified so we will try to read it
        // from the properties file
        UUID id = HostIdGenerator.getHostId(configFake, zkManager);
        Assert.assertTrue(id.toString().equals(hostId2));
    }

    @Test
    public void generateRandomId() throws Exception {
        // delete properties file
        boolean res = propFile.delete();
        Assert.assertTrue(res);
        UUID id = HostIdGenerator.getHostId(configFake, zkManager);
        // check that the id has been written in the property file
        boolean exists = propFile.exists();
        Assert.assertTrue(exists);
        Properties properties = new Properties();
        properties.load(new FileInputStream(localPropertiesFile));
        UUID idFromProperty = UUID.fromString(
            properties.getProperty(uuidPropertyName));
        Assert.assertTrue(id.equals(idFromProperty));
    }

    @Test(expected = HostIdGenerator.HostIdAlreadyInUseException.class)
    public void hostAlreadyAlive() throws Exception {
        UUID id = HostIdGenerator.getHostId(config, zkManager);
        // the first time works
        Assert.assertTrue(id.toString().equals(hostId1));
        // make it alive
        zkManager.makeAlive(id);
        // second time you get an exception because the host is already alive
        HostIdGenerator.getHostId(config, zkManager);
    }

    @Test(expected = HostIdGenerator.PropertiesFileNotWritableException.class)
    public void propertyFileCorrupted() throws Exception {
        // delete properties file so no ID will be loaded from there
        boolean res = propFile.delete();
        propFile.createNewFile();
        propFile.setReadOnly();
        UUID id = HostIdGenerator.getHostId(configFake, zkManager);
    }
}
