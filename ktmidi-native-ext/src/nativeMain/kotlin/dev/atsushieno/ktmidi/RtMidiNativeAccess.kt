package dev.atsushieno.ktmidi

import dev.atsushieno.rtmidicinterop.*
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.readValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.usePinned
import platform.posix.size_t

class RtMidiNativeAccess() : MidiAccess() {
    companion object {
        internal fun getPortName(rtmidi: RtMidiPtr?, index: UInt) : String {
            memScoped {
                val lenVar = alloc<Int>(0)
                if (rtmidi_get_port_name(rtmidi, index, null, lenVar.ptr.reinterpret()) < 0)
                    return "" // error
                val nameLength = lenVar.ptr[0]
                val nameBuf = ByteArray(nameLength) // rtmidi returns the length with the null-terminator.
                nameBuf.usePinned { nameBufPtr ->
                    if (rtmidi_get_port_name(rtmidi, index, nameBufPtr.addressOf(0).reinterpret(), lenVar.ptr.reinterpret()) < 0)
                        return "" // error
                    return nameBuf.decodeToString()
                }
            }
        }
    }

    // Ports

    private class RtMidiPortDetails(portIndex: UInt, override val name: String) : MidiPortDetails {
        override val id: String = portIndex.toString()
        override val manufacturer = "" // N/A by rtmidi
        override val version: String = "" // N/A by rtmidi
    }

    override val inputs: Iterable<MidiPortDetails>
        get() {
            val rtmidi = rtmidi_in_create_default()
            return sequence {
                for (i in 0u until rtmidi_get_port_count(rtmidi))
                    yield(RtMidiPortDetails(i, getPortName(rtmidi, i)))
            }.asIterable()
        }

    override val outputs: Iterable<MidiPortDetails>
        get() {
            val rtmidi = rtmidi_out_create_default()
            return sequence {
                for (i in 0u until rtmidi_get_port_count(rtmidi))
                    yield(RtMidiPortDetails(i, getPortName(rtmidi, i)))
            }.asIterable()
        }

    // Input/Output

    override val canCreateVirtualPort: Boolean
        get() = when(rtmidi_out_get_current_api(rtmidi_out_create_default())) {
            RtMidiApi.RTMIDI_API_LINUX_ALSA,
            RtMidiApi.RTMIDI_API_MACOSX_CORE -> true
            else -> false
        }

    override suspend fun createVirtualInputSender(context: PortCreatorContext): MidiOutput =
        RtMidiVirtualOutput(context)

    override suspend fun createVirtualOutputReceiver(context: PortCreatorContext): MidiInput =
        RtMidiVirtualInput(context)

    override suspend fun openInputAsync(portId: String): MidiInput =
        RtMidiInput(portId.toUInt())

    override suspend fun openOutputAsync(portId: String): MidiOutput =
        RtMidiOutput(portId.toUInt())

    internal abstract class RtMidiPort : MidiPort {
        override var midiProtocol: Int = 0 // unspecified
        abstract override val details: MidiPortDetails
        abstract override fun close()
    }

    internal class RtMidiInputHandler(rtmidi: RtMidiInPtr?) {
        private val stableRefToThis = StableRef.create(this)
        private var listener: OnMidiReceivedEventListener? = null

        companion object {
            internal fun callOnRtMidiMessage(instance: COpaquePointer, timestamp: Double, message: CPointer<UByteVar>?, messageSize: size_t)
                = instance.asStableRef<RtMidiInputHandler>().get().onRtMidiMessage(timestamp, message, messageSize)
        }

        fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
            this.listener = listener
        }

        private fun onRtMidiMessage(timestamp: Double, message: CPointer<UByteVar>?, messageSize: size_t) {
            listener?.onEventReceived(message?.readBytes(messageSize.toInt()) ?: byteArrayOf(), 0, messageSize.toInt(), (timestamp * 1_000_000_000).toLong())
        }

        init {
            // We cannot simply call onRtMidiMessage() here, due to some undocumented staticCFunction() requirements its lambda can point only to static functions.
            // You will get compilation error otherwise (or worse, depending on how you create the lambda, it fails to report the compilation error and crashes at runtime).
            // Therefore, we created callOnRtMidiMessage() as in the static context and pass "this" to it.
            // Since it has to go through C interop layer, we create and keep a StableRef to "this" through this' lifetime.
            rtmidi_in_set_callback(rtmidi, staticCFunction { timestamp: Double, message: CPointer<UByteVar>?, messageSize: size_t, userData: COpaquePointer? -> callOnRtMidiMessage(userData!!, timestamp, message, messageSize) }, stableRefToThis.asCPointer())
        }
    }

    internal class RtMidiInput(private val portIndex: UInt) : MidiInput, RtMidiPort() {
        private val rtmidi = rtmidi_in_create_default()
        override var connectionState = MidiPortConnectionState.OPEN // at created state
        private val inputHandler : RtMidiInputHandler = RtMidiInputHandler(rtmidi)

        override val details: MidiPortDetails
            get() = RtMidiPortDetails(portIndex, getPortName(rtmidi, portIndex))

        override fun close() {
            connectionState = MidiPortConnectionState.CLOSED
            rtmidi_close_port(rtmidi)
        }

        override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
            inputHandler.setMessageReceivedListener(listener)
        }

        init {
            rtmidi_open_port(rtmidi, portIndex, "ktmidi port $portIndex")
        }
    }

    internal class RtMidiOutput(private val portIndex: UInt) : MidiOutput, RtMidiPort() {
        private val rtmidi = rtmidi_out_create_default()
        override var connectionState = MidiPortConnectionState.OPEN // at created state

        override val details: MidiPortDetails
            get() = RtMidiPortDetails(portIndex, getPortName(rtmidi, portIndex))

        override fun close() {
            connectionState = MidiPortConnectionState.CLOSED
            rtmidi_close_port(rtmidi)
        }

        override fun send(mevent: ByteArray, offset: Int, length: Int, timestampInNanoseconds: Long) {
            mevent.usePinned { pinned ->
                rtmidi_out_send_message(rtmidi, pinned.addressOf(offset).reinterpret(), length)
            }
        }

        init {
            rtmidi_open_port(rtmidi, portIndex, "ktmidi port $portIndex")
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
        private val rtmidi = rtmidi_in_create_default()
        override var connectionState = MidiPortConnectionState.OPEN // at created state
        private val inputHandler : RtMidiInputHandler = RtMidiInputHandler(rtmidi)

        override fun close() {
            connectionState = MidiPortConnectionState.CLOSED
            rtmidi_close_port(rtmidi)
        }

        override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
            inputHandler.setMessageReceivedListener(listener)
        }

        init {
            rtmidi_open_virtual_port(rtmidi, context.portName)
        }
    }

    internal class RtMidiVirtualOutput(context: PortCreatorContext) : MidiOutput, RtMidiVirtualPort(context) {
        private val rtmidi = rtmidi_out_create_default()
        override var connectionState = MidiPortConnectionState.OPEN // at created state

        override fun close() {
            connectionState = MidiPortConnectionState.CLOSED
            rtmidi_close_port(rtmidi)
        }

        override fun send(mevent: ByteArray, offset: Int, length: Int, timestampInNanoseconds: Long) {
            mevent.usePinned { pinned ->
                rtmidi_out_send_message(rtmidi, pinned.addressOf(offset).reinterpret(), length)
            }
        }

        init {
            rtmidi_open_virtual_port(rtmidi, context.portName)
        }
    }
}
