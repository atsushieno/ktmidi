package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.EmptyMidiAccess
import dev.atsushieno.ktmidi.MidiCIProtocolType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MidiCIInitiatorTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun initialState() = runTest {
        val access = EmptyMidiAccess()
        val input = access.openInputAsync(access.inputs.first().id)
        val output = access.openOutputAsync(access.outputs.first().id)
        val connector = MidiCIInitiator(input, output, DeviceDetails(), MidiCIAuthorityLevelBasis.NodeServer, 37564)

        assertEquals(MidiCIInitiatorState.Initial, connector.state)
        assertEquals(MidiCIProtocolType.MIDI1, connector.midiProtocol)
        assertEquals(false, connector.protocolTested)
        assertEquals(0, connector.device.manufacturer)
        assertEquals(0x60, connector.authorityLevel)
        assertEquals(37564, connector.muid)
        assertContentEquals(MidiCIConstants.Midi2ThenMidi1Protocols, connector.preferredProtocols)
    }
}
