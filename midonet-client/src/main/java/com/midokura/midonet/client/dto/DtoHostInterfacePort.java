/*
 * Copyright (c) 2012. Midokura Japan K.K.
 */

package com.midokura.midonet.client.dto;

import java.net.URI;
import java.util.UUID;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Author: Tomoe Sugihara <tomoe@midokura.com>
 * Date: 9/5/12
 * Time: 11:58 PM
 */

@XmlRootElement
public class DtoHostInterfacePort {

    private UUID hostId;
    private UUID portId;
    private String interfaceName;
    private URI uri;

    public UUID getHostId() {
        return hostId;
    }

    public void setHostId(UUID hostId) {
        this.hostId = hostId;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public UUID getPortId() {
        return portId;
    }

    public void setPortId(UUID portId) {
        this.portId = portId;
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    @Override
    public String toString() {
        return "DtoHostInterfacePort{" +
            "hostId=" + hostId +
            ", portId=" + portId +
            ", interfaceName='" + interfaceName + '\'' +
            ", uri=" + uri +
            '}';
    }
}
