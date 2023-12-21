package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.MidiCIProtocolType
import kotlin.test.*

class MidiCIInitiatorTest {
    @Test
    fun initialState() {
        val mediator = TestCIMediator()
        val initiator = mediator.initiator

        assertEquals(0, initiator.device.manufacturerId)
        assertEquals(19474, initiator.muid)
    }

    @Test
    fun basicRun() {
        val mediator = TestCIMediator()
        val initiator = mediator.initiator
        initiator.sendDiscovery()
        assertEquals(1, initiator.connections.size, "connections.size")
        val responder = mediator.responder
        val conn = initiator.connections[responder.muid]
        assertNotNull(conn, "conn")
        assertEquals(responder.device.manufacturerId, conn.device.manufacturer, "conn.device.manufacturer")
    }
}

