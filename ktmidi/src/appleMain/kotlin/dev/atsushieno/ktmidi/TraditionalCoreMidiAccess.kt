package dev.atsushieno.ktmidi

import kotlinx.cinterop.*
import platform.CoreMIDI.*
import platform.posix.alloca

// It is based on traditional CoreMIDI API. For MIDI 2.0 support, see UmpCoreMidiAccess.
class TraditionalCoreMidiAccess : CoreMidiAccess() {
    override val name = "CoreMIDI-Traditional"

    override suspend fun openInput(portId: String): MidiInput =
        TraditionalCoreMidiInput(inputs.first { it.id == portId } as CoreMidiPortDetails)

    override suspend fun openOutput(portId: String): MidiOutput =
        TraditionalCoreMidiOutput(outputs.first { it.id == portId } as CoreMidiPortDetails)
}

@OptIn(ExperimentalForeignApi::class)
private class TraditionalCoreMidiInput(details: CoreMidiPortDetails) : CoreMidiPort(details), MidiInput {
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
        val input = readProcRefCon.asStableRef<TraditionalCoreMidiInput>()
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
private class TraditionalCoreMidiOutput(details: CoreMidiPortDetails) : CoreMidiPort(details), MidiOutput {
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
