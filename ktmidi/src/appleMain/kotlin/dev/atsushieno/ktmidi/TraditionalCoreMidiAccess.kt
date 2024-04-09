package dev.atsushieno.ktmidi

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.CoreMIDI.*
import platform.darwin.OSStatus
import platform.darwin.SInt32Var
import platform.posix.alloca

// FIXME: it is based on traditional CoreMIDI API.
//  We should also have UMP based CoreMidiAccess.
class TraditionalCoreMidiAccess : MidiAccess() {
    override val name = "CoreMIDI"
    override val inputs: Iterable<MidiPortDetails>
        get() = (0UL until MIDIGetNumberOfSources()).map { MIDIGetSource(it) }.filter { it != 0u }.map { CoreMidiPortDetails(it) }

    override val outputs: Iterable<MidiPortDetails>
        get() = (0UL until MIDIGetNumberOfDestinations()).map { MIDIGetDestination(it) }.filter { it != 0u }.map { CoreMidiPortDetails(it) }

    override suspend fun openInput(portId: String): MidiInput = CoreMidiInput(inputs.first { it.id == portId } as CoreMidiPortDetails)

    override suspend fun openOutput(portId: String): MidiOutput = CoreMidiOutput(outputs.first { it.id == portId } as CoreMidiPortDetails)
}


@OptIn(ExperimentalForeignApi::class)
private fun getPropertyString(obj: MIDIObjectRef, property: CFStringRef?): String? = memScoped {
    val str = alloc<CFStringRefVar>()
    checkStatus { MIDIObjectGetStringProperty(obj, property, str.ptr) }
    if (str.value.rawValue == NativePtr.NULL)
        return null
    return str.value?.getString()
}

@OptIn(ExperimentalForeignApi::class)
private fun getPropertyInt(obj: MIDIObjectRef, property: CFStringRef?): Int {
    memScoped {
        val i = cValue<SInt32Var>()
        checkStatus { MIDIObjectGetIntegerProperty(obj, property, i.ptr) }
        return i.ptr.pointed.value
    }
}

private class CoreMidiPortDetails(val endpoint: MIDIEndpointRef)
    : MidiPortDetails {

    @OptIn(ExperimentalForeignApi::class)
    override val id: String
        get() = getPropertyInt(endpoint, kMIDIPropertyUniqueID).toString()

    @OptIn(ExperimentalForeignApi::class)
    override val manufacturer
        get() = getPropertyString(endpoint, kMIDIPropertyManufacturer)
    @OptIn(ExperimentalForeignApi::class)
    override val name
        get() = getPropertyString(endpoint, kMIDIPropertyDisplayName) ?: getPropertyString(endpoint, kMIDIPropertyName) ?: "(unnamed port)"
    @OptIn(ExperimentalForeignApi::class)
    override val version
        get() = getPropertyString(endpoint, kMIDIPropertyDriverVersion)
    @OptIn(ExperimentalForeignApi::class)
    override val midiTransportProtocol
        get() = getPropertyInt(endpoint, kMIDIPropertyProtocolID)
}

private abstract class CoreMidiPort(override val details: CoreMidiPortDetails) : MidiPort {
    abstract val clientRef: MIDIClientRef
    abstract val portRef: MIDIPortRef

    private var closed: Boolean = false

    override val connectionState: MidiPortConnectionState
        get() = if (closed) MidiPortConnectionState.OPEN else MidiPortConnectionState.CLOSED

    override fun close() {
        MIDIClientDispose(clientRef)
        closed = true
    }

    @OptIn(ExperimentalForeignApi::class)
    protected val stableRef = StableRef.create(this)
    @OptIn(ExperimentalForeignApi::class)
    protected val notifyProc: MIDINotifyProc = staticCFunction(fun (message: CPointer<MIDINotification>?, refCon: COpaquePointer?) {
        if (refCon == null)
            return
        val input = refCon.asStableRef<CoreMidiPort>()
        input.get().notify(message)
    })
    @OptIn(ExperimentalForeignApi::class)
    private fun notify(message: CPointer<MIDINotification>?) {
        // what to do here?
    }
}

class CoreMidiException(status: OSStatus) : Exception("CoreMIDI error: $status")

private fun checkStatus(func: ()->OSStatus) {
    val status = func()
    if (status != 0)
        throw CoreMidiException(status)
}

@OptIn(ExperimentalForeignApi::class)
private class CoreMidiInput(details: CoreMidiPortDetails) : CoreMidiPort(details), MidiInput {
    private var client: MIDIClientRef = 0U
    private var port: MIDIPortRef = 0U
    override val clientRef = client
    override val portRef = port
    private var listener: OnMidiReceivedEventListener? = null

    override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
        this.listener = listener
    }

    private val readProc: MIDIReadProc = staticCFunction(fun (pktlistPtr: CPointer<MIDIPacketList>?, readProcRefCon: COpaquePointer?, srcConnRefCon: COpaquePointer?) {
        if (readProcRefCon == null)
            return
        val input = readProcRefCon.asStableRef<CoreMidiInput>()
        input.get().readInput(pktlistPtr)
    })

    private fun readInput(pktlistPtr: CPointer<MIDIPacketList>?) {
        val listener = this.listener
        if (pktlistPtr == null || listener == null)
            return
        memScoped {
            val pktlist = pktlistPtr.pointed
            var packetPtr: CPointer<MIDIPacket>? = pktlist.packet
            (0 until pktlist.numPackets.toInt()).forEach { _ ->
                val packet = packetPtr?.pointed ?: return@forEach
                val data = packet.data
                val bytes = data.readBytes(packet.length.toInt())
                listener.onEventReceived(bytes, 0, bytes.size, 0)
                packetPtr = MIDIPacketNext(packetPtr)
            }
        }
    }

    val clientName = "KTMidiInputClient"
    val portName = "KTMidiInputPort"

    init {
        memScoped {
            val clientP = alloc<MIDIClientRefVar>()
            checkStatus { MIDIClientCreate(clientName.toCFStringRef(), notifyProc, stableRef.asCPointer(), clientP.ptr) }
            client = clientP.value
            val portP = alloc<MIDIPortRefVar>()
            checkStatus { MIDIInputPortCreate(client, portName.toCFStringRef(), readProc, stableRef.asCPointer(), portP.ptr) }
            port = portP.value
            checkStatus { MIDIPortConnectSource(port, details.endpoint, stableRef.asCPointer()) }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class CoreMidiOutput(details: CoreMidiPortDetails) : CoreMidiPort(details), MidiOutput {
    private var client: MIDIClientRef = 0U
    private var port: MIDIPortRef = 0U
    override val clientRef = client
    override val portRef = port

    override fun send(mevent: ByteArray, offset: Int, length: Int, timestampInNanoseconds: Long) {
        mevent.usePinned { pinned ->
            val packetListPtr = alloca(length.toULong()) ?: return
            val packetListRef: CValuesRef<MIDIPacketList> = packetListPtr.reinterpret()
            MIDIPacketListInit(packetListRef)
            MIDIPacketListAdd(packetListRef, 1U, null, timestampInNanoseconds.toULong(), length.toULong(), pinned.addressOf(offset).reinterpret())
            MIDISend(port, details.endpoint, packetListRef)
        }
    }

    init {
        memScoped {
            val clientName = "KTMidiOutputClient"
            val client = alloc<MIDIClientRefVar>()
            checkStatus { MIDIClientCreate(clientName.toCFStringRef(), notifyProc, stableRef.asCPointer(), client.ptr) }
            val portName = "KTMidiOutputPort"
            val port = alloc<MIDIPortRefVar>()
            checkStatus { MIDIOutputPortCreate(client.value, portName.toCFStringRef(), port.ptr) }
        }
    }
}
