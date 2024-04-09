package dev.atsushieno.ktmidi

import kotlinx.cinterop.*
import platform.CoreMIDI.*
import platform.posix.alloca

class UmpCoreMidiAccess : MidiAccess() {
    override val name = "CoreMIDI-UMP"
    override val inputs: Iterable<MidiPortDetails>
        get() = (0UL until MIDIGetNumberOfSources())
            .map { MIDIGetSource(it) }.filter { it != 0u }.map { UmpCoreMidiPortDetails(it) }

    override val outputs: Iterable<MidiPortDetails>
        get() = (0UL until MIDIGetNumberOfDestinations())
            .map { MIDIGetDestination(it) }.filter { it != 0u }.map { UmpCoreMidiPortDetails(it) }

    override suspend fun openInput(portId: String): MidiInput =
        UmpCoreMidiInput(inputs.first { it.id == portId } as UmpCoreMidiPortDetails)

    override suspend fun openOutput(portId: String): MidiOutput =
        UmpCoreMidiOutput(outputs.first { it.id == portId } as UmpCoreMidiPortDetails)
}

private class UmpCoreMidiPortDetails(val endpoint: MIDIEndpointRef)
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

private abstract class UmpCoreMidiPort(override val details: UmpCoreMidiPortDetails) : MidiPort {
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
        val input = refCon.asStableRef<UmpCoreMidiPort>()
        input.get().notify(message)
    })
    @OptIn(ExperimentalForeignApi::class)
    private fun notify(message: CPointer<MIDINotification>?) {
        // what to do here?
    }
}

@OptIn(ExperimentalForeignApi::class)
private class UmpCoreMidiInput(details: UmpCoreMidiPortDetails) : UmpCoreMidiPort(details), MidiInput {
    private var client: MIDIClientRef = 0U
    private var port: MIDIPortRef = 0U
    override val clientRef = client
    override val portRef = port
    private var listener: OnMidiReceivedEventListener? = null

    override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
        this.listener = listener
    }

    private val receiveBlock: MIDIReceiveBlock = { eventListPtr: CPointer<MIDIEventList>?, receiveBlockRefCon: COpaquePointer? ->
        if (receiveBlockRefCon != null) {
            val input = receiveBlockRefCon.asStableRef<UmpCoreMidiInput>()
            input.get().receiveInput(eventListPtr)
        }
    }

    private fun receiveInput(eventListPtr: CPointer<MIDIEventList>?) {
        val listener = this.listener
        if (eventListPtr == null || listener == null)
            return
        memScoped {
            val eventList = eventListPtr.pointed
            var packetPtr: CPointer<MIDIEventPacket>? = eventList.packet
            (0 until eventList.numPackets.toInt()).forEach { _ ->
                val event = packetPtr?.pointed ?: return@forEach
                val data = event.words
                val bytes = data.readBytes(event.wordCount.toInt())
                listener.onEventReceived(bytes, 0, bytes.size, 0)
                packetPtr = MIDIEventPacketNext(packetPtr)
            }
        }
    }

    private val protocol = if (details.midiTransportProtocol == MidiTransportProtocol.UMP) kMIDIProtocol_2_0 else kMIDIProtocol_1_0

    init {
        memScoped {
            val clientName = "KTMidiInputClient"
            val portName = "KTMidiInputPort"
            val clientP = alloc<MIDIClientRefVar>()
            checkStatus { MIDIClientCreate(clientName.toCFStringRef(), notifyProc, stableRef.asCPointer(), clientP.ptr) }
            client = clientP.value
            val portP = alloc<MIDIPortRefVar>()
            checkStatus { MIDIInputPortCreateWithProtocol(client, portName.toCFStringRef(), protocol, portP.ptr, receiveBlock) }
            port = portP.value
            checkStatus { MIDIPortConnectSource(port, details.endpoint, stableRef.asCPointer()) }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class UmpCoreMidiOutput(details: UmpCoreMidiPortDetails) : UmpCoreMidiPort(details), MidiOutput {
    private var client: MIDIClientRef = 0U
    private var port: MIDIPortRef = 0U
    override val clientRef = client
    override val portRef = port
    private val protocol = if (details.midiTransportProtocol == MidiTransportProtocol.UMP) kMIDIProtocol_2_0 else kMIDIProtocol_1_0

    override fun send(mevent: ByteArray, offset: Int, length: Int, timestampInNanoseconds: Long) {
        mevent.usePinned { pinned ->
            val eventListPtr = alloca(length.toULong()) ?: return
            val eventListRef: CValuesRef<MIDIEventList> = eventListPtr.reinterpret()
            MIDIEventListInit(eventListRef, protocol)
            MIDIEventListAdd(eventListRef, 1U, null, timestampInNanoseconds.toULong(), length.toULong(), pinned.addressOf(offset).reinterpret())
            MIDISendEventList(port, details.endpoint, eventListRef)
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
