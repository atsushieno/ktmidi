package dev.atsushieno.ktmidi

import dev.atsushieno.ktmidi.LibreMidiAccess.LibreMidiException
import dev.atsushieno.panama.libremidi.Initializer
import dev.atsushieno.panama.libremidi.libremidi_api_configuration
import dev.atsushieno.panama.libremidi.libremidi_midi1_callback
import dev.atsushieno.panama.libremidi.libremidi_midi2_callback
import dev.atsushieno.panama.libremidi.libremidi_midi_configuration
import dev.atsushieno.panama.libremidi.libremidi_observer_configuration
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets

import dev.atsushieno.panama.libremidi.libremidi_c_h as library
import dev.atsushieno.panama.libremidi.`libremidi_midi_observer_enumerate_input_ports$x0` as InputEnumerationCallback
import dev.atsushieno.panama.libremidi.`libremidi_midi_observer_enumerate_output_ports$x0` as OutputEnumerationCallback

private fun checkReturn(action: ()->Int) {
    val ret = action()
    if (ret != 0)
        throw LibreMidiException(ret)
}

class LibreMidiAccess(private val api: Int) : MidiAccess() {

    companion object {
        private val arena = Arena.ofShared()

        private var sequentialIdSerial = 0

        private fun guessPlatform(): DesktopPlatform {
            val os = System.getProperty("os.name")
            return when {
                os.startsWith("windows", true) -> DesktopPlatform.Windows
                os.startsWith("mac", true) -> DesktopPlatform.MacOS
                else -> DesktopPlatform.Linux
            }
        }
        private var libraryLoaded = false;
        private fun ensureLibreMidiLoaded() {
            if (!libraryLoaded) {
                Initializer.initialize()
                libraryLoaded = true
            }
        }
        fun create(transportProtocol: Int): LibreMidiAccess {
            ensureLibreMidiLoaded()
            return LibreMidiAccess(API.getPlatformDefault(guessPlatform(), transportProtocol))
        }
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
        val Unspecified = library.UNSPECIFIED()
        val CoreMidi = library.COREMIDI()
        val AlsaSeq = library.ALSA_SEQ()
        val AlsaRaw = library.ALSA_RAW()
        val JackMidi = library.JACK_MIDI()
        val WindowsMM = library.WINDOWS_MM()
        val WindowsUwp = library.WINDOWS_UWP()
        val WebMidi = library.WEBMIDI() // ktmidi-jvm-desktop wouldn't run on Web platform though
        val PipeWire = library.PIPEWIRE()
        val AlsaSeqUmp = library.ALSA_SEQ_UMP()
        val AlsaRawUmp = library.ALSA_RAW_UMP()
        val CoreMidiUmp = library.COREMIDI_UMP()
        val WindowsMidiServices = library.WINDOWS_MIDI_SERVICES()
        val Dummy = library.DUMMY()

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

    private val observerConfig = libremidi_observer_configuration.allocate(arena).also {

        library.libremidi_midi_observer_configuration_init(it)
        libremidi_observer_configuration.track_virtual(it, true)
        libremidi_observer_configuration.track_any(it, true)
    }
    private val apiConfig = libremidi_api_configuration.allocate(arena).also {
        checkReturn { library.libremidi_midi_api_configuration_init(it) }
        libremidi_api_configuration.api(it, api)
    }
    private val observerPtr = arena.allocate(ValueLayout.ADDRESS_UNALIGNED).also {
        checkReturn { library.libremidi_midi_observer_new(observerConfig, apiConfig, it) }
    }
    private val observer = observerPtr.get(ValueLayout.ADDRESS_UNALIGNED, 0)

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
        val port: MemorySegment/*libremidi_midi_in_port*/
    ) : LibreMidiPortDetails(access, id, name), AutoCloseable {
        override fun close() {
            checkReturn { library.libremidi_midi_in_port_free(port) }
        }
    }

    class LibreMidiRealOutPortDetails(
        private val access: LibreMidiAccess,
        override val id: String,
        override val name: String,
        val port: MemorySegment/*libremidi_midi_out_port*/
    ) : LibreMidiPortDetails(access, id, name), AutoCloseable {
        override fun close() {
            checkReturn { library.libremidi_midi_out_port_free(port) }
        }
    }

    private fun getPortName(port: MemorySegment, getName: (port: MemorySegment, namePtr: MemorySegment, sizePtr: MemorySegment)->Int): CharBuffer {
        val namePtr = arena.allocate(ValueLayout.ADDRESS_UNALIGNED)
        val sizePtr = arena.allocate(ValueLayout.JAVA_LONG)
        checkReturn { getName(port, namePtr, sizePtr) }
        val size = sizePtr.get(ValueLayout.JAVA_LONG, 0)
        val nameSrc = namePtr.get(ValueLayout.ADDRESS_UNALIGNED, 0)
        val nameBuf = arena.allocate(size)
        MemorySegment.copy(nameSrc.reinterpret(size), 0, nameBuf, 0, size)
        return StandardCharsets.UTF_8.decode(nameBuf.asByteBuffer())
    }
    private fun getInPortName(inPort: MemorySegment) =
        getPortName(inPort) { port, namePtr, sizePtr ->
            library.libremidi_midi_in_port_name(port, namePtr, sizePtr)
        }.toString()
    private fun getOutPortName(outPort: MemorySegment) =
        getPortName(outPort) { port, namePtr, sizePtr ->
            library.libremidi_midi_out_port_name(port, namePtr, sizePtr)
        }.toString()

    override val inputs: Iterable<MidiPortDetails>
        get() {
            val list = mutableListOf<LibreMidiRealInPortDetails>()
            val inProc = InputEnumerationCallback.allocate({ _, port ->
                val name = getInPortName(port)
                val clonePtr = arena.allocate(ValueLayout.ADDRESS_UNALIGNED)
                checkReturn { library.libremidi_midi_in_port_clone(port, clonePtr) }
                val clone = clonePtr.get(ValueLayout.ADDRESS_UNALIGNED, 0)
                list.add(LibreMidiRealInPortDetails(this@LibreMidiAccess, "In_${instanceIdSerial}_${list.size}", name, clone))
            }, arena)
            checkReturn { library.libremidi_midi_observer_enumerate_input_ports(observer, MemorySegment.NULL, inProc) }
            return list
        }

    override val outputs: Iterable<MidiPortDetails>
        get() {
            val list = mutableListOf<LibreMidiRealOutPortDetails>()
            val outProc = OutputEnumerationCallback.allocate({ _, port ->
                val name = getOutPortName(port)
                val clonePtr = arena.allocate(ValueLayout.ADDRESS_UNALIGNED)
                checkReturn { library.libremidi_midi_out_port_clone(port, clonePtr) }
                val clone = clonePtr.get(ValueLayout.ADDRESS_UNALIGNED, 0)
                list.add(LibreMidiRealOutPortDetails(this@LibreMidiAccess, "Out_${instanceIdSerial}_${list.size}", name, clone))
            }, arena)
            checkReturn { library.libremidi_midi_observer_enumerate_output_ports(observer, MemorySegment.NULL, outProc) }
            return list
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
        val midiConfig = libremidi_midi_configuration.allocate(arena)
        checkReturn { library.libremidi_midi_configuration_init(midiConfig) }

        val nameArray = context.portName.toUtf8ByteArray()
        val nameCopy = arena.allocate(nameArray.size.toLong())
        nameCopy.copyFrom(MemorySegment.ofArray(nameArray))
        libremidi_midi_configuration.port_name(midiConfig, nameCopy)

        libremidi_midi_configuration.virtual_port(midiConfig, true)
        libremidi_midi_configuration.version(midiConfig,
            if (context.midiProtocol == MidiTransportProtocol.UMP) library.MIDI2()
            else library.MIDI1())

        val midiOutPtr = arena.allocate(ValueLayout.ADDRESS_UNALIGNED)
        checkReturn { library.libremidi_midi_out_new(midiConfig, apiConfig, midiOutPtr) }
        val midiOut = midiOutPtr.get(ValueLayout.ADDRESS_UNALIGNED, 0)
        val idName = "VIn_${instanceIdSerial}_${nextVirtualPortIndex++}"
        val portDetails = LibreMidiPortDetails(this, idName, context.portName)

        return LibreMidiOutput(midiOut, portDetails, this)
    }

    override suspend fun createVirtualOutputReceiver(context: PortCreatorContext): MidiInput {
        val dispatcher = InputDispatcher()
        val (ctx, midiConfig) = createMidiConfig(context.portName, dispatcher, true)

        val midiInPtr = arena.allocate(ValueLayout.ADDRESS_UNALIGNED)
        checkReturn { library.libremidi_midi_in_new(midiConfig, apiConfig, midiInPtr) }
        val midiIn = midiInPtr.get(ValueLayout.ADDRESS_UNALIGNED, 0)
        val idName = "VOut_${instanceIdSerial}_${nextVirtualPortIndex++}"
        val portDetails = LibreMidiPortDetails(this, idName, context.portName)

        return LibreMidiInput(midiIn, portDetails, dispatcher, ctx, this)
    }

    override suspend fun openInput(portId: String): MidiInput {
        val portDetails = inputs.first { it.id == portId } as LibreMidiRealInPortDetails
        val dispatcher = InputDispatcher()
        val (ctx, midiConfig) = createMidiConfig(portId, dispatcher, false)
        val ioUnion = libremidi_midi_configuration.in_or_out_port(midiConfig)
        libremidi_midi_configuration.in_or_out_port.in_port(ioUnion, portDetails.port)
        val midiInPtr = arena.allocate(ValueLayout.ADDRESS_UNALIGNED)
        checkReturn { library.libremidi_midi_in_new(midiConfig, apiConfig, midiInPtr) }
        val midiIn = midiInPtr.get(ValueLayout.ADDRESS_UNALIGNED, 0)
        return LibreMidiInput(midiIn, portDetails, dispatcher, ctx, this)
    }

    override suspend fun openOutput(portId: String): MidiOutput {
        val portDetails = outputs.first { it.id == portId } as LibreMidiRealOutPortDetails
        val midiConfig = libremidi_midi_configuration.allocate(arena)
        checkReturn { library.libremidi_midi_configuration_init(midiConfig) }
        val ioUnion = libremidi_midi_configuration.in_or_out_port(midiConfig)
        libremidi_midi_configuration.in_or_out_port.out_port(ioUnion, portDetails.port)
        libremidi_midi_configuration.version(midiConfig,
            if (portDetails.midiTransportProtocol == MidiTransportProtocol.UMP) library.MIDI2()
            else library.MIDI1())

        val midiOutPtr = arena.allocate(ValueLayout.ADDRESS_UNALIGNED)
        checkReturn { library.libremidi_midi_out_new(midiConfig, apiConfig, midiOutPtr) }
        val midiOut = midiOutPtr.get(ValueLayout.ADDRESS_UNALIGNED, 0)

        return LibreMidiOutput(midiOut, portDetails, this)
    }

    internal abstract class LibreMidiPort : MidiPort {
        abstract override val details: MidiPortDetails
        abstract override fun close()
    }

    private fun createMidiConfig(name: String, dispatcher: InputDispatcher, virtualPort: Boolean): Pair<Any, MemorySegment/*libremidi_midi_configuration*/> {
        lateinit var cb: Any
        val conf = libremidi_midi_configuration.allocate(arena)
        checkReturn { library.libremidi_midi_configuration_init(conf) }

        val onError = libremidi_midi_configuration.on_error(conf)
        val errorCallback = libremidi_midi_configuration.libremidi_midi_configuration_on_error.callback.allocate({ ctx: MemorySegment, error: MemorySegment, errorLen: Long, location: MemorySegment ->
            val errorString = StandardCharsets.UTF_8.decode(error.reinterpret(errorLen).asByteBuffer())
            // FIXME: maybe implement better logging
            println("libremidi onError: $errorString")
        }, arena)
        libremidi_midi_configuration.libremidi_midi_configuration_on_error.callback(onError, errorCallback)

        val nameArray = name.toUtf8ByteArray()
        val nameCopy = arena.allocate(nameArray.size.toLong())
        nameCopy.copyFrom(MemorySegment.ofArray(nameArray))
        libremidi_midi_configuration.port_name(conf, nameCopy)

        val cbUnion = libremidi_midi_configuration.on_midi_1_or_2_callback(conf)
        if (supportsUmpTransport) {
            libremidi_midi_configuration.version(conf, library.MIDI2())
            val umpCallback = libremidi_midi2_callback.allocate(arena)
            val callbackFunc = libremidi_midi2_callback.callback.Function { context, timestamp, data, len ->
                // It feels a bit awkward, but `data.asBuffer()` returns such an IntBuffer that has only capacity = 1,
                // so it is useless here. We need to create another BytePointer and its resulting ByteBuffer.
                val size = len.toInt() * 4
                val umpArray = ByteArray(size)
                data.reinterpret(size.toLong()).asByteBuffer().get(umpArray)
                dispatcher.listener?.onEventReceived(umpArray, 0, size, timestamp)
            }
            val cbFunc = libremidi_midi2_callback.callback.allocate(callbackFunc, arena)
            libremidi_midi2_callback.callback(umpCallback, cbFunc)
            libremidi_midi_configuration.on_midi_1_or_2_callback.on_midi2_message(cbUnion, umpCallback)
            cb = umpCallback
        } else {
            libremidi_midi_configuration.version(conf, library.MIDI1())
            val midi1Callback = libremidi_midi1_callback.allocate(arena)
            val callbackFunc = libremidi_midi1_callback.callback.Function { context, timestamp, data, len ->
                val array = ByteArray(len.toInt())
                data.reinterpret(len).asByteBuffer().get(array)
                dispatcher.listener?.onEventReceived(array, 0, len.toInt(), timestamp)
            }
            val cbFunc = libremidi_midi1_callback.callback.allocate(callbackFunc, arena)
            libremidi_midi1_callback.callback(midi1Callback, cbFunc)
            libremidi_midi_configuration.on_midi_1_or_2_callback.on_midi1_message(cbUnion, midi1Callback)
            cb = midi1Callback
        }
        libremidi_midi_configuration.virtual_port(conf, virtualPort)
        return Pair(cb, conf)
    }

    class InputDispatcher {
        var listener: OnMidiReceivedEventListener? = null
    }

    private class LibreMidiInput(
        private val midiIn: MemorySegment/*libremidi_midi_in_handle*/,
        private val portDetails: LibreMidiPortDetails,
        private val dispatcher: InputDispatcher,
        private val contextHolder: Any,
        private val access: LibreMidiAccess
    ) : MidiInput, LibreMidiPort() {
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

    private class LibreMidiOutput(
        private val midiOut: MemorySegment/*libremidi_midi_out_handle*/,
        private val portDetails: LibreMidiPortDetails,
        private val access: LibreMidiAccess
    ) : MidiOutput, LibreMidiPort() {
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
                val buf = arena.allocate(length.toLong())
                buf.copyFrom(MemorySegment.ofArray(msg.sliceArray(IntRange(offset, offset + length - 1))))
                checkReturn { library.libremidi_midi_out_schedule_ump(midiOut, timestampInNanoseconds, buf, (msg.size / 4).toLong()) }
            } else {
                val buf = arena.allocate(length.toLong())
                buf.copyFrom(MemorySegment.ofArray(msg.sliceArray(IntRange(offset, offset + length - 1))))
                println("send $timestampInNanoseconds $length " + mevent.drop(offset).take(length).joinToString { it.toUnsigned().toString(16) })
                checkReturn { library.libremidi_midi_out_schedule_message(midiOut, timestampInNanoseconds, buf, length.toLong()) }
            }
        }
    }
}
