/*
 * Copyright 2012 Midokura Europe SARL
 */

package com.midokura.midonet.client.dto;

import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement
public class DtoDhcpSubnet {
    private String subnetPrefix;
    private int subnetLength;
    private String defaultGateway;
    private String serverAddr;
    private String dnsServerAddr;
    private short interfaceMTU;
    private List<DtoDhcpOption121> opt121Routes;
    private URI hosts;
    private URI uri;

    public DtoDhcpSubnet() {
        this.opt121Routes = new ArrayList<DtoDhcpOption121>();
    }

    public String getSubnetPrefix() {
        return subnetPrefix;
    }

    public void setSubnetPrefix(String subnetPrefix) {
        this.subnetPrefix = subnetPrefix;
    }

    public int getSubnetLength() {
        return subnetLength;
    }

    public void setSubnetLength(int subnetLength) {
        this.subnetLength = subnetLength;
    }

    public String getDefaultGateway() {
        return defaultGateway;
    }

    public void setDefaultGateway(String defaultGateway) {
        this.defaultGateway = defaultGateway;
    }

    public String getServerAddr() {
        return serverAddr;
    }

    public void setServerAddr(String serverAddr) {
        this.serverAddr = serverAddr;
    }

    public String getDnsServerAddr() {
        return dnsServerAddr;
    }

    public void setDnsServerAddr(String dnsServerAddr) {
        this.dnsServerAddr = dnsServerAddr;
    }

    public short getInterfaceMTU() {
        return interfaceMTU;
    }

    public void setInterfaceMTU(short interfaceMTU) {
        this.interfaceMTU = interfaceMTU;
    }

    public List<DtoDhcpOption121> getOpt121Routes() {
        return opt121Routes;
    }

    public void setOpt121Routes(List<DtoDhcpOption121> opt121Routes) {
        this.opt121Routes = opt121Routes;
    }

    public URI getHosts() {
        return hosts;
    }

    public void setHosts(URI hosts) {
        this.hosts = hosts;
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DtoDhcpSubnet that = (DtoDhcpSubnet) o;

        if (subnetLength != that.subnetLength) return false;
        if (defaultGateway != null
                ? !defaultGateway.equals(that.defaultGateway)
                : that.defaultGateway != null)
            return false;
        if (hosts != null
                ? !hosts.equals(that.hosts)
                : that.hosts != null)
            return false;
        if (opt121Routes != null
                ? !opt121Routes.equals(that.opt121Routes)
                : that.opt121Routes != null)
            return false;
        if (subnetPrefix != null
                ? !subnetPrefix.equals(that.subnetPrefix)
                : that.subnetPrefix != null)
            return false;
        if (uri != null
                ? !uri.equals(that.uri)
                : that.uri != null)
            return false;
        if (serverAddr != null
                ? !serverAddr.equals(that.serverAddr)
                : that.serverAddr != null)
            return false;
        if (dnsServerAddr != null
                ? !dnsServerAddr.equals(that.dnsServerAddr)
                : that.dnsServerAddr!= null)
            return false;
        if (interfaceMTU != that.interfaceMTU)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = subnetPrefix != null ? subnetPrefix.hashCode() : 0;
        result = 31 * result + subnetLength;
        result = 31 * result + (defaultGateway != null
                ? defaultGateway.hashCode() : 0);
        result = 31 * result + (serverAddr != null
                ? serverAddr.hashCode() : 0);
        result = 31 * result + (dnsServerAddr != null
                ? dnsServerAddr.hashCode() : 0);
        result = 31 * result + interfaceMTU;
        result = 31 * result + (opt121Routes != null
                ? opt121Routes.hashCode() : 0);
        result = 31 * result + (hosts != null ? hosts.hashCode() : 0);
        result = 31 * result + (uri != null ? uri.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DtoDhcpSubnet{" +
                "defaultGateway='" + defaultGateway + '\'' +
                ", subnetPrefix='" + subnetPrefix + '\'' +
                ", subnetLength=" + subnetLength + '\'' + 
                ", serverAddr='" + serverAddr + '\'' +
                ", dnsServerAddr='" + dnsServerAddr + '\'' +
                ", interfaceMTU='" + interfaceMTU +
                ", opt121Routes=" + opt121Routes +
                ", hosts=" + hosts +
                ", uri=" + uri +
                '}';
    }
}
