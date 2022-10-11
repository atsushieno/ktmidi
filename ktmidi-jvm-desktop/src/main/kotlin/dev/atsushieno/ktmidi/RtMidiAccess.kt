package dev.atsushieno.ktmidi

import java.nio.ByteBuffer
import java.nio.IntBuffer
import com.ochafik.lang.jnaerator.runtime.NativeSize
import com.sun.jna.Pointer
import dev.atsushieno.rtmidijna.RtMidiWrapper
import dev.atsushieno.rtmidijna.RtmidiLibrary
import dev.atsushieno.rtmidijna.RtmidiLibrary.RtMidiCCallback
import kotlinx.coroutines.yield

class RtMidiAccess() : MidiAccess() {
    companion object {
        private val library = RtmidiLibrary.INSTANCE

        internal fun getPortName(rtmidi: RtMidiWrapper, index: Int) : String {
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
            RtmidiLibrary.RtMidiApi.RTMIDI_API_LINUX_ALSA,
            RtmidiLibrary.RtMidiApi.RTMIDI_API_MACOSX_CORE -> true
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

    internal class RtMidiInputHandler(private val rtmidi: RtMidiWrapper) {
        private var listener: OnMidiReceivedEventListener? = null

        fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
            this.listener = listener
        }

        private fun onRtMidiMessage(timestamp: Double, message: Pointer, messageSize: NativeSize) {
            listener?.onEventReceived(message.getByteArray(0, messageSize.toInt()), 0, messageSize.toInt(), (timestamp * 1_000_000_000).toLong())
        }

        class RtMidiAccessInputCallback(val owner: RtMidiInputHandler) : RtMidiCCallback {

            override fun apply(timeStamp: Double, message: Pointer?, messageSize: NativeSize?, userData: Pointer?) {
                owner.onRtMidiMessage(timeStamp, message!!, messageSize!!)
            }
        }

        private val callback = RtMidiAccessInputCallback(this)

        init {
            library.rtmidi_in_set_callback(rtmidi,
                callback,
                Pointer.NULL)
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

    internal abstract class RtMidiVirtualPort(context: PortCreatorContext) : MidiPort {
        private val detailsImpl: MidiPortDetails

        override val details: MidiPortDetails
            get() = detailsImpl

        override var midiProtocol: Int = 0

        init {
            detailsImpl = RtMidiVirtualPortDetails(context)
        }
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
