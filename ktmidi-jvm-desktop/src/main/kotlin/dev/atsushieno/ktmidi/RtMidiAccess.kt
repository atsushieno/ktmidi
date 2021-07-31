package dev.atsushieno.ktmidi

import com.ochafik.lang.jnaerator.runtime.NativeSize
import com.sun.jna.Native
import com.sun.jna.Pointer
import dev.atsushieno.rtmidijna.RtMidiWrapper
import dev.atsushieno.rtmidijna.RtmidiLibrary
import kotlinx.coroutines.yield

class RtMidiAccess() : MidiAccess() {
    companion object {
        private val library = RtmidiLibrary.INSTANCE
    }

    // Ports

    class RtMidiPortDetails(portIndex: Int, override val name: String) : MidiPortDetails {
        override val id: String = portIndex.toString()
        override val manufacturer = "" // N/A by rtmidi
        override val version: String = "" // N/A by rtmidi
    }

    override val inputs: Iterable<MidiPortDetails>
        get() {
            val rtmidi = library.rtmidi_in_create_default()
            return sequence {
                for (i in 0 until library.rtmidi_get_port_count(rtmidi))
                    yield(RtMidiPortDetails(i, library.rtmidi_get_port_name(rtmidi, i)))
            }.asIterable()
        }

    override val outputs: Iterable<MidiPortDetails>
        get() {
            val rtmidi = library.rtmidi_out_create_default()
            return sequence {
                for (i in 0 until library.rtmidi_get_port_count(rtmidi))
                    yield(RtMidiPortDetails(i, library.rtmidi_get_port_name(rtmidi, i)))
            }.asIterable()
        }

    // Input/Output

    override suspend fun openInputAsync(portId: String): MidiInput =
        RtMidiInput(portId.toInt())

    override suspend fun openOutputAsync(portId: String): MidiOutput =
        RtMidiOutput(portId.toInt())

    abstract class RtMidiPort : MidiPort {
        override val connectionState = MidiPortConnectionState.OPEN // at created state
        override var midiProtocol: Int = 0 // unspecified
        abstract override val details: MidiPortDetails
        abstract override fun close()
    }

    class RtMidiInput(private val portIndex: Int) : MidiInput, RtMidiPort() {
        private val rtmidi = library.rtmidi_in_create_default()

        override val details: MidiPortDetails
            get() = RtMidiPortDetails(portIndex, library.rtmidi_get_port_name(rtmidi, portIndex))

        override fun close() {
            library.rtmidi_close_port(rtmidi)
        }

        private var listener: OnMidiReceivedEventListener? = null

        override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
            this.listener = listener
        }

        private fun onRtMidiMessage(timestamp: Double, message: Pointer, messageSize: NativeSize, userData: Pointer) {
            listener?.onEventReceived(message.getByteArray(0, messageSize.toInt()), 0, messageSize.toInt(), (timestamp * 1_000_000_000).toLong())
        }

        init {
            library.rtmidi_in_set_callback(rtmidi,
                { timestamp, message, messageSize, userData -> onRtMidiMessage(timestamp, message, messageSize, userData) },
                Pointer.NULL)
        }
    }

    class RtMidiOutput(private val portIndex: Int) : MidiOutput, RtMidiPort() {
        private val rtmidi = library.rtmidi_out_create_default()

        override val details: MidiPortDetails
            get() = RtMidiPortDetails(portIndex, library.rtmidi_get_port_name(rtmidi, portIndex))

        override fun close() {
            library.rtmidi_close_port(rtmidi)
        }

        override fun send(mevent: ByteArray, offset: Int, length: Int, timestamp: Long) {
            library.rtmidi_out_send_message(rtmidi, if (offset > 0) mevent.drop(offset).take(length).toByteArray() else mevent, length)
        }
    }
}
