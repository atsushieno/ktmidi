package dev.atsushieno.ktmidi

import kotlinx.cinterop.*
import platform.CoreMIDI.*
import platform.posix.alloca

class UmpCoreMidiAccess : CoreMidiAccess() {
    override val name = "CoreMIDI-UMP"

    override suspend fun openInput(portId: String): MidiInput =
        UmpCoreMidiInput(inputs.first { it.id == portId } as CoreMidiPortDetails)

    override suspend fun openOutput(portId: String): MidiOutput =
        UmpCoreMidiOutput(outputs.first { it.id == portId } as CoreMidiPortDetails)
}

@OptIn(ExperimentalForeignApi::class)
private class UmpCoreMidiInput(details: CoreMidiPortDetails) : CoreMidiPort(details), MidiInput {
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
                val bytes = data.readBytes(event.wordCount.toInt() * 4)
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
private class UmpCoreMidiOutput(details: CoreMidiPortDetails) : CoreMidiPort(details), MidiOutput {
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
