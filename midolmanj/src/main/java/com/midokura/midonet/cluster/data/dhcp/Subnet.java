/*
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midonet.cluster.data.dhcp;

import com.midokura.midonet.cluster.data.Entity;
import com.midokura.packets.IntIPv4;

import java.util.List;

/**
 * DHCP subnet
 */
public class Subnet extends Entity.Base<String, Subnet.Data, Subnet> {

    public Subnet() {
        this(null, new Data());
    }

    public Subnet(String addr, Data data) {
        super(addr, data);
    }

    @Override
    protected Subnet self() {
        return this;
    }

    public IntIPv4 getSubnetAddr() {
        return getData().subnetAddr;
    }

    public Subnet setSubnetAddr(IntIPv4 subnetAddr) {
        getData().subnetAddr = subnetAddr;
        return self();
    }

    public IntIPv4 getServerAddr() {
        return getData().serverAddr;
    }

    public Subnet setServerAddr(IntIPv4 serverAddr) {
        getData().serverAddr = serverAddr;
        return self();
    }

    public IntIPv4 getDnsServerAddr() {
        return getData().dnsServerAddr;
    }

    public Subnet setDnsServerAddr(IntIPv4 dnsServerAddr) {
        getData().dnsServerAddr = dnsServerAddr;
        return self();
    }

    public IntIPv4 getDefaultGateway() {
        return getData().defaultGateway;
    }

    public Subnet setDefaultGateway(IntIPv4 defaultGateway) {
        getData().defaultGateway = defaultGateway;
        return self();
    }

    public short getInterfaceMTU() {
        return getData().interfaceMTU;
    }

    public Subnet setInterfaceMTU(short interfaceMTU) {
        getData().interfaceMTU = interfaceMTU;
        return self();
    }

    public List<Opt121> getOpt121Routes() {
        return getData().opt121Routes;
    }

    public Subnet setOpt121Routes(List<Opt121> opt121Routes) {
        getData().opt121Routes = opt121Routes;
        return self();
    }

    public static class Data {

        public IntIPv4 subnetAddr;
        public IntIPv4 serverAddr;
        public IntIPv4 dnsServerAddr;
        public IntIPv4 defaultGateway;
        short interfaceMTU;
        public List<Opt121> opt121Routes;

        @Override
        public String toString() {
            return "Subnet{" +
                    "subnetAddr=" + subnetAddr +
                    ", serverAddr=" + serverAddr +
                    ", dnsServerAddr=" + dnsServerAddr +
                    ", interfaceMTU=" + interfaceMTU +
                    ", defaultGateway=" + defaultGateway +
                    ", opt121Routes=" + opt121Routes +
                    '}';
        }
    }
}
