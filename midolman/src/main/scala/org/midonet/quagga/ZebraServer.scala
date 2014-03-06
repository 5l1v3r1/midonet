/**
 * Requestrver.scala - Quagga Zebra server classes.
 *
 * A pure Scala implementation of the Quagga Zebra server to connect
 * MidoNet and protocol daemons like BGP, OSPF and RIP.
 *
 * This module can connected using a Unix domain or a TCP server socket,
 * where Quagga uses a Unix domain socket by default.
 *
 * Copyright (c) 2011 Midokura KK. All rights reserved.
 */

package org.midonet.quagga

import java.net.{Socket, SocketAddress}
import java.nio.channels.spi.SelectorProvider
import java.nio.channels.{ByteChannel, SelectionKey}
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.GroupPrincipal
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.UserPrincipal
import scala.collection.mutable

import akka.actor.{ActorContext, Props, ActorRef, Actor}
import akka.event.LoggingReceive

import org.midonet.midolman.logging.ActorLogWithoutPath
import org.midonet.netlink.{AfUnix, NetlinkSelectorProvider, UnixDomainChannel}
import org.midonet.packets.IPv4Addr
import org.midonet.util.eventloop.{SelectListener, SelectLoop}

case class SpawnConnection(channel: ByteChannel)
case class ConnectionClosed(reqId: Int)

object ZebraServer {
    def apply(address: AfUnix.Address, handler: ZebraProtocolHandler,
              ifAddr: IPv4Addr, ifName: String, selectLoop: SelectLoop)
             (implicit context: ActorContext): ActorRef = {
        context.actorOf(
            Props(new ZebraServer(address, handler, ifAddr, ifName, selectLoop)).
                withDispatcher("actors.pinned-dispatcher"),
            "zebra-server-" + ifAddr + "-" + ifName)
    }
}

class ZebraServer(val address: AfUnix.Address, val handler: ZebraProtocolHandler,
                  val ifAddr: IPv4Addr, val ifName: String,
                  val selectLoop: SelectLoop) extends Actor with ActorLogWithoutPath {

    var lastRequestId = 0
    val server = makeServer

    val zebraConnections = mutable.Set[ActorRef]()
    val zebraConnMap = mutable.Map[ActorRef, ByteChannel]()

    private def makeServer: UnixDomainChannel = {
        SelectorProvider.provider() match {
            case nl: NetlinkSelectorProvider =>
                nl.openUnixDomainSocketChannel(AfUnix.Type.SOCK_STREAM)
            case other =>
                log.error("Invalid selector type: {}", other.getClass());
                throw new RuntimeException()
        }
    }

    private def setSocketOwnership() {
        val file: Path = Paths.get(address.getPath)

        val owner: UserPrincipal = file.getFileSystem().
            getUserPrincipalLookupService().lookupPrincipalByName("quagga")
        Files.setOwner(file, owner);

        val group: GroupPrincipal = file.getFileSystem().
            getUserPrincipalLookupService().lookupPrincipalByGroupName("quagga")
        Files.getFileAttributeView(file, classOf[PosixFileAttributeView]).
            setGroup(group);
    }

    override def preStart() {
        log.info("creating ZebraServer on {}", address.getPath)
        server.bind(address)
        server.configureBlocking(false)
        setSocketOwnership()
        selectLoop.register(server, SelectionKey.OP_READ | SelectionKey.OP_ACCEPT,
            new SelectListener() {
                override def handleEvent(key: SelectionKey) {
                    log.info("Accepting connection")
                    val clientConn = server.accept
                    clientConn.configureBlocking(true)
                    self ! SpawnConnection(clientConn)
                }
            })
    }

    override def postStop() {
        log.info("Stopping zebra server with {} connections", zebraConnections.size)
        selectLoop.unregister(server, SelectionKey.OP_ACCEPT | SelectionKey.OP_READ)
        server.close()
        for ((actor, sock) <- zebraConnMap) {
            if (sock.isOpen)
                sock.close()
            context.system.stop(actor)
        }
        zebraConnMap.clear()
        zebraConnections.clear()
        log.debug("Server stopped")
    }

    private def addZebraConn(requestId: Int, channel: ByteChannel): ActorRef = {
        log.debug("creating a ZebraConnection for id: {}", requestId)

        val connName = "zebra-conn-" + ifName + "-" + requestId
        val zebraConn = context.actorOf(
            Props(new ZebraConnection(self, handler, ifAddr, ifName, requestId, channel)).
                withDispatcher("actors.pinned-dispatcher"), connName)

        zebraConnections += zebraConn
        zebraConnMap(zebraConn) = channel
        log.debug("ZebraConnection created for ifAddr: {}, ifName: {}", ifAddr, ifName)
        zebraConn
    }

    override def receive = LoggingReceive {
        case SpawnConnection(channel) =>
            lastRequestId += 1
            log.debug("new client with id {}", lastRequestId)
            val zebraConn = addZebraConn(lastRequestId, channel)
            zebraConn ! ProcessMessage

        case ConnectionClosed(requestId) =>
            log.debug("client with id {} disconnected", requestId)
            zebraConnections -= sender
            zebraConnMap -= sender
            context.system.stop(sender)

        case m => log.error("Unknown message received - {}", m)
    }
}
