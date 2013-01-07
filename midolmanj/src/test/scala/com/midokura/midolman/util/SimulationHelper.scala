/*
 * Copyright 2012 Midokura Pte. Ltd.
 */

package com.midokura.midolman.util

import java.util.UUID

import scala.collection.JavaConversions._

import com.midokura.packets._
import com.midokura.midolman.flows.{WildcardFlow, WildcardMatch}
import com.midokura.midolman.MidolmanTestCase
import com.midokura.midolman.util.AddressConversions._
import com.midokura.sdn.dp.Packet
import com.midokura.sdn.dp.flows._
import com.midokura.midolman.DatapathController.PacketIn
import com.midokura.midolman.FlowController.AddWildcardFlow

trait SimulationHelper extends MidolmanTestCase {

    final val IPv6_ETHERTYPE: Short = 0x86dd.toShort

    def applyOutPacketActions(packet: Packet): Ethernet = {
        packet should not be null
        packet.getData should not be null
        packet.getActions should not be null

        val eth = Ethernet.deserialize(packet.getData)
        var ip: IPv4 = null
        var tcp: TCP = null
        var udp: UDP = null
        eth.getEtherType match {
            case IPv4.ETHERTYPE =>
                ip = eth.getPayload.asInstanceOf[IPv4]
                ip.getProtocol match {
                    case TCP.PROTOCOL_NUMBER =>
                        tcp = ip.getPayload.asInstanceOf[TCP]
                    case UDP.PROTOCOL_NUMBER =>
                        udp = ip.getPayload.asInstanceOf[UDP]
                }
        }

        val actions = packet.getActions.flatMap(action => action match {
            case a: FlowActionSetKey => Option(a)
            case _ => None
        }).toSet

        // TODO(guillermo) incomplete, but it should cover testing needs
        actions foreach { action =>
            action.getFlowKey match {
                case key: FlowKeyEthernet =>
                    if (key.getDst != null) eth.setDestinationMACAddress(key.getDst)
                    if (key.getSrc != null) eth.setSourceMACAddress(key.getSrc)
                case key: FlowKeyIPv4 =>
                    ip should not be null
                    if (key.getDst != 0) ip.setDestinationAddress(key.getDst)
                    if (key.getSrc != 0) ip.setSourceAddress(key.getSrc)
                    if (key.getTtl != 0) ip.setTtl(key.getTtl)
                case key: FlowKeyTCP =>
                    tcp should not be null
                    if (key.getDst != 0) tcp.setDestinationPort(key.getDst)
                    if (key.getSrc != 0) tcp.setSourcePort(key.getSrc)
                case key: FlowKeyUDP =>
                    udp should not be null
                    if (key.getUdpDst != 0) udp.setDestinationPort(key.getUdpDst)
                    if (key.getUdpSrc != 0) udp.setSourcePort(key.getUdpSrc)
            }
        }

        eth
    }

    def getOutPacketPorts(packet: Packet): Set[Short] = {
        packet should not be null
        packet.getData should not be null
        packet.getActions should not be null

        packet.getActions.flatMap(action => action match {
            case a: FlowActionOutput => Option(a.getValue.getPortNumber.toShort)
            case _ => None
        }).toSet
    }

    def injectArpRequest(portName: String, srcIp: Int, srcMac: MAC, dstIp: Int) {
        val arp = new ARP()
        arp.setHardwareType(ARP.HW_TYPE_ETHERNET)
        arp.setProtocolType(ARP.PROTO_TYPE_IP)
        arp.setHardwareAddressLength(6)
        arp.setProtocolAddressLength(4)
        arp.setOpCode(ARP.OP_REQUEST)
        arp.setSenderHardwareAddress(srcMac)
        arp.setSenderProtocolAddress(srcIp)
        arp.setTargetHardwareAddress("ff:ff:ff:ff:ff:ff")
        arp.setTargetProtocolAddress(dstIp)

        val eth = new Ethernet()
        eth.setPayload(arp)
        eth.setSourceMACAddress(srcMac)
        eth.setDestinationMACAddress("ff:ff:ff:ff:ff:ff")
        eth.setEtherType(ARP.ETHERTYPE)
        triggerPacketIn(portName, eth)
    }

    def feedArpCache(portName: String, srcIp: Int, srcMac: MAC,
                     dstIp: Int, dstMac: MAC) {
        val arp = new ARP()
        arp.setHardwareType(ARP.HW_TYPE_ETHERNET)
        arp.setProtocolType(ARP.PROTO_TYPE_IP)
        arp.setHardwareAddressLength(6)
        arp.setProtocolAddressLength(4)
        arp.setOpCode(ARP.OP_REPLY)
        arp.setSenderHardwareAddress(srcMac)
        arp.setSenderProtocolAddress(srcIp)
        arp.setTargetHardwareAddress(dstMac)
        arp.setTargetProtocolAddress(dstIp)

        val eth = new Ethernet()
        eth.setPayload(arp)
        eth.setSourceMACAddress(srcMac)
        eth.setDestinationMACAddress(dstMac)
        eth.setEtherType(ARP.ETHERTYPE)
        triggerPacketIn(portName, eth)
    }

    def injectTcp(port: String,
              fromMac: MAC, fromIp: IntIPv4, fromPort: Int,
              toMac: MAC, toIp: IntIPv4, toPort: Int,
              syn: Boolean = false, rst: Boolean = false, ack: Boolean = false) {
        val tcp = new TCP()
        tcp.setSourcePort(fromPort)
        tcp.setDestinationPort(toPort)
        val flags = 0 | (if (syn) 0x00 else 0x02) |
                        (if (rst) 0x00 else 0x04) |
                        (if (ack) 0x00 else 0x10)
        tcp.setFlags(flags.toShort)
        tcp.setPayload(new Data("TCP Payload".getBytes))
        val ip = new IPv4().setSourceAddress(fromIp.addressAsInt).
                            setDestinationAddress(toIp.addressAsInt).
                            setProtocol(TCP.PROTOCOL_NUMBER).
                            setTtl(64).
                            setPayload(tcp)
        val eth = new Ethernet().setSourceMACAddress(fromMac).
                                 setDestinationMACAddress(toMac).
                                 setEtherType(IPv4.ETHERTYPE).
                                 setPayload(ip).asInstanceOf[Ethernet]
        triggerPacketIn(port, eth)
    }

    def expectPacketOnPort(port: UUID): PacketIn = {
        fishForRequestOfType[PacketIn](dpProbe())

        val pktInMsg = simProbe().expectMsgClass(classOf[PacketIn])
        pktInMsg should not be null
        pktInMsg.pktBytes should not be null
        pktInMsg.wMatch should not be null
        pktInMsg.wMatch.getInputPortUUID should be === port
        pktInMsg
    }

    def fishForFlowAddedMessage(): WildcardFlow = {
        val addFlowMsg = fishForRequestOfType[AddWildcardFlow](flowProbe())
        addFlowMsg should not be null
        addFlowMsg.flow should not be null
        addFlowMsg.flow
    }

    def expectFlowAddedMessage(): WildcardFlow = {
        val addFlowMsg = requestOfType[AddWildcardFlow](flowProbe())
        addFlowMsg should not be null
        addFlowMsg.flow should not be null
        addFlowMsg.flow
    }

    def expectMatchForIPv4Packet(pkt: Ethernet, wmatch: WildcardMatch) {
        wmatch.getEthernetDestination should be === pkt.getDestinationMACAddress
        wmatch.getEthernetSource should be === pkt.getSourceMACAddress
        wmatch.getEtherType should be === pkt.getEtherType
        val ipPkt = pkt.getPayload.asInstanceOf[IPv4]
        wmatch.getNetworkDestination should be === ipPkt.getDestinationAddress
        wmatch.getNetworkSource should be === ipPkt.getSourceAddress
        wmatch.getNetworkProtocol should be === ipPkt.getProtocol

        ipPkt.getProtocol match {
            case UDP.PROTOCOL_NUMBER =>
                val udpPkt = ipPkt.getPayload.asInstanceOf[UDP]
                wmatch.getTransportDestination should be === udpPkt.getDestinationPort
                wmatch.getTransportSource should be === udpPkt.getSourcePort
            case TCP.PROTOCOL_NUMBER =>
                val tcpPkt = ipPkt.getPayload.asInstanceOf[TCP]
                wmatch.getTransportDestination should be === tcpPkt.getDestinationPort
                wmatch.getTransportSource should be === tcpPkt.getSourcePort
            case _ =>
        }
    }

    def localPortNumberToName(portNo: Short): Option[String] = {
        dpController().underlyingActor.localToVifPorts.get(portNo) match {
            case Some(id) => dpController().underlyingActor.vifPorts.get(id)
            case None => None
        }
    }
}
