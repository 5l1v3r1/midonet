/*
 * Copyright 2012 Midokura Pte. Ltd.
 */

package com.midokura.midolman.host.sensor;

import com.midokura.midolman.host.interfaces.InterfaceDescription;
import com.midokura.packets.MAC;
import org.junit.Test;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class TestIpAddrInterfaceSensor {

    @Test
    public void testUpdateInterfaceData() throws Exception {

        IpAddrInterfaceSensor interfaceSensor =
                new IpAddrInterfaceSensor() {
                    @Override
                    protected List<String> getInterfacesOutput() {
                        return Arrays.asList(
                                "1: lo: <LOOPBACK,UP,LOWER_UP> mtu 16436 qdisc noqueue state UNKNOWN",
                                "link/loopback 00:00:00:00:00:00 brd 00:00:00:00:00:00",
                                "inet 127.0.0.1/8 scope host lo",
                                "inet6 ::1/128 scope host",
                                "valid_lft forever preferred_lft forever",
                                "2: eth0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc pfifo_fast state UP qlen 1000",
                                "link/ether 08:00:27:c8:c1:f3 brd ff:ff:ff:ff:ff:ff",
                                "inet 172.16.16.16/16 brd 172.16.255.255 scope global eth0:1",
                                "inet 192.168.2.68/24 brd 192.168.2.255 scope global eth0",
                                "inet6 2005::1/64 scope global",
                                "valid_lft forever preferred_lft forever",
                                "inet6 2004::1/10 scope global",
                                "valid_lft forever preferred_lft forever",
                                "inet6 2003::1/10 scope global",
                                "valid_lft forever preferred_lft forever",
                                "inet6 2002::1/10 scope global",
                                "valid_lft forever preferred_lft forever",
                                "inet6 2001::1/10 scope global",
                                "valid_lft forever preferred_lft forever",
                                "inet6 fe80::a00:27ff:fec8:c1f3/64 scope link",
                                "valid_lft forever preferred_lft forever",
                                "3: virbr0: <BROADCAST,MULTICAST> mtu 1500 qdisc noqueue state DOWN",
                                "link/ether 1a:4e:bc:c7:ea:ff brd ff:ff:ff:ff:ff:ff",
                                "inet 192.168.122.1/24 brd 192.168.122.255 scope global virbr0",
                                "5: xxx: <BROADCAST,MULTICAST> mtu 1500 qdisc noop state DOWN qlen 500",
                                "link/ether 4e:07:50:07:55:8a brd ff:ff:ff:ff:ff:ff"
                        );
                    }
                };

        List<InterfaceDescription> interfaces =
                interfaceSensor.updateInterfaceData(new ArrayList<InterfaceDescription>());

        //Check that we parsed all the interfaces
        assertThat(interfaces.size(), equalTo(4));

        // Check first interface
        InterfaceDescription interfaceDescription = interfaces.get(0);
        assertThat(interfaceDescription.getName(), equalTo("lo"));
        assertThat(interfaceDescription.isUp(), equalTo(true));
        assertThat(interfaceDescription.getMtu(), equalTo(16436));
        assertThat(interfaceDescription.getMac(), equalTo(MAC.fromString("00:00:00:00:00:00").getAddress()));
        assertThat(interfaceDescription.getInetAddresses().size(), equalTo(2));
        assertThat(interfaceDescription.getInetAddresses().get(0), equalTo(InetAddress.getByName("127.0.0.1")));
        assertThat(interfaceDescription.getInetAddresses().get(1), equalTo(InetAddress.getByName("::1")));
        assertThat(interfaceDescription.getEndpoint(), equalTo(InterfaceDescription.Endpoint.LOCALHOST));

        // Check second interface
        interfaceDescription = interfaces.get(1);
        assertThat(interfaceDescription.getName(), equalTo("eth0"));
        assertThat(interfaceDescription.isUp(), equalTo(true));
        assertThat(interfaceDescription.getMtu(), equalTo(1500));
        assertThat(interfaceDescription.getMac(), equalTo(MAC.fromString("08:00:27:C8:C1:F3").getAddress()));
        assertThat(interfaceDescription.getInetAddresses().size(), equalTo(8));
        assertThat(interfaceDescription.getInetAddresses().get(0), equalTo(InetAddress.getByName("172.16.16.16")));
        assertThat(interfaceDescription.getInetAddresses().get(1), equalTo(InetAddress.getByName("192.168.2.68")));
        assertThat(interfaceDescription.getInetAddresses().get(2), equalTo(InetAddress.getByName("2005::1")));
        assertThat(interfaceDescription.getInetAddresses().get(3), equalTo(InetAddress.getByName("2004::1")));
        assertThat(interfaceDescription.getInetAddresses().get(4), equalTo(InetAddress.getByName("2003::1")));
        assertThat(interfaceDescription.getInetAddresses().get(5), equalTo(InetAddress.getByName("2002::1")));
        assertThat(interfaceDescription.getInetAddresses().get(6), equalTo(InetAddress.getByName("2001::1")));
        assertThat(interfaceDescription.getInetAddresses().get(7), equalTo(InetAddress.getByName("fe80::a00:27ff:fec8:c1f3")));
        assertThat(interfaceDescription.getEndpoint(), equalTo(InterfaceDescription.Endpoint.UNKNOWN));

        // Check third interface
        interfaceDescription = interfaces.get(2);
        assertThat(interfaceDescription.getName(), equalTo("virbr0"));
        assertThat(interfaceDescription.isUp(), equalTo(false));
        assertThat(interfaceDescription.getMtu(), equalTo(1500));
        assertThat(interfaceDescription.getMac(), equalTo(MAC.fromString("1A:4E:BC:C7:EA:FF").getAddress()));
        assertThat(interfaceDescription.getInetAddresses().size(), equalTo(1));
        assertThat(interfaceDescription.getInetAddresses().get(0), equalTo(InetAddress.getByName("192.168.122.1")));
        assertThat(interfaceDescription.getEndpoint(), equalTo(InterfaceDescription.Endpoint.UNKNOWN));

        // Check fourth interface
        interfaceDescription = interfaces.get(3);
        assertThat(interfaceDescription.getName(), equalTo("xxx"));
        assertThat(interfaceDescription.isUp(), equalTo(false));
        assertThat(interfaceDescription.getMtu(), equalTo(1500));
        assertThat(interfaceDescription.getMac(), equalTo(MAC.fromString("4E:07:50:07:55:8A").getAddress()));
        assertThat(interfaceDescription.getInetAddresses().size(), equalTo(0));
        assertThat(interfaceDescription.getEndpoint(), equalTo(InterfaceDescription.Endpoint.UNKNOWN));
    }
}
