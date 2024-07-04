package dev.atsushieno.ktmidi

import kotlinx.cinterop.*
import platform.CoreMIDI.*
import platform.Foundation.NSProcessInfo

@OptIn(ExperimentalForeignApi::class, ExperimentalStdlibApi::class)
abstract class CoreMidiAccess : MidiAccess() {
    companion object {
        @OptIn(ExperimentalForeignApi::class)
        fun create() =
            // We do not want to create UmpCoreMidiAccess until macOS 14 or iOS 17 by default
            // (due to their framework stability...)
            // You can try to do so by explicitly creating UmpCoreMidiAccess instance.
            if (NSProcessInfo.processInfo.operatingSystemVersion.useContents {
                // FIXME: How can I detect if I am on macOS or iOS in general??
                if (false) // isiOS
                    majorVersion >= 17
                else
                    majorVersion >= 14
            })
                UmpCoreMidiAccess()
            else
                TraditionalCoreMidiAccess()
    }

    override val canCreateVirtualPort: Boolean = true

    override val inputs: Iterable<MidiPortDetails>
        get() = (0UL until MIDIGetNumberOfSources())
            .map { MIDIGetSource(it) }.filter { it != 0u }.map { CoreMidiPortDetails(it) }

    override val outputs: Iterable<MidiPortDetails>
        get() = (0UL until MIDIGetNumberOfDestinations())
            .map { MIDIGetDestination(it) }.filter { it != 0u }.map { CoreMidiPortDetails(it) }

    override val canDetectStateChanges: Boolean = true

    internal fun notifyChange(change: StateChange, port: CoreMidiPortDetails) = stateChanged(change, port)

}

internal class CoreMidiPortDetails(val endpoint: MIDIEndpointRef)
    : MidiPortDetails {

    @OptIn(ExperimentalForeignApi::class)
    override val id: String
        get() = getPropertyInt(endpoint, kMIDIPropertyUniqueID).toString()

    @OptIn(ExperimentalForeignApi::class)
    override val manufacturer
        get() = getPropertyString(endpoint, kMIDIPropertyManufacturer)
    @OptIn(ExperimentalForeignApi::class)
    override val name
        get() = getPropertyString(endpoint, kMIDIPropertyDisplayName)
            ?: getPropertyString(endpoint, kMIDIPropertyName)
            ?: "(unnamed port)"
    @OptIn(ExperimentalForeignApi::class)
    override val version
        get() = getPropertyString(endpoint, kMIDIPropertyDriverVersion)
    @OptIn(ExperimentalForeignApi::class)
    override val midiTransportProtocol
        get() = getPropertyInt(endpoint, kMIDIPropertyProtocolID)
}

internal interface ListenerHolder {
    val listener: OnMidiReceivedEventListener?
}

// MIDIClientRef setup
@OptIn(ExperimentalForeignApi::class)
internal class ClientHolder(access: CoreMidiAccess) : AutoCloseable {
    val clientRef: MIDIClientRef = memScoped {
        val clientName = "KTMidiClient"
        val client = alloc<MIDIClientRefVar>()
        val notifyBlock: MIDINotifyBlock = { message: CPointer<MIDINotification>? ->
            if (message != null) {
                val notification = message.pointed
                // what to do here?
                when (notification.messageID) {
                    kMIDIMsgObjectAdded -> {
                        val evt = notification.reinterpret<MIDIObjectAddRemoveNotification>()
                        when (evt.childType) {
                            kMIDIObjectType_Source, kMIDIObjectType_Destination ->
                                access.notifyChange(MidiAccess.StateChange.Added, CoreMidiPortDetails(evt.child))
                        }
                    }
                    kMIDIMsgObjectRemoved -> {
                        val evt = notification.reinterpret<MIDIObjectAddRemoveNotification>()
                        when (evt.childType) {
                            kMIDIObjectType_Source, kMIDIObjectType_Destination ->
                                access.notifyChange(MidiAccess.StateChange.Removed, CoreMidiPortDetails(evt.child))
                        }
                    }
                }
            }
        }
        checkStatus { MIDIClientCreateWithBlock(clientName.toCFStringRef(), client.ptr, notifyBlock) }
        client.value
    }

    override fun close() {
        MIDIClientDispose(clientRef)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal abstract class CoreMidiPort(
    private val holder: ClientHolder,
    private val coreMidiPortDetails: CoreMidiPortDetails
) : MidiPort {
    override val details: MidiPortDetails
        get() = coreMidiPortDetails

    val clientRef: MIDIClientRef
        get() = holder.clientRef

    private var closed: Boolean = false

    override val connectionState: MidiPortConnectionState
        get() = if (closed) MidiPortConnectionState.OPEN else MidiPortConnectionState.CLOSED

    override fun close() {
        closed = true
    }

    @OptIn(ExperimentalForeignApi::class)
    protected val stableRef by lazy { StableRef.create(this) }
}
