package dev.atsushieno.ktmidi

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.CoreMIDI.*
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.posix.alloca

class CoreMidiAccess : MidiAccess() {
    override val name = "CoreMIDI"
    override val inputs: Iterable<MidiPortDetails>
        get() = (0UL until MIDIGetNumberOfSources()).map { CoreMidiPortDetails(MIDIGetSource(it)) }

    override val outputs: Iterable<MidiPortDetails>
        get() = (0UL until MIDIGetNumberOfDestinations()).map { CoreMidiPortDetails(MIDIGetDestination(it)) }

    override suspend fun openInput(portId: String): MidiInput = CoreMidiInput(inputs.first { it.id == portId } as CoreMidiPortDetails)

    override suspend fun openOutput(portId: String): MidiOutput = CoreMidiOutput(outputs.first { it.id == portId } as CoreMidiPortDetails)
}

private class CoreMidiPortDetails(val endpoint: MIDIEndpointRef)
    : MidiPortDetails {

    @OptIn(ExperimentalForeignApi::class)
    override val id: String = getPropertyString(kMIDIPropertyUniqueID) ?: endpoint.toInt().toString()

    @OptIn(ExperimentalForeignApi::class)
    private fun getPropertyString(property: CFStringRef?): String? {
        val str: CPointer<CFStringRefVar>? = null
        val status = MIDIObjectGetStringProperty(endpoint, property, str)
        if (status == 0 || str == null)
            return null
        return CFBridgingRelease(CFBridgingRetain(str)) as String?
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun getPropertyInt(property: CFStringRef?): Int =
        TODO("FIXME: Not implemented")

    @OptIn(ExperimentalForeignApi::class)
    override val manufacturer = getPropertyString(kMIDIPropertyManufacturer)
    @OptIn(ExperimentalForeignApi::class)
    override val name = getPropertyString(kMIDIPropertyDisplayName) ?: getPropertyString(kMIDIPropertyName) ?: "(unnamed port)"
    @OptIn(ExperimentalForeignApi::class)
    override val version = getPropertyString(kMIDIPropertyDriverVersion)
    //@OptIn(ExperimentalForeignApi::class)
    //override val midiProtocol = getPropertyInt(kMIDIPropertyProtocolID)
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

    override var midiProtocol: Int
        get() = TODO("Not yet implemented")
        set(value) = TODO("Not yet implemented")
}

@OptIn(ExperimentalForeignApi::class)
private class CoreMidiInput(details: CoreMidiPortDetails) : CoreMidiPort(details), MidiInput {
    private var client: MIDIClientRef = 0U
    private var port: MIDIPortRef = 0U
    override val clientRef = client
    override val portRef = port

    override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
        TODO("Not yet implemented")
    }

    init {
        memScoped {
            val clientName = "KTMidiInputClient"
            clientName.usePinned { clientNameBuf ->
                val clientPtr = alloc<MIDIClientRefVar>()
                MIDIClientCreate(clientNameBuf.addressOf(0).reinterpret(), null, null, clientPtr.reinterpret())
                client = clientPtr.value

                val portName = "KTMidiInputPort"
                portName.usePinned { portNameBuf ->
                    val portPtr = alloc<MIDIPortRefVar>()
                    MIDIInputPortCreate(client, portNameBuf.addressOf(0).reinterpret(), null, null, portPtr.reinterpret())
                    port = portPtr.value
                }
            }
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
        val buf = mevent.drop(offset).take(length).toByteArray()
        buf.usePinned { pinned ->
            val packetListPtr = alloca(mevent.size.toULong()) ?: return
            val packetListRef: CValuesRef<MIDIPacketList> = packetListPtr.reinterpret()
            MIDIPacketListInit(packetListRef)
            MIDIPacketListAdd(packetListRef, 1U, null, timestampInNanoseconds.toULong(), mevent.size.toULong(), pinned.addressOf(0).reinterpret())
            MIDISend(port, details.endpoint, packetListRef)
        }
    }

    init {
        memScoped {
            val clientName = "KTMidiInputClient"
            clientName.usePinned { clientNameBuf ->
                val clientPtr = alloc<MIDIClientRefVar>()
                MIDIClientCreate(clientNameBuf.addressOf(0).reinterpret(), null, null, clientPtr.reinterpret())
                client = clientPtr.value

                val portName = "KTMidiInputPort"
                portName.usePinned { portNameBuf ->
                    val portPtr = alloc<MIDIPortRefVar>()
                    MIDIInputPortCreate(client, portNameBuf.addressOf(0).reinterpret(), null, null, portPtr.reinterpret())
                    port = portPtr.value
                }
            }
        }
    }
}