/*
 * Copyright 2012 Midokura Europe SARL
 */

package com.midokura.midonet.api.dhcp;

import com.midokura.midonet.api.RelativeUriResource;
import com.midokura.midonet.api.ResourceUriBuilder;
import com.midokura.midonet.cluster.data.dhcp.Host;
import com.midokura.packets.IntIPv4;
import com.midokura.packets.MAC;

import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;

@XmlRootElement
public class DhcpHost extends RelativeUriResource {
    protected String macAddr;
    protected String ipAddr; // DHCP "your ip address"
    protected String name; // DHCP option 12 - host name

    public DhcpHost(String macAddr, String ipAddr, String name) {
        this.macAddr = macAddr;
        this.ipAddr = ipAddr;
        this.name = name;
    }

    /* Default constructor - for deserialization. */
    public DhcpHost() {
    }

    public DhcpHost(Host host) {
        this.ipAddr = host.getIp().toString();
        this.macAddr = host.getMAC().toString();
        this.name = host.getName();
    }

    /**
     * @return the self URI
     */
    @Override
    public URI getUri() {
        if (getParentUri() != null && macAddr != null) {
            return ResourceUriBuilder.getDhcpHost(getParentUri(), macAddr);
        } else {
            return null;
        }
    }

    public String getMacAddr() {
        return macAddr;
    }

    public void setMacAddr(String macAddr) {
        this.macAddr = macAddr;
    }

    public String getIpAddr() {
        return ipAddr;
    }

    public void setIpAddr(String ipAddr) {
        this.ipAddr = ipAddr;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Host toData() {
        return new Host()
                .setIp(IntIPv4.fromString(this.ipAddr))
                .setMAC(MAC.fromString(this.macAddr))
                .setName(this.name);
    }

}
