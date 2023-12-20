package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.MidiCIProtocolType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MidiCIInitiatorTest {
    @Test
    fun initialState() {
        val mediator = TestCIMediator()
        val initiator = mediator.initiator

        assertEquals(MidiCIInitiatorState.Initial, initiator.state)
        assertEquals(0, initiator.device.manufacturerId)
        assertEquals(19474, initiator.muid)
    }

    @Test
    fun basicRun() {
        val mediator = TestCIMediator()
        val initiator = mediator.initiator
        initiator.sendDiscovery()
        assertEquals(MidiCIInitiatorState.DISCOVERED, initiator.state)
        val responder = mediator.responder
        //assertEquals(MidiCIProtocolType.MIDI2, responder.currentMidiProtocol.type.toInt())
    }
}

