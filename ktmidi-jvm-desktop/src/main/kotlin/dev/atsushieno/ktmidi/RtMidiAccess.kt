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

    override val canCreateVirtualPort: Boolean
        get() = when(library.rtmidi_out_get_current_api(library.rtmidi_out_create_default())) {
            RtmidiLibrary.RtMidiApi.RTMIDI_API_LINUX_ALSA,
            RtmidiLibrary.RtMidiApi.RTMIDI_API_MACOSX_CORE -> true
            else -> false
        }

    override suspend fun createVirtualInputSender(context: PortCreatorContext) =
        RtMidiVirtualOutput(context)

    override suspend fun createVirtualOutputReceiver(context: PortCreatorContext) =
        RtMidiVirtualInput(context)

    override suspend fun openInputAsync(portId: String): MidiInput =
        RtMidiInput(portId.toInt())

    override suspend fun openOutputAsync(portId: String): MidiOutput =
        RtMidiOutput(portId.toInt())

    abstract class RtMidiPort : MidiPort {
        override var midiProtocol: Int = 0 // unspecified
        abstract override val details: MidiPortDetails
        abstract override fun close()
    }

    private class RtMidiInputHandler(private val rtmidi: RtMidiWrapper) {
        private var listener: OnMidiReceivedEventListener? = null

        fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
            this.listener = listener
        }

        private fun onRtMidiMessage(timestamp: Double, message: Pointer, messageSize: NativeSize) {
            listener?.onEventReceived(message.getByteArray(0, messageSize.toInt()), 0, messageSize.toInt(), (timestamp * 1_000_000_000).toLong())
        }

        init {
            library.rtmidi_in_set_callback(rtmidi,
                { timestamp, message, messageSize, _ -> onRtMidiMessage(timestamp, message, messageSize) },
                Pointer.NULL)
        }
    }

    class RtMidiInput(private val portIndex: Int) : MidiInput, RtMidiPort() {
        private val rtmidi = library.rtmidi_in_create_default()
        override var connectionState = MidiPortConnectionState.OPEN // at created state
        private val inputHandler : RtMidiInputHandler = RtMidiInputHandler(rtmidi)

        override val details: MidiPortDetails
            get() = RtMidiPortDetails(portIndex, library.rtmidi_get_port_name(rtmidi, portIndex))

        override fun close() {
            connectionState = MidiPortConnectionState.CLOSED
            library.rtmidi_close_port(rtmidi)
        }

        override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
            inputHandler.setMessageReceivedListener(listener)
        }

        init {
            library.rtmidi_open_port(rtmidi, portIndex, "ktmidi port $portIndex")
        }
    }

    class RtMidiOutput(private val portIndex: Int) : MidiOutput, RtMidiPort() {
        private val rtmidi = library.rtmidi_out_create_default()
        override var connectionState = MidiPortConnectionState.OPEN // at created state

        override val details: MidiPortDetails
            get() = RtMidiPortDetails(portIndex, library.rtmidi_get_port_name(rtmidi, portIndex))

        override fun close() {
            connectionState = MidiPortConnectionState.CLOSED
            library.rtmidi_close_port(rtmidi)
        }

        override fun send(mevent: ByteArray, offset: Int, length: Int, timestamp: Long) {
            library.rtmidi_out_send_message(rtmidi, if (offset > 0) mevent.drop(offset).take(length).toByteArray() else mevent, length)
        }

        init {
            library.rtmidi_open_port(rtmidi, portIndex, "ktmidi port $portIndex")
        }
    }

    // Virtual ports

    class RtMidiVirtualPortDetails(context: PortCreatorContext) : MidiPortDetails {
        override val id: String
        override val manufacturer: String
        override val name: String
        override val version: String

        init {
            id = context.portName
            name = context.portName
            manufacturer = context.manufacturer
            version = context.version
        }
    }

    abstract class RtMidiVirtualPort(context: PortCreatorContext) : MidiPort {
        private val detailsImpl: MidiPortDetails

        override val details: MidiPortDetails
            get() = detailsImpl

        override var midiProtocol: Int = 0

        init {
            detailsImpl = RtMidiVirtualPortDetails(context)
        }
    }

    class RtMidiVirtualInput(context: PortCreatorContext) : MidiInput, RtMidiVirtualPort(context) {
        private val rtmidi = library.rtmidi_in_create_default()
        override var connectionState = MidiPortConnectionState.OPEN // at created state
        private val inputHandler : RtMidiInputHandler = RtMidiInputHandler(rtmidi)

        override fun close() {
            connectionState = MidiPortConnectionState.CLOSED
            library.rtmidi_close_port(rtmidi)
        }

        override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
            inputHandler.setMessageReceivedListener(listener)
        }

        init {
            library.rtmidi_open_virtual_port(rtmidi, context.portName)
        }
    }

    class RtMidiVirtualOutput(context: PortCreatorContext) : MidiOutput, RtMidiVirtualPort(context) {
        private val rtmidi = library.rtmidi_out_create_default()
        override var connectionState = MidiPortConnectionState.OPEN // at created state

        override fun close() {
            connectionState = MidiPortConnectionState.CLOSED
            library.rtmidi_close_port(rtmidi)
        }

        override fun send(mevent: ByteArray, offset: Int, length: Int, timestamp: Long) {
            library.rtmidi_out_send_message(rtmidi, if (offset > 0) mevent.drop(offset).take(length).toByteArray() else mevent, length)
        }

        init {
            library.rtmidi_open_virtual_port(rtmidi, context.portName)
        }
    }
}
