package dev.atsushieno.ktmidi

import kotlinx.cinterop.*
import platform.CoreFoundation.CFStringRef
import platform.CoreMIDI.*
import platform.darwin.SInt32Var
import platform.posix.alloca

class CoreMidiAccess : MidiAccess() {
    override val name = "CoreMIDI"
    override val inputs: Iterable<MidiPortDetails>
        get() = (0UL until MIDIGetNumberOfSources()).map { MIDIGetSource(it) }.filter { it != 0u }.map { CoreMidiPortDetails(it) }

    override val outputs: Iterable<MidiPortDetails>
        get() = (0UL until MIDIGetNumberOfDestinations()).map { MIDIGetDestination(it) }.filter { it != 0u }.map { CoreMidiPortDetails(it) }

    override suspend fun openInput(portId: String): MidiInput = CoreMidiInput(inputs.first { it.id == portId } as CoreMidiPortDetails)

    override suspend fun openOutput(portId: String): MidiOutput = CoreMidiOutput(outputs.first { it.id == portId } as CoreMidiPortDetails)
}

private class CoreMidiPortDetails(val endpoint: MIDIEndpointRef)
    : MidiPortDetails {

    @OptIn(ExperimentalForeignApi::class)
    override val id: String
        get() = getPropertyInt(kMIDIPropertyUniqueID).toString()

    @OptIn(ExperimentalForeignApi::class)
    private fun getPropertyString(property: CFStringRef?): String? = memScoped {
        viaPtr<CFStringRef> { str ->
            val status = MIDIObjectGetStringProperty(endpoint, property, str)
            if (status == 0 || str.rawValue == NativePtr.NULL)
                return@memScoped null
        }?.releaseString()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun getPropertyInt(property: CFStringRef?): Int {
        memScoped {
            val i = cValue<SInt32Var>()
            MIDIObjectGetIntegerProperty(endpoint, property, i.ptr)
            return i.ptr[0]
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override val manufacturer
        get() = getPropertyString(kMIDIPropertyManufacturer)
    @OptIn(ExperimentalForeignApi::class)
    override val name
        get() = getPropertyString(kMIDIPropertyDisplayName) ?: getPropertyString(kMIDIPropertyName) ?: "(unnamed port)"
    @OptIn(ExperimentalForeignApi::class)
    override val version
        get() = getPropertyString(kMIDIPropertyDriverVersion)
    @OptIn(ExperimentalForeignApi::class)
    override val midiTransportProtocol
        get() = getPropertyInt(kMIDIPropertyProtocolID)
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
            client = viaPtr { clientPtr: CPointer<MIDIClientRefVar> ->
                MIDIClientCreate(clientName.toCFStringRef(), null, null, clientPtr)
                val portName = "KTMidiInputPort"
                port = viaPtr { portPtr: CPointer<MIDIPortRefVar> ->
                    MIDIInputPortCreate(client, portName.toCFStringRef(), null, null, portPtr)
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
            val clientName = "KTMidiOutputClient"
            client = viaPtr { clientPtr: CPointer<MIDIClientRefVar> ->
                MIDIClientCreate(clientName.toCFStringRef(), null, null, clientPtr)
                val portName = "KTMidiOutputPort"
                port = viaPtr { portPtr: CPointer<MIDIPortRefVar> ->
                    MIDIOutputPortCreate(client, portName.toCFStringRef(), portPtr)
                }
            }
        }
    }
}
