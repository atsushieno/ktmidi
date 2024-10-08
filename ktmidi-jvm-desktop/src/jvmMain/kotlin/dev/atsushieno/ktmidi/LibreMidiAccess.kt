package dev.atsushieno.ktmidi

import dev.atsushieno.ktmidi.LibreMidiAccess.LibreMidiException
import dev.atsushieno.libremidi_javacpp.*
import org.bytedeco.javacpp.*
import java.nio.ByteBuffer
import java.nio.IntBuffer

import dev.atsushieno.libremidi_javacpp.global.libremidi as library

private fun checkReturn(action: ()->Int) {
    val ret = action()
    if (ret != 0)
        throw LibreMidiException(ret)
}

class LibreMidiAccess(private val api: Int) : MidiAccess() {
    companion object {
        private var sequentialIdSerial = 0

        private fun guessPlatform(): DesktopPlatform {
            val os = System.getProperty("os.name")
            return when {
                os.startsWith("windows", true) -> DesktopPlatform.Windows
                os.startsWith("mac", true) -> DesktopPlatform.MacOS
                else -> DesktopPlatform.Linux
            }
        }
        fun create(transportProtocol: Int) = LibreMidiAccess(API.getPlatformDefault(guessPlatform(), transportProtocol))
    }

    class LibreMidiException(msg: String, innerException: Exception?) : Exception(msg, innerException) {
        constructor(errorCode: Int): this("LibreMidi error ($errorCode)", null)
        constructor(msg: String): this(msg, null)
        constructor(innerException: Exception?): this("LibreMidi error", innerException)
    }

    enum class DesktopPlatform {
        Linux,
        Windows,
        MacOS,
    }

    object API {
        val Unspecified = library.UNSPECIFIED
        val CoreMidi = library.COREMIDI
        val AlsaSeq = library.ALSA_SEQ
        val AlsaRaw = library.ALSA_RAW
        val JackMidi = library.JACK_MIDI
        val WindowsMM = library.WINDOWS_MM
        val WindowsUwp = library.WINDOWS_UWP
        val WebMidi = library.WEBMIDI // ktmidi-jvm-desktop wouldn't run on Web platform though
        val PipeWire = library.PIPEWIRE
        val AlsaSeqUmp = library.ALSA_SEQ_UMP
        val AlsaRawUmp = library.ALSA_RAW_UMP
        val CoreMidiUmp = library.COREMIDI_UMP
        val WindowsMidiServices = library.WINDOWS_MIDI_SERVICES
        val Dummy = library.DUMMY

        fun getPlatformDefault(platform: DesktopPlatform, transportProtocol: Int) =
            when(platform) {
                DesktopPlatform.Linux ->
                    if (transportProtocol == MidiTransportProtocol.UMP) AlsaSeqUmp
                    else AlsaSeq
                DesktopPlatform.Windows ->
                    if (transportProtocol == MidiTransportProtocol.UMP) WindowsMidiServices
                    else WindowsUwp
                DesktopPlatform.MacOS ->
                    if (transportProtocol == MidiTransportProtocol.UMP) CoreMidiUmp
                    else CoreMidi
            }
    }

    private val instanceIdSerial = sequentialIdSerial++

    override val supportsUmpTransport = when (api) {
        API.WindowsMidiServices, API.CoreMidiUmp, API.AlsaSeqUmp, API.AlsaRawUmp -> true
        else -> false
    }

    private val observerConfig = libremidi_observer_configuration().also {
        library.libremidi_midi_observer_configuration_init(it)
        it.track_virtual(true)
        it.track_any(true)
    }
    private val apiConfig = libremidi_api_configuration().also {
        checkReturn { library.libremidi_midi_api_configuration_init(it) }
        it.api(api)
    }
    private val observer = libremidi_midi_observer_handle().also {
        checkReturn { library.libremidi_midi_observer_new(observerConfig, apiConfig, it) }
    }

    override val name: String
        get() = "LibreMidi"

    // Ports
    open class LibreMidiPortDetails(
        private val access: LibreMidiAccess,
        override val id: String,
        override val name: String
    ) : MidiPortDetails {
        override val manufacturer = "N/A" // N/A by libremidi
        override val version: String = "N/A" // N/A by libremidi
        override val midiTransportProtocol =
            if (access.supportsUmpTransport) MidiTransportProtocol.UMP
            else MidiTransportProtocol.MIDI1
    }

    class LibreMidiRealInPortDetails(
        private val access: LibreMidiAccess,
        override val id: String,
        override val name: String,
        val port: libremidi_midi_in_port
    ) : LibreMidiPortDetails(access, id, name), AutoCloseable {
        override fun close() {
            checkReturn { library.libremidi_midi_in_port_free(port) }
        }
    }

    class LibreMidiRealOutPortDetails(
        private val access: LibreMidiAccess,
        override val id: String,
        override val name: String,
        val port: libremidi_midi_out_port
    ) : LibreMidiPortDetails(access, id, name), AutoCloseable {
        override fun close() {
            checkReturn { library.libremidi_midi_out_port_free(port) }
        }
    }

    private val cachedInputs = mutableListOf<LibreMidiRealInPortDetails>()
    private val cachedOutputs = mutableListOf<LibreMidiRealOutPortDetails>()

    override val inputs: Iterable<MidiPortDetails>
        get() {
            cachedInputs.forEach { it.close() }
            cachedInputs.clear()

            val list = mutableListOf<LibreMidiRealInPortDetails>()
            val cb = object : Arg2_Pointer_libremidi_midi_in_port() {
                override fun call(ctx: Pointer?, port: libremidi_midi_in_port) {
                    val name = withNameBuffer(1024) { namePtr, size ->
                        checkReturn { library.libremidi_midi_in_port_name(port, namePtr, size) }
                    }
                    val clone = libremidi_midi_in_port()
                    checkReturn { library.libremidi_midi_in_port_clone(port, clone) }
                    list.add(LibreMidiRealInPortDetails(this@LibreMidiAccess, "In_${instanceIdSerial}_${list.size}", name, clone))
                }
            }
            checkReturn { library.libremidi_midi_observer_enumerate_input_ports(observer, Pointer(), cb) }

            cachedInputs.addAll(list)
            return list
        }

    override val outputs: Iterable<MidiPortDetails>
        get() {
            cachedOutputs.forEach { it.close() }
            cachedOutputs.clear()

            val list = mutableListOf<LibreMidiRealOutPortDetails>()
            val cb = object : Arg2_Pointer_libremidi_midi_out_port() {
                override fun call(ctx: Pointer?, port: libremidi_midi_out_port) {
                    val name = withNameBuffer(1024) { namePtr, size ->
                        checkReturn { library.libremidi_midi_out_port_name(port, namePtr, size) }
                    }
                    val clone = libremidi_midi_out_port()
                    checkReturn { library.libremidi_midi_out_port_clone(port, clone) }
                    list.add(LibreMidiRealOutPortDetails(this@LibreMidiAccess, "Out_${instanceIdSerial}_${list.size}", name, clone))
                }
            }
            checkReturn { library.libremidi_midi_observer_enumerate_output_ports(observer, null, cb) }

            cachedOutputs.addAll(list)
            return list
        }

    private fun withNameBuffer(bufferSize: Int, func: (BytePointer, SizeTPointer) -> Unit): String {
        // FIXME: can we indeed limit the name to this size?
        val nameArray = ByteArray(bufferSize)
        val nameBuf = ByteBuffer.wrap(nameArray)
        val namePtr = BytePointer(nameBuf)
        val size = SizeTPointer(0)
        func(namePtr, size)
        namePtr.asByteBuffer().get(nameArray)
        return nameArray.take(size.get().toInt()).toByteArray().decodeToString()
    }

    // Input/Output

    override fun canCreateVirtualPort(context: PortCreatorContext) =
        when(api) {
            API.AlsaSeqUmp, API.CoreMidiUmp, API.WindowsMidiServices -> context.midiProtocol == MidiTransportProtocol.UMP
            else -> context.midiProtocol == MidiTransportProtocol.MIDI1
        }

    @Deprecated("Use canCreateVirtualPort(PortCreatorContext) instead")
    override val canCreateVirtualPort: Boolean
        get() = when(api) {
            API.AlsaSeq, API.CoreMidi, API.AlsaSeqUmp, API.CoreMidiUmp, API.WindowsMidiServices -> true
            else -> false
        }

    private var nextVirtualPortIndex = 0
    override suspend fun createVirtualInputSender(context: PortCreatorContext): MidiOutput {
        val midiConfig = libremidi_midi_configuration().also {
            checkReturn { library.libremidi_midi_configuration_init(it) }
            it.port_name(BytePointer(context.portName))
            it.virtual_port(true)
            it.version(
                if (context.midiProtocol == MidiTransportProtocol.UMP) libremidi_midi_configuration.MIDI2
                else libremidi_midi_configuration.MIDI1)
        }
        val midiOut = libremidi_midi_out_handle()
        checkReturn { library.libremidi_midi_out_new(midiConfig, apiConfig, midiOut) }
        val idName = "VIn_${instanceIdSerial}_${nextVirtualPortIndex++}"
        val portDetails = LibreMidiPortDetails(this, idName, context.portName)

        return LibreMidiOutput(midiOut, portDetails, this)
    }

    override suspend fun createVirtualOutputReceiver(context: PortCreatorContext): MidiInput {
        val dispatcher = InputDispatcher()
        val (ctx, midiConfig) = createMidiConfig(context.portName, dispatcher, true)
        val midiIn = libremidi_midi_in_handle()
        checkReturn { library.libremidi_midi_in_new(midiConfig, apiConfig, midiIn) }
        val idName = "VOut_${instanceIdSerial}_${nextVirtualPortIndex++}"
        val portDetails = LibreMidiPortDetails(this, idName, context.portName)

        return LibreMidiInput(midiIn, portDetails, dispatcher, ctx, this)
    }

    override suspend fun openInput(portId: String): MidiInput {
        val portDetails = inputs.first { it.id == portId } as LibreMidiRealInPortDetails
        val dispatcher = InputDispatcher()
        val (ctx, midiConfig) = createMidiConfig(portId, dispatcher, false)
        midiConfig.in_port(portDetails.port)
        val midiIn = libremidi_midi_in_handle()
        checkReturn { library.libremidi_midi_in_new(midiConfig, apiConfig, midiIn) }
        return LibreMidiInput(midiIn, portDetails, dispatcher, ctx, this)
    }

    override suspend fun openOutput(portId: String): MidiOutput {
        val portDetails = outputs.first { it.id == portId } as LibreMidiRealOutPortDetails
        val midiConfig = libremidi_midi_configuration().also {
            checkReturn { library.libremidi_midi_configuration_init(it) }
            it.out_port(portDetails.port)
            it.version(
                if (portDetails.midiTransportProtocol == MidiTransportProtocol.UMP) libremidi_midi_configuration.MIDI2
                else libremidi_midi_configuration.MIDI1)
        }
        val midiOut = libremidi_midi_out_handle().also {
            checkReturn { library.libremidi_midi_out_new(midiConfig, apiConfig, it) }
        }

        return LibreMidiOutput(midiOut, portDetails, this)
    }

    internal abstract class LibreMidiPort : MidiPort {
        abstract override val details: MidiPortDetails
        abstract override fun close()
    }

    private fun createMidiConfig(name: String, dispatcher: InputDispatcher, virtualPort: Boolean): Pair<Any, libremidi_midi_configuration> {
        lateinit var cb: Any
        val conf = libremidi_midi_configuration().also {
            checkReturn { library.libremidi_midi_configuration_init(it) }
            it.port_name(BytePointer(name))
            if (supportsUmpTransport) {
                it.version(libremidi_midi_configuration.MIDI2)
                val umpCallback = object: libremidi_midi_configuration.Callback_Pointer_IntPointer_long() {
                    override fun call(ctx: Pointer?, data: IntPointer, len: Long) {
                        // It feels a bit awkward, but `data.asBuffer()` returns such an IntBuffer that has only capacity = 1,
                        // so it is useless here. We need to create another BytePointer and its resulting ByteBuffer.
                        val buf = BytePointer(data).capacity(len * 4)
                        val array = ByteArray(len.toInt() * 4)
                        ByteBuffer.wrap(array).put(buf.asByteBuffer())
                        // FIXME: support input timestamp (libremidi needs somewhat tricky code)
                        dispatcher.listener?.onEventReceived(array, 0, len.toInt() * 4, 0)
                    }
                }
                it.on_midi2_message_callback(umpCallback)
                cb = umpCallback
            } else {
                it.version(libremidi_midi_configuration.MIDI1)
                val midi1Callback = object: libremidi_midi_configuration.Callback_Pointer_BytePointer_long() {
                    override fun call(ctx: Pointer?, data: BytePointer, len: Long) {
                        val array = ByteArray(len.toInt())
                        data.get(array)
                        // FIXME: support input timestamp (libremidi needs somewhat tricky code)
                        dispatcher.listener?.onEventReceived(array, 0, len.toInt(), 0)
                    }
                }
                it.on_midi1_message_callback(midi1Callback)
                cb = midi1Callback
            }
            it.virtual_port(virtualPort)
        }
        return Pair(cb, conf)
    }

    class InputDispatcher {
        var listener: OnMidiReceivedEventListener? = null
    }

    private class LibreMidiInput(private val midiIn: libremidi_midi_in_handle, private val portDetails: LibreMidiPortDetails, private val dispatcher: InputDispatcher, private val contextHolder: Any, private val access: LibreMidiAccess) : MidiInput, LibreMidiPort() {
        override var connectionState = MidiPortConnectionState.OPEN // at created state

        override val details: MidiPortDetails = portDetails

        override fun close() {
            connectionState = MidiPortConnectionState.CLOSED
            checkReturn { library.libremidi_midi_in_free(midiIn) }
            if (portDetails is LibreMidiRealInPortDetails)
                checkReturn { library.libremidi_midi_in_port_free(portDetails.port) }
        }

        override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
            dispatcher.listener = listener
        }
    }

    private class LibreMidiOutput(private val midiOut: libremidi_midi_out_handle, private val portDetails: LibreMidiPortDetails, private val access: LibreMidiAccess) : MidiOutput, LibreMidiPort() {
        override var connectionState = MidiPortConnectionState.OPEN // at created state

        override val details: MidiPortDetails = portDetails

        override fun close() {
            connectionState = MidiPortConnectionState.CLOSED
            checkReturn { library.libremidi_midi_out_free(midiOut) }
            if (portDetails is LibreMidiRealOutPortDetails)
                checkReturn { library.libremidi_midi_out_port_free(portDetails.port) }
        }

        override fun send(mevent: ByteArray, offset: Int, length: Int, timestampInNanoseconds: Long) {
            val msg = if (offset > 0 && mevent.size == length) mevent.drop(offset).take(length).toByteArray() else mevent
            if (access.supportsUmpTransport) {
                checkReturn {
                    val byteBuffer = ByteBuffer.allocateDirect(length)
                    byteBuffer.put(msg)
                    byteBuffer.position(0)
                    val intBuffer = byteBuffer.asIntBuffer()
                    library.libremidi_midi_out_schedule_ump(midiOut, timestampInNanoseconds, intBuffer, (msg.size / 4).toLong())
                }
            }
            else
                checkReturn { library.libremidi_midi_out_schedule_message(midiOut, timestampInNanoseconds, msg, length.toLong()) }
        }
    }
}
