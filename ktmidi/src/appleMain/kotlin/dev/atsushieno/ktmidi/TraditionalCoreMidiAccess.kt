package dev.atsushieno.ktmidi

import kotlinx.cinterop.*
import platform.CoreMIDI.*

// It is based on traditional CoreMIDI API. For MIDI 2.0 support, see UmpCoreMidiAccess.
class TraditionalCoreMidiAccess(val sendBufferSize: Int = 1024) : CoreMidiAccess() {
    constructor() : this(1024)

    override val name = "CoreMIDI-Traditional"

    override suspend fun openInput(portId: String): MidiInput =
        TraditionalCoreMidiInput(ClientHolder(this), null, inputs.first { it.id == portId } as CoreMidiPortDetails)

    override suspend fun openOutput(portId: String): MidiOutput =
        TraditionalCoreMidiOutput(sendBufferSize, ClientHolder(this), outputs.first { it.id == portId } as CoreMidiPortDetails)

    override suspend fun createVirtualInputSender(context: PortCreatorContext): MidiOutput {
        val holder = ClientHolder(this)
        val endpoint = createVirtualInputSource(holder.clientRef, context)
        return TraditionalCoreMidiOutput(sendBufferSize, holder, CoreMidiPortDetails(endpoint))
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun createVirtualOutputReceiver(context: PortCreatorContext): MidiInput {
        val holder = ClientHolder(this)
        val readProcHolder = ReadProcHolder(null)
        val endpoint = createVirtualOutputDestination(holder.clientRef,
            readProcHolder.readProc,
            readProcHolder.stableRef.asCPointer(),
            context)
        val input = TraditionalCoreMidiInput(holder, readProcHolder, CoreMidiPortDetails(endpoint))
        readProcHolder.input = input
        return input
    }
}

private class ReadProcHolder(var input: TraditionalCoreMidiInput?) {
    @OptIn(ExperimentalForeignApi::class)
    val stableRef by lazy { StableRef.create(this) }

    @OptIn(ExperimentalForeignApi::class)
    val readProc: MIDIReadProc = staticCFunction(fun (pktlistPtr: CPointer<MIDIPacketList>?, readProcRefCon: COpaquePointer?, srcConnRefCon: COpaquePointer?) {
        if (readProcRefCon == null)
            return
        val input = readProcRefCon.asStableRef<ReadProcHolder>()
        input.get().readInput(pktlistPtr)
    })

    @OptIn(ExperimentalForeignApi::class)
    private fun readInput(pktlistPtr: CPointer<MIDIPacketList>?) {
        val listener = input?.listener
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
}

@OptIn(ExperimentalForeignApi::class)
private fun createVirtualInputSource(clientRef: MIDIClientRef, context: PortCreatorContext): MIDIEndpointRef = memScoped {
    val endpoint = alloc<MIDIEndpointRefVar>()
    checkStatus { MIDISourceCreate(clientRef, context.applicationName.toCFStringRef(), endpoint.ptr) }
    checkStatus { MIDIObjectSetStringProperty(endpoint.value, kMIDIPropertyManufacturer, context.manufacturer.toCFStringRef()) }
    checkStatus { MIDIObjectSetStringProperty(endpoint.value, kMIDIPropertyDriverVersion, context.version.toCFStringRef()) }
    endpoint.value
}

@OptIn(ExperimentalForeignApi::class)
private fun createVirtualOutputDestination(clientRef: MIDIClientRef, readProc: MIDIReadProc?, refCon: COpaquePointer?, context: PortCreatorContext): MIDIEndpointRef = memScoped {
    val endpoint = alloc<MIDIEndpointRefVar>()
    checkStatus { MIDIDestinationCreate(clientRef, context.applicationName.toCFStringRef(), readProc, refCon, endpoint.ptr) }
    checkStatus { MIDIObjectSetStringProperty(endpoint.value, kMIDIPropertyManufacturer, context.manufacturer.toCFStringRef()) }
    checkStatus { MIDIObjectSetStringProperty(endpoint.value, kMIDIPropertyDriverVersion, context.version.toCFStringRef()) }
    endpoint.value
}

@OptIn(ExperimentalForeignApi::class)
private open class TraditionalCoreMidiInput(holder: ClientHolder, customReadProcHolder: ReadProcHolder?, private val coreMidiPortDetails: CoreMidiPortDetails)
    : CoreMidiPort(holder, coreMidiPortDetails), MidiInput, ListenerHolder {

    override var listener: OnMidiReceivedEventListener? = null

    override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
        this.listener = listener
    }

    private val readProcHolder by lazy { customReadProcHolder ?: ReadProcHolder(this) }

    init {
        memScoped {
            val portName = "KTMidiInputPort"
            val port = alloc<MIDIPortRefVar>()
            checkStatus { MIDIInputPortCreate(clientRef, portName.toCFStringRef(), readProcHolder.readProc, readProcHolder.stableRef.asCPointer(), port.ptr) }
            checkStatus { MIDIPortConnectSource(port.value, coreMidiPortDetails.endpoint, stableRef.asCPointer()) }
            port.value
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private open class TraditionalCoreMidiOutput(val sendBufferSize: Int, holder: ClientHolder, private val coreMidiPortDetails: CoreMidiPortDetails)
    : CoreMidiPort(holder, coreMidiPortDetails), MidiOutput
{
    private val portRef = memScoped {
        val portName = "KTMidiOutputPort"
        val port = alloc<MIDIPortRefVar>()
        checkStatus { MIDIOutputPortCreate(clientRef, portName.toCFStringRef(), port.ptr) }
        port.value
    }

    val arena = Arena()
    val packetList by lazy { arena.alloc(sendBufferSize, 0) as MIDIPacketList }

    override fun send(mevent: ByteArray, offset: Int, length: Int, timestampInNanoseconds: Long) {
        mevent.usePinned { pinned ->
            val curPacket = MIDIPacketListInit(packetList.ptr)
            // FIXME: use AudioGetCurrentHostTime() and mach_absolute_time() to calculate timestamps
            //  (They do not exist in Kotlin-Native platform bindings yet)
            MIDIPacketListAdd(packetList.ptr, sendBufferSize.toULong(), curPacket, 0UL, length.toULong(), pinned.addressOf(offset).reinterpret())
                ?: throw CoreMidiException("Could not add message to send buffer. Trying increasing the buffer size.")
            checkStatus {
                MIDISend(portRef, coreMidiPortDetails.endpoint, packetList.ptr)
            }
        }
    }
}
