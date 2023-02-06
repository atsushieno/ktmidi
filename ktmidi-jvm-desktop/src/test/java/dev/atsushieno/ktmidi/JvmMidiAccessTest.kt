package dev.atsushieno.ktmidi

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class JvmMidiAccessTest {

    /**
     * Every JVM has Gervill as a synthesizer with midi inputs to send data to and a Real Time Sequencer with midi
     * outputs and inputs that can be used to retrieve data from or to send data to. These ports can be used for
     * testing.
     */
    @Test
    fun testMidiPorts() = runTest {
        val midiAccess = JvmMidiAccess()

        assertEquals("JVM", midiAccess.name)

        val defaultSequencerInputDetails = midiAccess.inputs.find { it.name == "Real Time Sequencer" }
        val defaultSequencerOutputDetails = midiAccess.outputs.find { it.name == "Real Time Sequencer" }

        assertNotNull(defaultSequencerInputDetails)
        assertNotNull(defaultSequencerOutputDetails)

        val input = midiAccess.openInput(defaultSequencerInputDetails.id)
        val output = midiAccess.openOutput(defaultSequencerOutputDetails.id)

        // currently this is always open
        assertEquals(MidiPortConnectionState.OPEN, input.connectionState)
        assertEquals(MidiPortConnectionState.OPEN, output.connectionState)

        val inputEvents = mutableListOf<Sequence<MidiEvent>>()
        // collect inputs from output? How does the real time sequencer route data?
        input.setMessageReceivedListener(object : OnMidiReceivedEventListener {
            override fun onEventReceived(data: ByteArray, start: Int, length: Int, timestampInNanoseconds: Long) {
                val events = MidiEvent.convert(data, start, length)
                inputEvents.add(events)
            }
        })

        // CC0
        val message = arrayOf(176.toByte(), 1, 127)
        output.send(message.toByteArray(), 0, 3, 0)

        input.close()
        output.close()

        // connectionState should represent the underlying port state? TODO
        //assertEquals(MidiPortConnectionState.CLOSED, input.connectionState)
        //assertEquals(MidiPortConnectionState.CLOSED, output.connectionState)
    }

}
