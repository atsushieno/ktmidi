package dev.atsushieno.ktmidi

val emptyMidiAccess = EmptyMidiAccess()

abstract class MidiAccess {
    /** The name of the MIDI access API (such as ALSA, WinMM, CoreMIDI) for application developers' convenience. */
    abstract val name: String

    /** List of MIDI input devices that application developers can receive inputs. */
    abstract val inputs: Iterable<MidiPortDetails>
    /** List of MIDI output devices that application developers can send outputs. */
    abstract val outputs: Iterable<MidiPortDetails>

    abstract suspend fun openInput(portId: String): MidiInput
    abstract suspend fun openOutput(portId: String): MidiOutput

    enum class StateChange {
        Added,
        Removed,
        Other
    }

    // Who can actually detect the changes? Web MIDI API on Chromium surprisingly does,
    // while none of rtmidi, javax.sound.midi, JUCE can...
    open val canDetectStateChanges = false
    var stateChanged : (StateChange, MidiPortDetails) -> Unit = { _, _ -> }

    @Deprecated("Use canCreateVirtualPort(PortCreatorContext) instead")
    open val canCreateVirtualPort = false

    open fun canCreateVirtualPort(context: PortCreatorContext): Boolean =
        canCreateVirtualPort && if (context.midiProtocol == MidiTransportProtocol.UMP) supportsUmpTransport else true

    open suspend fun createVirtualInputSender(context: PortCreatorContext): MidiOutput {
        throw UnsupportedOperationException()
    }
    open suspend fun createVirtualOutputReceiver(context: PortCreatorContext): MidiInput {
        throw UnsupportedOperationException()
    }

    /** Indicates whether it supports MIDI 2.0 UMP transports.
     *  It is useful when you compare multiple MidiAccess APIs and choose one that matches your need.
     */
    open val supportsUmpTransport = false
}

interface MidiPortDetails {
    val id: String
    val manufacturer: String?
    val name: String?
    val version: String?
    val midiTransportProtocol: Int
}

enum class MidiPortConnectionState {
    OPEN,
    CLOSED,
}

interface MidiPort {
    val details: MidiPortDetails
    val connectionState: MidiPortConnectionState
    fun close()
}

fun interface OnMidiReceivedEventListener {
    fun onEventReceived(data: ByteArray, start: Int, length: Int, timestampInNanoseconds: Long)
}

interface MidiInput : MidiPort {
    abstract fun setMessageReceivedListener(listener: OnMidiReceivedEventListener)
}

interface MidiOutput : MidiPort {
    abstract fun send(mevent: ByteArray, offset: Int, length: Int, timestampInNanoseconds: Long)
}

// Virtual MIDI port support

abstract class SimpleVirtualMidiPort protected constructor(
    override val details: MidiPortDetails,
    private val onDispose: () -> Unit
) : MidiPort {

    private var state: MidiPortConnectionState = MidiPortConnectionState.OPEN

    override val connectionState: MidiPortConnectionState
        get() = state

    override fun close ()
    {
        onDispose ()
        state = MidiPortConnectionState.CLOSED
    }
}

class SimpleVirtualMidiInput(details: MidiPortDetails, onDispose: () -> Unit) : SimpleVirtualMidiPort(
    details,
    onDispose
), MidiInput
{
    private var messageReceived: OnMidiReceivedEventListener? = null

    override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
        messageReceived = listener
    }
}

class SimpleVirtualMidiOutput(details: MidiPortDetails, onDispose: () -> Unit) : SimpleVirtualMidiPort(
    details,
    onDispose
), MidiOutput
{
    var onSend: (ByteArray,Int,Int, Long) -> Unit = { _, _, _, _ -> }

    override fun send (mevent: ByteArray, offset: Int, length: Int, timestampInNanoseconds: Long) {
        onSend (mevent, offset, length, timestampInNanoseconds)
    }
}

data class PortCreatorContext(
    var applicationName: String,
    var portName: String,
    var manufacturer: String,
    var version: String,
    /** Use MidiProtocolVersion (UNSPECIFIED, MIDI1 or MIDI2) */
    var midiProtocol: Int = MidiProtocolVersion.UNSPECIFIED,
    var umpGroup: Int = 0
)

// MidiAccess implementation.

class EmptyMidiAccess : MidiAccess() {
    companion object {
        // They are exposed so that they could be accessed in non-blocking context
        //  that is mandatory in Kotlin/Wasm etc.
        val input: MidiInput = EmptyMidiInput.instance
        val output: MidiOutput = EmptyMidiOutput.instance
    }
    override val name: String
        get() = "(EMPTY)"

    override val inputs: Iterable<MidiPortDetails>
        get() = arrayListOf(EmptyMidiInput.instance.details)
    override val outputs: Iterable<MidiPortDetails>
        get() = arrayListOf(EmptyMidiOutput.instance.details)

    override suspend fun openInput(portId: String): MidiInput {
        if (portId != input.details.id)
            throw IllegalArgumentException("Port ID $portId does not exist.")
        return input
    }

    override suspend fun openOutput(portId: String): MidiOutput {
        if (portId != output.details.id)
            throw IllegalArgumentException("Port ID $portId does not exist.")
        return output
    }
}

internal abstract class EmptyMidiPort : MidiPort {
    override val connectionState = MidiPortConnectionState.OPEN

    override fun close() {
        // do nothing.
    }
}

internal class EmptyMidiPortDetails(override val id: String, name: String) : MidiPortDetails {
    override val manufacturer = "ktmidi project"
    override val name: String? = name
    override val version = "0.0"
    override val midiTransportProtocol = 1
}

internal class EmptyMidiInput : EmptyMidiPort(), MidiInput {
    companion object {
        val instance = EmptyMidiInput()
    }

    override val details: MidiPortDetails = EmptyMidiPortDetails("empty_in", "Empty MIDI Input")

    override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
        // do nothing, it will receive nothing
    }
}

internal class EmptyMidiOutput : EmptyMidiPort(), MidiOutput {
    companion object {
        val instance = EmptyMidiOutput()
    }

    override fun send(mevent: ByteArray, offset: Int, length: Int, timestampInNanoseconds: Long) {
        // do nothing.
    }

    override val details: MidiPortDetails = EmptyMidiPortDetails("empty_out", "Empty MIDI Output")
}
