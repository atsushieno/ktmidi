package dev.atsushieno.ktmidi

import dev.atsushieno.rtmidi_javacpp.RtMidiCCallback
import org.bytedeco.javacpp.BytePointer
import java.nio.ByteBuffer
import java.nio.IntBuffer
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacpp.Pointer

import dev.atsushieno.rtmidi_javacpp.global.RtMidi as library

class RtMidiAccess() : MidiAccess() {
    companion object {

        internal fun getPortName(rtmidi: Pointer, index: Int) : String {
            val lenArr = intArrayOf(0)
            val len = IntBuffer.wrap(lenArr)
            if (library.rtmidi_get_port_name(rtmidi, index, null, len) < 0)
                return "" // error
            val nameBuf = ByteArray(len.get(0) - 1) // rtmidi returns the length with the null-terminator.
            if (library.rtmidi_get_port_name(rtmidi, index, ByteBuffer.wrap(nameBuf), len) < 0)
                return "" // error
            return String(nameBuf)
        }
    }

    override val name: String
        get() = "RtMidi"

    // Ports

    private class RtMidiPortDetails(portIndex: Int, override val name: String) : MidiPortDetails {
        override val id: String = portIndex.toString()
        override val manufacturer = "" // N/A by rtmidi
        override val version: String = "" // N/A by rtmidi
    }

    override val inputs: Iterable<MidiPortDetails>
        get() {
            val rtmidi = library.rtmidi_in_create_default()
            return sequence {
                for (i in 0 until library.rtmidi_get_port_count(rtmidi))
                    yield(RtMidiPortDetails(i, getPortName(rtmidi, i)))
            }.asIterable()
        }

    override val outputs: Iterable<MidiPortDetails>
        get() {
            val rtmidi = library.rtmidi_out_create_default()
            return sequence {
                for (i in 0 until library.rtmidi_get_port_count(rtmidi))
                    yield(RtMidiPortDetails(i, getPortName(rtmidi, i)))
            }.asIterable()
        }

    // Input/Output

    override val canCreateVirtualPort: Boolean
        get() = when(library.rtmidi_out_get_current_api(library.rtmidi_out_create_default())) {
            library.RTMIDI_API_LINUX_ALSA,
            library.RTMIDI_API_MACOSX_CORE -> true
            else -> false
        }

    override suspend fun createVirtualInputSender(context: PortCreatorContext): MidiOutput =
        RtMidiVirtualOutput(context)

    override suspend fun createVirtualOutputReceiver(context: PortCreatorContext): MidiInput =
        RtMidiVirtualInput(context)

    override suspend fun openInput(portId: String): MidiInput =
        RtMidiInput(portId.toInt())

    override suspend fun openOutput(portId: String): MidiOutput =
        RtMidiOutput(portId.toInt())

    internal abstract class RtMidiPort : MidiPort {
        override var midiProtocol: Int = 0 // unspecified
        abstract override val details: MidiPortDetails
        abstract override fun close()
    }

    internal class RtMidiInputHandler(rtmidi: Pointer/*RtMidiInPtr*/) {
        private var listener: OnMidiReceivedEventListener? = null

        fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
            this.listener = listener
        }

        private fun onRtMidiMessage(timestamp: Double, message: Pointer, messageSize: Long) {
            val array = ByteArray(messageSize.toInt())
            (message as BytePointer).get(array)
            listener?.onEventReceived(array, 0, messageSize.toInt(), (timestamp * 1_000_000_000).toLong())
        }

        class RtMidiAccessInputCallback(val owner: RtMidiInputHandler) : RtMidiCCallback() {

            override fun call(timeStamp: Double, message: BytePointer?, messageSize: Long, userData: Pointer?) {
                owner.onRtMidiMessage(timeStamp, message!!, messageSize)
            }
        }

        private val callback = RtMidiAccessInputCallback(this)

        init {
            library.rtmidi_in_set_callback(rtmidi,
                callback,
                null)
            library.rtmidi_in_ignore_types(
                rtmidi,
                false,
                true,
                true,
            )
        }
    }

    internal class RtMidiInput(private val portIndex: Int) : MidiInput, RtMidiPort() {
        private val rtmidi = library.rtmidi_in_create_default()
        override var connectionState = MidiPortConnectionState.OPEN // at created state
        private val inputHandler : RtMidiInputHandler = RtMidiInputHandler(rtmidi)

        override val details: MidiPortDetails
            get() = RtMidiPortDetails(portIndex, getPortName(rtmidi, portIndex))

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

    internal class RtMidiOutput(private val portIndex: Int) : MidiOutput, RtMidiPort() {
        private val rtmidi = library.rtmidi_out_create_default()
        override var connectionState = MidiPortConnectionState.OPEN // at created state

        override val details: MidiPortDetails
            get() = RtMidiPortDetails(portIndex, getPortName(rtmidi, portIndex))

        override fun close() {
            connectionState = MidiPortConnectionState.CLOSED
            library.rtmidi_close_port(rtmidi)
        }

        override fun send(mevent: ByteArray, offset: Int, length: Int, timestampInNanoseconds: Long) {
            library.rtmidi_out_send_message(rtmidi, if (offset > 0) mevent.drop(offset).take(length).toByteArray() else mevent, length)
        }

        init {
            library.rtmidi_open_port(rtmidi, portIndex, "ktmidi port $portIndex")
        }
    }

    // Virtual ports

    internal class RtMidiVirtualPortDetails(context: PortCreatorContext) : MidiPortDetails {
        override val id: String = context.portName
        override val manufacturer: String = context.manufacturer
        override val name: String = context.portName
        override val version: String = context.version
    }

    internal abstract class RtMidiVirtualPort(context: PortCreatorContext) : MidiPort {
        private val detailsImpl: MidiPortDetails = RtMidiVirtualPortDetails(context)

        override val details: MidiPortDetails
            get() = detailsImpl

        override var midiProtocol: Int = 0
    }

    internal class RtMidiVirtualInput(context: PortCreatorContext) : MidiInput, RtMidiVirtualPort(context) {
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

    internal class RtMidiVirtualOutput(context: PortCreatorContext) : MidiOutput, RtMidiVirtualPort(context) {
        private val rtmidi = library.rtmidi_out_create_default()
        override var connectionState = MidiPortConnectionState.OPEN // at created state

        override fun close() {
            connectionState = MidiPortConnectionState.CLOSED
            library.rtmidi_close_port(rtmidi)
        }

        override fun send(mevent: ByteArray, offset: Int, length: Int, timestampInNanoseconds: Long) {
            library.rtmidi_out_send_message(rtmidi, if (offset > 0) mevent.drop(offset).take(length).toByteArray() else mevent, length)
        }

        init {
            library.rtmidi_open_virtual_port(rtmidi, context.portName)
        }
    }
}
