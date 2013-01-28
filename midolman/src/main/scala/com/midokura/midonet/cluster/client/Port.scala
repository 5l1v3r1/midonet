/*
 * Copyright 2012 Midokura Europe SARL
 */

package com.midokura.midonet.cluster.client

import collection.JavaConversions._
import java.util.UUID

import com.midokura.packets.{IntIPv4, MAC}

trait Port[T] {
    var id: UUID = null
    var deviceID: UUID = null
    var inFilterID: UUID = null
    var outFilterID: UUID = null
    var properties: Map[String, String] = null  //move

    def self: T = {
        this.asInstanceOf[T]
    }

    def setID(id: UUID): T = {
        this.id = id
        self
    }

    def setDeviceID(id: UUID): T = {
        this.deviceID = id
        self
    }

    def setInFilter(chain: UUID): T = {
        this.inFilterID = chain
        self
    }

    def setOutFilter(chain: UUID): T = {
        this.outFilterID = chain
        self
    }

    def setProperties(props: Map[String, String]): T = {
        properties = props; self
    }

    def setProperties(props: java.util.Map[String, String]): T = {
        properties = Map(props.toSeq:_*)
        self
    }
}

trait ExteriorPort[T] extends Port[T] {
    var tunnelKey: Long = _
    var portGroups: Set[UUID] = null
    var hostID: UUID = null
    var interfaceName: String = null

    def setTunnelKey(key: Long): T = {
        this.tunnelKey = key; self
    }

    def setPortGroups(groups: Set[UUID]): T = {
        portGroups = groups; self
    }

    def setPortGroups(groups: java.util.Set[UUID]): T = {
        portGroups = Set(groups.toSeq:_*)
        self
    }

    def setHostID(id: UUID): T = {
        this.hostID = id; self
    }

    def setInterfaceName(name: String): T = {
        this.interfaceName = name; self
    }
}

trait InteriorPort[T] extends Port[T] {
    var peerID: UUID = null

    def setPeerID(id: UUID): T = {
        this.peerID = id; self
    }
}

trait BridgePort[T] extends Port[T] {}

trait RouterPort[T] extends Port[T] {
    var portAddr: IntIPv4 = null
    var portMac: MAC = null

    def nwLength(): Int = {
        portAddr.getMaskLength
    }

    def nwAddr(): IntIPv4 = {
        portAddr.toNetworkAddress
    }

    def setPortAddr(addr: IntIPv4): T = {
        this.portAddr = addr; self
    }

    def setPortMac(mac: MAC): T = {
        this.portMac = mac; self
    }

}

class ExteriorBridgePort
    extends ExteriorPort[ExteriorBridgePort]
    with BridgePort[ExteriorBridgePort] {}

class InteriorBridgePort
    extends InteriorPort[InteriorBridgePort]
    with BridgePort[InteriorBridgePort] {}

class ExteriorRouterPort
    extends ExteriorPort[ExteriorRouterPort]
    with RouterPort[ExteriorRouterPort] {}

class InteriorRouterPort
    extends InteriorPort[InteriorRouterPort]
    with RouterPort[InteriorRouterPort] {}

