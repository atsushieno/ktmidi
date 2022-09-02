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
        assertEquals(MidiCIConstants.Midi1ProtocolTypeInfo, initiator.currentMidiProtocol)
        assertEquals(false, initiator.protocolTested)
        assertEquals(0, initiator.device.manufacturer)
        assertEquals(0x60, initiator.authorityLevel)
        assertEquals(19474, initiator.muid)
        assertContentEquals(MidiCIConstants.Midi2ThenMidi1Protocols, initiator.preferredProtocols)
    }

    @Test
    fun basicRun() {
        val mediator = TestCIMediator()
        val initiator = mediator.initiator
        initiator.sendDiscovery()
        assertTrue(initiator.protocolTested)
        assertEquals(MidiCIConstants.Midi2ProtocolTypeInfo, initiator.currentMidiProtocol)
        val responder = mediator.responder
        assertEquals(MidiCIProtocolType.MIDI2, responder.currentMidiProtocol.type.toInt())
    }
}

