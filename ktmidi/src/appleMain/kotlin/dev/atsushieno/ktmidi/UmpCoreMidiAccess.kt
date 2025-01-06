package dev.atsushieno.ktmidi

import kotlinx.cinterop.*
import platform.CoreMIDI.*

class UmpCoreMidiAccess(val sendBufferSize: Int) : CoreMidiAccess() {
    // It is kept for ABI backward compatibility...
    constructor() : this(1024)

    override val name = "CoreMIDI-UMP"

    override suspend fun openInput(portId: String): MidiInput =
        UmpCoreMidiInput(ClientHolder(this), null, inputs.first { it.id == portId } as CoreMidiPortDetails)

    override suspend fun openOutput(portId: String): MidiOutput =
        UmpCoreMidiOutput(sendBufferSize, ClientHolder(this), outputs.first { it.id == portId } as CoreMidiPortDetails)

    override suspend fun createVirtualInputSender(context: PortCreatorContext): MidiOutput {
        val holder = ClientHolder(this)
        val endpoint = createVirtualInputSource(holder.clientRef, context)
        return UmpCoreMidiOutput(sendBufferSize, holder, CoreMidiPortDetails(endpoint))
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun createVirtualOutputReceiver(context: PortCreatorContext): MidiInput {
        val holder = ClientHolder(this)
        val receiveBlockHolder = ReceiveBlockHolder(null)
        val endpoint = createVirtualOutputDestination(holder.clientRef, receiveBlockHolder.receiveBlock, context)
        val input = UmpCoreMidiInput(ClientHolder(this), receiveBlockHolder, CoreMidiPortDetails(endpoint))
        receiveBlockHolder.input = input
        return input
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun createVirtualInputSource(clientRef: MIDIClientRef, context: PortCreatorContext): MIDIEndpointRef = memScoped {
    val endpoint = alloc<MIDIEndpointRefVar>()
    val protocol = if (context.midiProtocol == MidiTransportProtocol.UMP) kMIDIProtocol_2_0 else kMIDIProtocol_1_0
    checkStatus { MIDISourceCreateWithProtocol(clientRef, context.applicationName.toCFStringRef(), protocol, endpoint.ptr) }
    checkStatus { MIDIObjectSetStringProperty(endpoint.value, kMIDIPropertyManufacturer, context.manufacturer.toCFStringRef()) }
    checkStatus { MIDIObjectSetStringProperty(endpoint.value, kMIDIPropertyDriverVersion, context.version.toCFStringRef()) }
    endpoint.value
}

@OptIn(ExperimentalForeignApi::class)
private fun createVirtualOutputDestination(clientRef: MIDIClientRef, receiveBlock: MIDIReceiveBlock, context: PortCreatorContext): MIDIEndpointRef = memScoped {
    val endpoint = alloc<MIDIEndpointRefVar>()
    val protocol = if (context.midiProtocol == MidiTransportProtocol.UMP) kMIDIProtocol_2_0 else kMIDIProtocol_1_0
    checkStatus { MIDIDestinationCreateWithProtocol(clientRef, context.applicationName.toCFStringRef(), protocol, endpoint.ptr, receiveBlock) }
    checkStatus { MIDIObjectSetStringProperty(endpoint.value, kMIDIPropertyManufacturer, context.manufacturer.toCFStringRef()) }
    checkStatus { MIDIObjectSetStringProperty(endpoint.value, kMIDIPropertyDriverVersion, context.version.toCFStringRef()) }
    endpoint.value
}

private class ReceiveBlockHolder(var input: UmpCoreMidiInput?) {
    @OptIn(ExperimentalForeignApi::class)
    val receiveBlock: MIDIReceiveBlock = { eventListPtr: CPointer<MIDIEventList>?, receiveBlockRefCon: COpaquePointer? ->
        if (receiveBlockRefCon != null) {
            val input = receiveBlockRefCon.asStableRef<ReceiveBlockHolder>()
            input.get().receiveInput(eventListPtr)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun receiveInput(eventListPtr: CPointer<MIDIEventList>?) {
        val listener = input?.listener
        if (eventListPtr == null || listener == null)
            return
        memScoped {
            val eventList = eventListPtr.pointed
            var packetPtr: CPointer<MIDIEventPacket>? = eventList.packet
            (0 until eventList.numPackets.toInt()).forEach { _ ->
                val event = packetPtr?.pointed ?: return@forEach
                val data = event.words
                val bytes = data.readBytes(event.wordCount.toInt() * 4)
                listener.onEventReceived(bytes, 0, bytes.size, 0)
                packetPtr = MIDIEventPacketNext(packetPtr)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class UmpCoreMidiInput(holder: ClientHolder, customReceiveBlockHolder: ReceiveBlockHolder?, coreMidiPortDetails: CoreMidiPortDetails)
    : CoreMidiPort(holder, coreMidiPortDetails), MidiInput, ListenerHolder
{
    override var listener: OnMidiReceivedEventListener? = null

    override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
        this.listener = listener
    }

    private val receiveBlockHolder by lazy { customReceiveBlockHolder ?: ReceiveBlockHolder(this) }

    private val protocol = if (details.midiTransportProtocol == MidiTransportProtocol.UMP) kMIDIProtocol_2_0 else kMIDIProtocol_1_0

    init {
        memScoped {
            val portName = "KTMidiInputPort"
            val port = alloc<MIDIPortRefVar>()
            checkStatus { MIDIInputPortCreateWithProtocol(clientRef, portName.toCFStringRef(), protocol, port.ptr, receiveBlockHolder.receiveBlock) }
            checkStatus { MIDIPortConnectSource(port.value, coreMidiPortDetails.endpoint, stableRef.asCPointer()) }
            port.value
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class UmpCoreMidiOutput(val sendBufferSize: Int, holder: ClientHolder, private val coreMidiPortDetails: CoreMidiPortDetails)
    : CoreMidiPort(holder, coreMidiPortDetails), MidiOutput
{
    private val portRef by lazy {
        memScoped {
            val portName = "KTMidiOutputPort"
            val port = alloc<MIDIPortRefVar>()
            checkStatus { MIDIOutputPortCreate(clientRef, portName.toCFStringRef(), port.ptr) }
            port.value
        }
    }
    private val protocol = if (details.midiTransportProtocol == MidiTransportProtocol.UMP) kMIDIProtocol_2_0 else kMIDIProtocol_1_0

    val arena = Arena()
    val eventList by lazy { arena.alloc(sendBufferSize, 0) as MIDIEventList }

    override fun send(mevent: ByteArray, offset: Int, length: Int, timestampInNanoseconds: Long) {
        mevent.usePinned { pinned ->
            val packet = MIDIEventListInit(eventList.ptr, protocol)
            MIDIEventListAdd(eventList.ptr, 1U, packet, timestampInNanoseconds.toULong(), length.toULong(), pinned.addressOf(offset).reinterpret())
            MIDISendEventList(portRef, coreMidiPortDetails.endpoint, eventList.ptr)
        }
    }
}
