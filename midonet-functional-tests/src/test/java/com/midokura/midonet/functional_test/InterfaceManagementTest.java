/*
 * Copyright 2012 Midokura Europe SARL
 */
package com.midokura.midonet.functional_test;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.sun.jersey.test.framework.WebAppDescriptor;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.midokura.midolman.util.Sudo;
import com.midokura.midonet.client.dto.DtoHost;
import com.midokura.midonet.client.dto.DtoInterface;
import com.midokura.midonet.client.resource.Host;
import com.midokura.midonet.client.resource.ResourceCollection;
import com.midokura.midonet.functional_test.utils.TapWrapper;
import com.midokura.midonet.functional_test.utils.ZKLauncher;
import com.midokura.tools.timed.Timed;
import com.midokura.util.lock.LockHelper;


import static com.midokura.midonet.functional_test.FunctionalTestsHelper.*;
import static com.midokura.midonet.functional_test.utils.MidolmanLauncher.ConfigType.With_Node_Agent;
import static com.midokura.midonet.functional_test.utils.ZKLauncher.ConfigType.Default;
import static com.midokura.util.Waiters.waitFor;
import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Test Suite that will exercise the interface management subsystem.
 */
@Ignore
public class InterfaceManagementTest extends TestBase {

    private static final Logger log = LoggerFactory
        .getLogger(InterfaceManagementTest.class);

    @Override
    public void setup() {}

    @Override
    public void teardown() {}

    @Test
    public void testNewHostAppearsWhenTheAgentIsExecuted() throws Exception {

        ResourceCollection<Host> hosts = apiClient.getHosts();
        assertThat("We didn't start with no hosts registered",
                   hosts, allOf(notNullValue(), hasSize(1)));
    }

    @Test
    public void testHostIsMarkedAsDownWhenTheAgentDies() throws Exception {
        /*DtoHost[] hosts = api.getHosts();
        assertThat("No hosts should be visible now",
                   hosts, allOf(notNullValue(), arrayWithSize(0)));

        MidolmanLauncher launcher =
            MidolmanLauncher.start(With_Node_Agent,
                                   "InterfaceManagementTest.testHostIsMarkedAsDownWhenTheAgentDies");

        try {
            DtoHost[] newHosts =
                waitFor("a new host appeared",
                        new Timed.Execution<DtoHost[]>() {
                            @Override
                            protected void _runOnce() throws Exception {
                                setResult(api.getHosts());
                                setCompleted(getResult().length == 1);
                            }
                        });

            assertThat("A new host is listed in the list of hosts",
                       newHosts, allOf(notNullValue(), arrayWithSize(1)));

            final DtoHost hostInfo = newHosts[0];

            assertThat("The host is marked as alive",
                       hostInfo.isAlive(), equalTo(true));

            launcher.stop();

            DtoHost newHostInfo =
                waitFor("host status change",
                        new Timed.Execution<DtoHost>() {
                            @Override
                            protected void _runOnce() throws Exception {
                                setResult(api.getHost(hostInfo.getUri()));
                                setCompleted(
                                    getResult().isAlive() != hostInfo.isAlive());
                            }
                        });

            assertThat("The host was marked as down after midolman exited",
                       newHostInfo.isAlive(),
                       Matchers.equalTo(false));

        } finally {
            launcher.stop();
        }*/
    }

    @Test
    public void testHostIsMarkedAsAliveAfterAgentRestarts() throws Exception {
        /*DtoHost[] hosts = api.getHosts();
        assertThat("No hosts should be visible now",
                   hosts, allOf(notNullValue(), arrayWithSize(0)));

        MidolmanLauncher launcher =
            MidolmanLauncher.start(With_Node_Agent,
                                   "InterfaceManagementTest.testHostIsMarkedAsAliveAfterAgentRestarts");

        try {
            hosts = waitFor("a new host should appear",
                            new Timed.Execution<DtoHost[]>() {
                                @Override
                                protected void _runOnce() throws Exception {
                                    setResult(api.getHosts());
                                    setCompleted(getResult().length == 1);
                                }
                            });

            assertThat("A new host is listed in the list of hosts",
                       hosts, allOf(notNullValue(), arrayWithSize(1)));

            final DtoHost hostInfo = hosts[0];

            assertThat("The host is marked as alive",
                       hostInfo.isAlive(), equalTo(true));

            launcher.stop();

            DtoHost newHostInfo =
                waitFor("host status change",
                        new Timed.Execution<DtoHost>() {
                            @Override
                            protected void _runOnce() throws Exception {
                                setResult(api.getHost(hostInfo.getUri()));
                                setCompleted(!getResult().isAlive());
                            }
                        });

            assertThat("The host was marked as down after midolman exited",
                       newHostInfo.isAlive(), equalTo(false));

            // start the agent again
            launcher = MidolmanLauncher.start(With_Node_Agent,
                                              "InterfaceManagementTest.testHostIsMarkedAsAliveAfterAgentRestarts-restarted");

            newHostInfo =
                waitFor("host status change",
                        new Timed.Execution<DtoHost>() {
                            @Override
                            protected void _runOnce() throws Exception {
                                setResult(api.getHost(hostInfo.getUri()));
                                setCompleted(getResult().isAlive());
                            }
                        });

            assertThat("The host was marked as alive after midolman restarts",
                       newHostInfo.isAlive(), equalTo(true));

        } finally {
            launcher.stop();
        }*/
    }

    /*
    @Test
    public void testNewInterfaceBecomesVisible() throws Exception {

        final String tapInterfaceName = "newTapInterface";

        MidolmanLauncher launcher =
            MidolmanLauncher.start(With_Node_Agent,
                                   "InterfaceManagementTest.testNewInterfaceBecomesVisible");

        TapWrapper tapWrapper = null;

        try {
            DtoHost[] hosts =
                waitFor("a new host should appear",
                        new Timed.Execution<DtoHost[]>() {
                            @Override
                            protected void _runOnce() throws Exception {
                                setResult(api.getHosts());
                                setCompleted(getResult().length == 1);
                            }
                        });
            assertThat("A new host is listed in the list of hosts",
                       hosts, allOf(notNullValue(), arrayWithSize(1)));

            final DtoHost host = hosts[0];
            assertThat("The new hosts is marked as alive!",
                       host.isAlive(), equalTo(true));

            DtoInterface[] interfaces = api.getHostInterfaces(host);
            assertThat("The interfaces array is properly formatted",
                       interfaces,
                       allOf(
                           notNullValue(),
                           arrayWithSize(greaterThanOrEqualTo(0))));

            final Matcher<DtoInterface[]> hasNewTapMatcher =
                hasItemInArray(hasProperty("name", equalTo(tapInterfaceName)));

            assertThat(
                "The interface we want to create does not already exists",
                interfaces,
                not(hasNewTapMatcher));

            tapWrapper = new TapWrapper(tapInterfaceName);

            interfaces =
                waitFor("the new interface to become visible on the host",
                        new Timed.Execution<DtoInterface[]>() {
                            @Override
                            protected void _runOnce() throws Exception {
                                setResult(api.getHostInterfaces(host));
                                setCompleted(
                                    hasNewTapMatcher.matches(getResult()));
                            }
                        });

            assertThat("the new interface appeared properly on the host",
                       interfaces, hasNewTapMatcher);
        } finally {
            launcher.stop();
            removeTapWrapper(tapWrapper);
        }
    }

    @Test
    public void testCreateInterfaceOnHost() throws Exception {
        final String tapInterfaceName = "newTapInt2";

        assertThat("We were expecting no hosts to be registered",
                   api.getHosts(), arrayWithSize(0));

        MidolmanLauncher launcher =
            MidolmanLauncher.start(With_Node_Agent,
                                   "InterfaceManagementTest.testCreateInterfaceOnHost");

        TapWrapper tapWrapper = null;

        try {
            DtoHost[] hosts =
                waitFor("a host should register",
                        new Timed.Execution<DtoHost[]>() {
                            @Override
                            protected void _runOnce() throws Exception {
                                setResult(api.getHosts());
                                setCompleted(getResult().length == 1);
                            }
                        });

            final DtoHost host = hosts[0];
            assertThat("The new host should be alive!", host.isAlive());

            DtoInterface[] interfaces = api.getHostInterfaces(host);
            assertThat("We should have seen some interfaces",
                       interfaces,
                       allOf(
                           notNullValue(),
                           arrayWithSize(greaterThanOrEqualTo(0))));

            final Matcher<DtoInterface[]> hasNewTapMatcher =
                hasItemInArray(hasProperty("name", equalTo(tapInterfaceName)));

            assertThat(
                "The interface we want to create does not already exists",
                interfaces,
                not(hasNewTapMatcher));

            DtoInterface dtoInterface = new DtoInterface();
            dtoInterface.setName(tapInterfaceName);
            dtoInterface.setType(DtoInterface.Type.Virtual);
            api.addInterface(host, dtoInterface);

            interfaces =
                waitFor("the new interface to become visible on the host",
                        new Timed.Execution<DtoInterface[]>() {
                            @Override
                            protected void _runOnce() throws Exception {
                                setResult(api.getHostInterfaces(host));
                                setCompleted(
                                    hasNewTapMatcher.matches(getResult()));
                            }
                        });

            assertThat("the new interface appeared properly on the host",
                       interfaces, hasNewTapMatcher);

            tapWrapper = new TapWrapper(tapInterfaceName, false);
        } finally {
            removeTapWrapper(tapWrapper);
            launcher.stop();
        }
    }

    @Test
    public void testUpdateInterfaceMacForHost() throws Exception {
        final String tapInterfaceName = "newTapInt3";

        assertThat("We were expecting no hosts to be registered",
                   api.getHosts(), arrayWithSize(0));

        MidolmanLauncher launcher =
            MidolmanLauncher.start(With_Node_Agent,
                                   "InterfaceManagementTest.testUpdateInterfaceMacForHost");

        TapWrapper tapWrapper = new TapWrapper(tapInterfaceName);

        try {
            final DtoHost host = waitForHostRegistration();

            final Matcher<DtoInterface[]> tapMatcher =
                hasItemInArray(hasProperty("name", equalTo(tapInterfaceName)));

            DtoInterface[] interfaces =
                waitFor("the interface to be exposed via API",
                        new Timed.Execution<DtoInterface[]>() {
                            @Override
                            protected void _runOnce() throws Exception {
                                setResult(api.getHostInterfaces(host));
                                setCompleted(tapMatcher.matches(getResult()));
                            }
                        });

            DtoInterface dtoInterface = null;
            for (DtoInterface anInterface : interfaces) {
                if (anInterface.getName().equals(tapInterfaceName)) {
                    dtoInterface = anInterface;
                    break;
                }
            }

            String targetMacAddress = "12:11:11:11:11:17";

            assertThat(
                "the interface dto is ok (non null and with a different mac)",
                dtoInterface,
                allOf(notNullValue(),
                      hasProperty("mac", not(equalTo(targetMacAddress)))));

            //noinspection ConstantConditions
            dtoInterface.setMac(targetMacAddress);

            final Matcher<DtoInterface[]> newMacMatcher =
                hasItemInArray(
                    allOf(
                        hasProperty("mac", equalTo(targetMacAddress)),
                        hasProperty("name", equalTo(dtoInterface.getName()))
                    )
                );

            // make the update
            api.updateInterface(dtoInterface);

            // wait to see the change
            waitFor("the interface MAC address to change",
                    new Timed.Execution<java.lang.Object>() {
                        @Override
                        protected void _runOnce() throws Exception {
                            setResult(api.getHostInterfaces(host));
                            setCompleted(newMacMatcher.matches(getResult()));
                        }
                    });
        } finally {
            launcher.stop();
            removeTapWrapper(tapWrapper);
        }
    }

    @Test
    public void testUpdateInterfaceAddressForHost() throws Exception {
        final String tapInterfaceName = "newTapInt4";

        assertThat("We were expecting no hosts to be registered",
                   api.getHosts(), arrayWithSize(0));

        MidolmanLauncher launcher =
            MidolmanLauncher.start(With_Node_Agent,
                                   "InterfaceManagementTest.testUpdateInterfaceAddressForHost");

        TapWrapper tapWrapper = new TapWrapper(tapInterfaceName);

        try {
            final DtoHost host = waitForHostRegistration();

            final Matcher<DtoInterface[]> tapMatcher =
                hasItemInArray(hasProperty("name", equalTo(tapInterfaceName)));

            DtoInterface[] interfaces =
                waitFor("the interface to be exposed via API",
                        new Timed.Execution<DtoInterface[]>() {
                            @Override
                            protected void _runOnce() throws Exception {
                                setResult(api.getHostInterfaces(host));
                                setCompleted(tapMatcher.matches(getResult()));
                            }
                        });

            DtoInterface dtoInterface = null;
            for (DtoInterface anInterface : interfaces) {
                if (anInterface.getName().equals(tapInterfaceName)) {
                    dtoInterface = anInterface;
                    break;
                }
            }

            String targetIpAddress = "10.56.34.1";
            @SuppressWarnings("ConstantConditions")
            InetAddress addresses[] =
                new InetAddress[dtoInterface.getAddresses().length + 1];

            System.arraycopy(dtoInterface.getAddresses(), 0,
                             addresses, 0, dtoInterface.getAddresses().length);


            addresses[addresses.length - 1] =
                InetAddress.getByName(targetIpAddress);

            assertThat(
                "the interface dto is ok (non null and with a different mac)",
                dtoInterface,
                allOf(notNullValue(),
                      hasProperty("addresses",
                                  not(hasItemInArray(
                                      hasProperty("address", equalTo(
                                          new byte[]{(byte) 10, (byte) 56, (byte) 34, (byte) 1}))
                                  )))));

            //noinspection ConstantConditions
            dtoInterface.setAddresses(addresses);

            final Matcher<DtoInterface[]> addressMatcher =
                hasItemInArray(
                    allOf(
                        hasProperty("name", equalTo(dtoInterface.getName())),
                        hasProperty("addresses", hasItemInArray(
                            hasProperty("address", Matchers.equalTo(
                                new byte[]{(byte) 10, (byte) 56, (byte) 34, (byte) 1}))
                        ))
                    ));

            // make the update
            api.updateInterface(dtoInterface);

            // wait to see the change
            waitFor("the interface address to change",
                    TimeUnit.SECONDS.toMillis(20),
                    TimeUnit.MILLISECONDS.toMillis(50),
                    new Timed.Execution<java.lang.Object>() {
                        @Override
                        protected void _runOnce() throws Exception {
                            setResult(api.getHostInterfaces(host));
                            log.debug("{}", getResult());
                            setCompleted(addressMatcher.matches(getResult()));
                        }
                    });
        } finally {
            launcher.stop();
            removeTapWrapper(tapWrapper);
        }
    }

    @Test
    public void testUpdateInterfaceDeleteAddressForHost()
        throws Exception {
        final String tapName = newTapName();

        assertThat("We were expecting no hosts to be registered",
                   api.getHosts(), arrayWithSize(0));

        MidolmanLauncher launcher =
            MidolmanLauncher.start(With_Node_Agent,
                                   "InterfaceManagementTest.testUpdateInterfaceDeleteAddressForHost");

        TapWrapper tapWrapper = new TapWrapper(tapName);

        Sudo.sudoExec(
            format("ip addr add 10.43.56.34/12 dev %s", tapName));

        try {
            final DtoHost host = waitForHostRegistration();

            DtoInterface dtoInterface = waitForNamedInterface(host, tapName);

            assertThat(
                "the interface should be visible with the correct address",
                dtoInterface,
                allOf(notNullValue(),
                      hasProperty("addresses",
                                  hasItemInArray(
                                      hasProperty("hostAddress",
                                                  equalTo("10.43.56.34"))))));

            List<InetAddress> newAddresses = new ArrayList<InetAddress>();
            //noinspection ConstantConditions
            for (InetAddress inetAddress : dtoInterface.getAddresses()) {
                if (!inetAddress.getHostAddress().equals("10.43.56.34")) {
                    newAddresses.add(inetAddress);
                }
            }

            dtoInterface.setAddresses(
                newAddresses.toArray(new InetAddress[newAddresses.size()]));

            final Matcher<DtoInterface[]> addressMatcher =
                hasItemInArray(
                    allOf(
                        hasProperty("name", equalTo(dtoInterface.getName())),
                        hasProperty("addresses", hasItemInArray(
                            hasProperty("hostAddress",
                                        equalTo("10.43.56.34"))))));

            // make the update
            api.updateInterface(dtoInterface);

            // wait to see the change
            waitFor("the interface MAC address to change",
                    new Timed.Execution<java.lang.Object>() {
                        @Override
                        protected void _runOnce() throws Exception {
                            setResult(api.getHostInterfaces(host));
                            setCompleted(addressMatcher.matches(getResult()));
                        }
                    });
        } finally {
            launcher.stop();
            removeTapWrapper(tapWrapper);
        }
    }

    private DtoInterface waitForNamedInterface(final DtoHost host,
                                               String tapInterfaceName)
        throws Exception {
        final Matcher<DtoInterface[]> tapMatcher =
            hasItemInArray(hasProperty("name", equalTo(tapInterfaceName)));

        DtoInterface[] interfaces =
            waitFor("the interface to be exposed via API",
                    new Timed.Execution<DtoInterface[]>() {
                        @Override
                        protected void _runOnce() throws Exception {
                            setResult(api.getHostInterfaces(host));
                            setCompleted(tapMatcher.matches(getResult()));
                        }
                    });

        for (DtoInterface anInterface : interfaces) {
            if (anInterface.getName().equals(tapInterfaceName)) {
                return anInterface;
            }
        }

        return null;
    }

    private DtoHost waitForHostRegistration() throws Exception {
        DtoHost[] hosts =
            waitFor("host registration",
                    new Timed.Execution<DtoHost[]>() {
                        @Override
                        protected void _runOnce() throws Exception {
                            setResult(api.getHosts());
                            setCompleted(getResult().length == 1);
                        }
                    });

        final DtoHost host = hosts[0];
        assertThat("The new host should be alive!", host.isAlive());
        return host;
    }*/

    private static int tapInterfaceId = 1;

    private String newTapName() {
        return "tstIMTap" + (++tapInterfaceId);
    }
}
