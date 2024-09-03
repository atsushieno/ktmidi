package dev.atsushieno.ktmidi

import dev.atsushieno.ktmidi.LibreMidiAccess.LibreMidiException
import dev.atsushieno.libremidi_javacpp.*
import org.bytedeco.javacpp.*
import java.nio.ByteBuffer

import dev.atsushieno.libremidi_javacpp.global.libremidi as library

private fun checkReturn(action: ()->Int) {
    val ret = action()
    if (ret != 0)
        throw LibreMidiException(ret)
}

class LibreMidiAccess(private val api: Int) : MidiAccess() {
    companion object {
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

    override val supportsUmpTransport = when (api) {
        API.WindowsMidiServices, API.CoreMidiUmp, API.AlsaSeqUmp, API.AlsaRawUmp -> true
        else -> false
    }

    private val observerConfig = libremidi_observer_configuration().also {
        library.libremidi_midi_observer_configuration_init(it)
        it.track_virtual(true)
        it.track_any(true)
    }
    val apiConfig = libremidi_api_configuration().also {
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
            library.libremidi_midi_in_port_free(port)
        }
    }

    class LibreMidiRealOutPortDetails(
        private val access: LibreMidiAccess,
        override val id: String,
        override val name: String,
        val port: libremidi_midi_out_port
    ) : LibreMidiPortDetails(access, id, name), AutoCloseable {
        override fun close() {
            library.libremidi_midi_out_port_free(port)
        }
    }

    override val inputs: Iterable<MidiPortDetails>
        get() {
            val list = mutableListOf<MidiPortDetails>()
            val cb = object : Arg2_Pointer_libremidi_midi_in_port() {
                override fun call(ctx: Pointer?, port: libremidi_midi_in_port) {
                    val name = withNameBuffer(1024) { namePtr, size ->
                        checkReturn { library.libremidi_midi_in_port_name(port, namePtr, size) }
                    }
                    val clone = libremidi_midi_in_port()
                    library.libremidi_midi_in_port_clone(port, clone)
                    list.add(LibreMidiRealInPortDetails(this@LibreMidiAccess, "In_${list.size}", name, clone))
                }
            }
            checkReturn { library.libremidi_midi_observer_enumerate_input_ports(observer, Pointer(), cb) }
            return list
        }

    override val outputs: Iterable<MidiPortDetails>
        get() {
            val list = mutableListOf<MidiPortDetails>()
            val cb = object : Arg2_Pointer_libremidi_midi_out_port() {
                override fun call(ctx: Pointer?, port: libremidi_midi_out_port) {
                    val name = withNameBuffer(1024) { namePtr, size ->
                        checkReturn { library.libremidi_midi_out_port_name(port, namePtr, size) }
                    }
                    val clone = libremidi_midi_out_port()
                    library.libremidi_midi_out_port_clone(port, clone)
                    list.add(LibreMidiRealOutPortDetails(this@LibreMidiAccess, "Out_${list.size}", name, clone))
                }
            }
            checkReturn { library.libremidi_midi_observer_enumerate_output_ports(observer, null, cb) }
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

    override val canCreateVirtualPort: Boolean
        get() = when(api) {
            API.AlsaSeq, API.CoreMidi, API.AlsaSeqUmp, API.CoreMidiUmp, API.WindowsMidiServices -> true
            else -> false
        }

    private var nextVirtualPortIndex = 0
    override suspend fun createVirtualInputSender(context: PortCreatorContext): MidiOutput {
        val midiConfig = libremidi_midi_configuration().also {
            it.port_name(BytePointer(context.portName))
            checkReturn { library.libremidi_midi_configuration_init(it) }
            it.virtual_port(true)
        }
        val midiOut = libremidi_midi_out_handle().also {
            //checkReturn { library.libremidi_midi_out_new(midiConfig, apiConfig, it) }
        }
        val idName = "VIn_${nextVirtualPortIndex++}"
        val portDetails = LibreMidiPortDetails(this, idName, context.portName)

        return LibreMidiOutput(midiOut, portDetails, this)
    }

    override suspend fun createVirtualOutputReceiver(context: PortCreatorContext): MidiInput {
        val dispatcher = InputDispatcher()
        val midiConfig = createMidiConfig(context.portName, dispatcher, true)
        val midiIn = libremidi_midi_in_handle().also {
            checkReturn { library.libremidi_midi_in_new(midiConfig, apiConfig, it) }
        }
        val idName = "VOut_${nextVirtualPortIndex++}"
        val portDetails = LibreMidiPortDetails(this, idName, context.portName)

        return LibreMidiInput(midiIn, portDetails, dispatcher, this)
    }

    override suspend fun openInput(portId: String): MidiInput {
        val portDetails = inputs.first { it.id == portId } as LibreMidiRealInPortDetails
        val dispatcher = InputDispatcher()
        val midiConfig = createMidiConfig(portId, dispatcher, false)
        val midiIn = libremidi_midi_in_handle().also {
            midiConfig.in_port(portDetails.port)
            checkReturn { library.libremidi_midi_in_new(midiConfig, apiConfig, it) }
        }
        return LibreMidiInput(midiIn, portDetails, dispatcher, this)
    }

    override suspend fun openOutput(portId: String): MidiOutput {
        val portDetails = outputs.first { it.id == portId } as LibreMidiRealOutPortDetails
        val midiConfig = libremidi_midi_configuration().also {
            checkReturn { library.libremidi_midi_configuration_init(it) }
            it.out_port(portDetails.port)
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

    private fun createMidiConfig(name: String, dispatcher: InputDispatcher, virtualPort: Boolean) = libremidi_midi_configuration().also {
        checkReturn { library.libremidi_midi_configuration_init(it) }
        it.port_name(BytePointer(name))
        if (supportsUmpTransport) {
            val umpCallback = object: libremidi_midi_configuration.Callback_Pointer_IntPointer_long() {
                override fun call(ctx: Pointer?, data: IntPointer, len: Long) {
                    val array = ByteArray(len.toInt())
                    BytePointer(data).get(array)
                    // FIXME: support input timestamp (libremidi needs somewhat tricky code)
                    dispatcher.listener?.onEventReceived(array, 0, len.toInt(), 0)
                }
            }
            it.on_midi2_message_callback(umpCallback)
        } else {
            val midi1Callback = object: libremidi_midi_configuration.Callback_Pointer_BytePointer_long() {
                override fun call(ctx: Pointer?, data: BytePointer, len: Long) {
                    val array = ByteArray(len.toInt())
                    data.get(array)
                    // FIXME: support input timestamp (libremidi needs somewhat tricky code)
                    dispatcher.listener?.onEventReceived(array, 0, len.toInt(), 0)
                }
            }
            it.on_midi1_message_callback(midi1Callback)
        }
        it.virtual_port(virtualPort)
    }

    class InputDispatcher {
        var listener: OnMidiReceivedEventListener? = null
    }

    private class LibreMidiInput(private val midiIn: libremidi_midi_in_handle, private val portDetails: LibreMidiPortDetails, private val dispatcher: InputDispatcher, private val access: LibreMidiAccess) : MidiInput, LibreMidiPort() {
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
            if (access.supportsUmpTransport)
                checkReturn { library.libremidi_midi_out_schedule_ump(midiOut, timestampInNanoseconds, ByteBuffer.wrap(msg).asIntBuffer(), length.toLong()) }
            else
                checkReturn { library.libremidi_midi_out_schedule_message(midiOut, timestampInNanoseconds, msg, length.toLong()) }
        }
    }
}
