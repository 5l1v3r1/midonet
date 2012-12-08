/*
 * Copyright 2012 Midokura Europe SARL
 */

package com.midokura.midonet.functional_test.topology;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.midokura.midonet.client.dto.DtoBgp;
import com.midokura.midonet.client.dto.DtoExteriorRouterPort;
import com.midokura.midonet.client.dto.DtoRoute;
import com.midokura.midonet.client.dto.DtoRouter;
import com.midokura.midonet.client.dto.DtoVpn;
import com.midokura.packets.IntIPv4;
import com.midokura.packets.MAC;
import com.midokura.midonet.functional_test.mocks.MidolmanMgmt;

public class ExteriorRouterPort {

    public static class VMPortBuilder {
        private MidolmanMgmt mgmt;
        private DtoRouter router;
        private DtoExteriorRouterPort port;
        IntIPv4 vmAddr;

        public VMPortBuilder(MidolmanMgmt mgmt, DtoRouter router) {
            this.mgmt = mgmt;
            this.router = router;
            port = new DtoExteriorRouterPort();
            port.setNetworkLength(24);
        }

        public VMPortBuilder setVMAddress(IntIPv4 addr) {
            int mask = ~0 << 8;
            IntIPv4 netAddr = new IntIPv4(addr.addressAsInt() & mask);
            port.setNetworkAddress(netAddr.toString());
            vmAddr = addr;
            // The router port's address is 1 + the network address.
            IntIPv4 portAddr = new IntIPv4(1 + netAddr.addressAsInt());
            port.setPortAddress(portAddr.toString());
            return this;
        }

        public ExteriorRouterPort build() {
            DtoExteriorRouterPort p = mgmt.addExteriorRouterPort(
                    router, port);
            DtoRoute rt = new DtoRoute();
            rt.setDstNetworkAddr(vmAddr.toUnicastString());
            rt.setDstNetworkLength(vmAddr.getMaskLength());
            rt.setSrcNetworkAddr("0.0.0.0");
            rt.setSrcNetworkLength(0);
            rt.setType(DtoRoute.Normal);
            rt.setNextHopPort(p.getId());
            rt.setWeight(10);
            rt = mgmt.addRoute(router, rt);
            return new ExteriorRouterPort(mgmt, p);
        }
    }

    /**
     * A GatewayPort is a Exterior Port that will have local link addresses,
     * connect to a gateway and forward many routes.
     */
    public static class GWPortBuilder {
        private MidolmanMgmt mgmt;
        private DtoRouter router;
        private DtoExteriorRouterPort port;
        private IntIPv4 peerIp;
        private List<IntIPv4> routes;

        public GWPortBuilder(MidolmanMgmt mgmt, DtoRouter router) {
            this.mgmt = mgmt;
            this.router = router;
            port = new DtoExteriorRouterPort();
            port.setNetworkLength(30);
            routes = new ArrayList<IntIPv4>();
        }

        public GWPortBuilder setLocalMac(MAC mac) {
            port.setPortMac(mac.toString());
            return this;
        }

        public GWPortBuilder setLocalLink(IntIPv4 localIp, IntIPv4 peerIp) {
            this.peerIp = peerIp;
            port.setPortAddress(localIp.toString());
            int mask = ~0 << 8;
            IntIPv4 netAddr = new IntIPv4(localIp.addressAsInt() & mask);
            port.setNetworkAddress(netAddr.toString());
            return this;
        }

        public GWPortBuilder addRoute(IntIPv4 nwDst) {
            routes.add(nwDst);
            return this;
        }

        public ExteriorRouterPort build() {
            DtoExteriorRouterPort p = mgmt.addExteriorRouterPort(
                    router, port);
            for (IntIPv4 dst : routes) {
                DtoRoute rt = new DtoRoute();
                rt.setDstNetworkAddr(dst.toUnicastString());
                rt.setDstNetworkLength(dst.getMaskLength());
                rt.setSrcNetworkAddr("0.0.0.0");
                rt.setSrcNetworkLength(0);
                rt.setType(DtoRoute.Normal);
                rt.setNextHopPort(p.getId());
                rt.setNextHopGateway(peerIp.toString());
                rt.setWeight(10);
                rt = mgmt.addRoute(router, rt);
            }
            return new ExteriorRouterPort(mgmt, p);
        }
    }

    public static class VPNPortBuilder {
        private MidolmanMgmt mgmt;
        private DtoRouter router;
        private DtoExteriorRouterPort port;
        private DtoVpn vpn;

        public VPNPortBuilder(MidolmanMgmt mgmt, DtoRouter router) {
            this.mgmt = mgmt;
            this.router = router;
            this.port = new DtoExteriorRouterPort();
            this.vpn = new DtoVpn();
        }

        public VPNPortBuilder setVpnType(DtoVpn.VpnType type) {
            vpn.setVpnType(type);
            return this;
        }

        public VPNPortBuilder setLocalIp(IntIPv4 addr) {
            int mask = ~0 << 8;
            IntIPv4 netAddr = new IntIPv4(addr.addressAsInt() & mask);
            port.setNetworkAddress(netAddr.toString());
            port.setNetworkLength(24);
            // The router port's address is 1 + the network address.
            IntIPv4 portAddr = new IntIPv4(1 + netAddr.addressAsInt());
            port.setPortAddress(portAddr.toString());
            return this;
        }

        public VPNPortBuilder setPrivatePortId(UUID vportId) {
            vpn.setPrivatePortId(vportId);
            return this;
        }

        public VPNPortBuilder setLayer4Port(int layer4Port) {
            vpn.setPort(layer4Port);
            return this;
        }

        public VPNPortBuilder setRemoteIp(String remoteIp) {
            vpn.setRemoteIp(remoteIp);
            return this;
        }

        public ExteriorRouterPort build() {
            DtoExteriorRouterPort p = mgmt.addExteriorRouterPort(
                    router, port);
            vpn = mgmt.addVpn(p, vpn);
            DtoRoute rt = new DtoRoute();
            rt.setDstNetworkAddr(port.getNetworkAddress());
            rt.setDstNetworkLength(port.getNetworkLength());
            rt.setSrcNetworkAddr("0.0.0.0");
            rt.setSrcNetworkLength(0);
            rt.setType(DtoRoute.Normal);
            rt.setNextHopPort(p.getId());
            rt.setWeight(10);
            rt = mgmt.addRoute(router, rt);
            ExteriorRouterPort port = new ExteriorRouterPort(mgmt, p);
            port.setVpn(vpn);
            return port;
        }
    }

    MidolmanMgmt mgmt;
    public DtoExteriorRouterPort port;
    private DtoVpn vpn;

    ExteriorRouterPort(MidolmanMgmt mgmt, DtoExteriorRouterPort port) {
        this.mgmt = mgmt;
        this.port = port;
    }

    public DtoVpn getVpn() {
        return vpn;
    }

    public void setVpn(DtoVpn vpn) {
        this.vpn = vpn;
    }

    public void delete() {
        mgmt.delete(port.getUri());
    }

    public Bgp.Builder addBgp() {

        return new Bgp.Builder() {

            int localAS;
            int peerAS;
            String peerAddress;

            @Override
            public Bgp.Builder setLocalAs(int localAS) {
                this.localAS = localAS;
                return this;
            }

            @Override
            public Bgp.Builder setPeer(int peerAS, String peerAddress) {
                this.peerAS = peerAS;
                this.peerAddress = peerAddress;

                return this;
            }

            @Override
            public Bgp build() {
                DtoBgp bgp = new DtoBgp();

                bgp.setLocalAS(localAS);
                bgp.setPeerAS(peerAS);
                bgp.setPeerAddr(peerAddress);

                return new Bgp(mgmt, mgmt.addBGP(port, bgp));
            }
        };
    }

    public IntIPv4 getIpAddr() {
        return IntIPv4.fromString(port.getPortAddress());
    }

    public MAC getMacAddr() {
        return MAC.fromString(port.getPortMac());
    }
}
