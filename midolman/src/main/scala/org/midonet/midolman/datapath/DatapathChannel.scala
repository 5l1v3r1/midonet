/*
 * Copyright 2014 Midokura SARL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.midonet.midolman.datapath

import java.nio.ByteBuffer
import java.util.{List => JList}

import com.lmax.disruptor._
import org.midonet.midolman.config.MidolmanConfig

import org.midonet.midolman.datapath.DisruptorDatapathChannel.DatapathEvent
import org.midonet.netlink._
import org.midonet.odp._
import org.midonet.odp.flows.FlowAction

trait DatapathChannel {
    def executePacket(packet: Packet, actions: JList[FlowAction]): Long
    def createFlow(flow: Flow): Long

    def start(datapath: Datapath): Unit
    def stop(): Unit
}

object DisruptorDatapathChannel {
    val PACKET_EXECUTION: Byte = 0
    val FLOW_CREATE: Byte = 1
    val SKIP: Byte = 2

    private val MAX_BUF_CAPACITY = 4 * 1024 * 1024

    sealed class DatapathEvent(var bb: ByteBuffer, var op: Byte)

    def eventFactory(config: MidolmanConfig): EventFactory[DatapathEvent] =
        new EventFactory[DatapathEvent] {
            override def newInstance(): DatapathEvent =
                new DatapathEvent(
                    BytesUtil.instance.allocateDirect(
                        config.datapath.sendBufferPoolBufSizeKb * 1024),
                    -1)
        }
}

class DisruptorDatapathChannel(ovsFamilies: OvsNetlinkFamilies,
                               ringBuffer: RingBuffer[DatapathEvent],
                               processors: Array[_ <: EventProcessor]) extends DatapathChannel {
    import DisruptorDatapathChannel._

    private var datapathId: Int = _
    private var supportsMegaflow: Boolean = _
    private val protocol = new OvsProtocol(0, ovsFamilies)

    def start(datapath: Datapath): Unit = {
        datapathId = datapath.getIndex
        supportsMegaflow = datapath.supportsMegaflow()

        processors foreach { proc =>
            ringBuffer.addGatingSequences(proc.getSequence)
        }

        processors.zipWithIndex foreach { case (proc, idx) =>
            val t = new Thread("datapath-output-" + idx) {
                override def run() {
                    proc.run()
                }
            }
            t.setDaemon(true)
            t.start()
        }
    }

    def stop(): Unit =
        processors foreach (_.halt())

    def executePacket(packet: Packet,
                      actions: JList[FlowAction]): Long = {
        if (actions.isEmpty) {
            return RingBuffer.INITIAL_CURSOR_VALUE
        }

        val seq = ringBuffer.next()
        val event = ringBuffer.get(seq)
        event.bb.clear()

        try {
            preparePacketExecute(event, packet, actions)
            event.op = PACKET_EXECUTION
        } catch { case t: Throwable =>
            event.op = SKIP
            throw t
        } finally {
            ringBuffer.publish(seq)
        }
        seq
    }

    private def preparePacketExecute(event: DatapathEvent,
                                     packet: Packet,
                                     actions: JList[FlowAction]): Unit =
        try {
            protocol.preparePacketExecute(datapathId, packet, actions, event.bb)
        } catch { case t: IndexOutOfBoundsException =>
            if (maybeGrow(event)) {
                preparePacketExecute(event, packet, actions)
            } else {
                throw t
            }
        }

    def createFlow(flow: Flow): Long = {
        val seq = ringBuffer.next()

        val event = ringBuffer.get(seq)
        event.bb.clear()

        try {
            prepareFlow(event, flow)
            event.op = FLOW_CREATE
        } catch { case t: Throwable =>
            event.op = SKIP
            throw t
        } finally {
            ringBuffer.publish(seq)
        }
        seq
    }

    private def prepareFlow(event: DatapathEvent, flow: Flow): Unit =
        try {
            protocol.prepareFlowCreate(datapathId, supportsMegaflow, flow, event.bb)
        } catch { case t: IndexOutOfBoundsException =>
            if (maybeGrow(event)) {
                prepareFlow(event, flow)
            } else {
                throw t
            }
        }

    private def maybeGrow(event: DatapathEvent): Boolean = {
        val capacity = event.bb.capacity()
        if (capacity >= MAX_BUF_CAPACITY)
            return false
        val newCapacity = capacity * 2
        event.bb = BytesUtil.instance.allocateDirect(newCapacity)
        true
    }
}
