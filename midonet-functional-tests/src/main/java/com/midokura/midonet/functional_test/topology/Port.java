/*
 * Copyright 2011 Midokura Europe SARL
 */

package com.midokura.midonet.functional_test.topology;

import java.util.UUID;

import com.midokura.midonet.client.dto.DtoBgp;
import com.midokura.midonet.client.dto.DtoExteriorRouterPort;
import com.midokura.midonet.functional_test.mocks.MidolmanMgmt;

public class Port {

    MidolmanMgmt mgmt;
    DtoExteriorRouterPort port;
    String name;

    Port(MidolmanMgmt mgmt, DtoExteriorRouterPort port, String name) {
        this.mgmt = mgmt;
        this.port = port;
        this.name = name;
    }

    public Route.Builder addRoute() {
        // TODO Auto-generated method stub
        return null;
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

    public String getName() {
        return name;
    }

    public UUID getId() {
        return port.getId();
    }
}
