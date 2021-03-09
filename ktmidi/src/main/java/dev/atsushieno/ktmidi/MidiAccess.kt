package dev.atsushieno.ktmidi

class MidiAccessManager {
    companion object {
        var DEFAULT: MidiAccess = EmptyMidiAccess()
        var EMPTY: MidiAccess = DEFAULT
    }
}

interface MidiAccess {
    val inputs: Iterable<MidiPortDetails>
    val outputs: Iterable<MidiPortDetails>

    fun openInputAsync(portId: String): MidiInput
    fun openOutputAsync(portId: String): MidiOutput
}

/*
// In the future we could use default interface members, but we should target earlier frameworks in the meantime.
interface MidiAccess2 : MidiAccess
{
    var extensionManager : MidiAccessExtensionManager
}

abstract class MidiAccessExtensionManager
{
    abstract fun <T>getInstance () : T
}

class MidiConnectionStateDetectorExtension
{
    var stateChanged : Runnable<MidiConnectionEventArgs>? = null
}

abstract class MidiPortCreatorExtension
{
    abstract fun createInputPort (details: MidiPortDetails ):MidiInput
    abstract fun createOutputPort (details: MidiPortDetails ):MidiOutput
}

class MidiConnectionEventArgs
{
    var port : MidiPortDetails
}
*/

interface MidiPortDetails
{
    val id : String
    val manufacturer : String?
    val name : String?
    val version : String?
}

enum class MidiPortConnectionState
{
    OPEN,
    CLOSED,
    PENDING
}

interface MidiPort
{
    val details : MidiPortDetails
    val connection : MidiPortConnectionState
    fun close ()
}

interface OnMidiReceivedEventListener {
    fun onEventReceived (data : ByteArray, start: Int, length: Int, timestamp : Long)
}

interface MidiInput : MidiPort
{
    fun setMessageReceivedListener (listener: OnMidiReceivedEventListener)
}

interface MidiOutput : MidiPort
{
    fun send (mevent: ByteArray, offset : Int, length: Int, timestamp: Long)
}

class EmptyMidiAccess : MidiAccess
{
    override val inputs : Iterable<MidiPortDetails>
            get () = arrayListOf(EmptyMidiInput.instance.details)
    override val outputs : Iterable<MidiPortDetails>
            get () = arrayListOf(EmptyMidiOutput.instance.details)

    override fun openInputAsync(portId: String): MidiInput {
        if (portId != EmptyMidiInput.instance.details.id)
            throw IllegalArgumentException ("Port ID ${portId} does not exist.")
        return EmptyMidiInput.instance
    }

    override fun openOutputAsync(portId: String): MidiOutput {
        if (portId != EmptyMidiOutput.instance.details.id)
            throw IllegalArgumentException ("Port ID ${portId} does not exist.")
        return EmptyMidiOutput.instance
    }

    //override var stateChanged : Runnable<MidiConnectionEventArgs> = null
}

abstract class EmptyMidiPort : MidiPort
{
    override val details = createDetails ()

    internal abstract fun createDetails () : MidiPortDetails

    override val connection = MidiPortConnectionState.OPEN

    override fun close() {
        // do nothing.
    }
}

class EmptyMidiPortDetails(override val id: String, name: String) : MidiPortDetails
{
    override val manufacturer = "dummy project"
    override val name : String? = name
    override val version = "0.0"
}

class EmptyMidiInput : EmptyMidiPort(), MidiInput
{
    companion object {
        var instance = EmptyMidiInput ()
    }

    override fun createDetails ():MidiPortDetails
    {
        return EmptyMidiPortDetails ("dummy_in", "Dummy MIDI Input")
    }

    override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
        // do nothing, it will receive nothing
    }
}

class EmptyMidiOutput : EmptyMidiPort(), MidiOutput
{
    companion object {
        var instance = EmptyMidiOutput()
    }

    override fun send (mevent: ByteArray, offset:Int, length:Int, timestamp:Long) {
        // do nothing.
    }

    override fun createDetails ():MidiPortDetails
    {
        return EmptyMidiPortDetails ("dummy_out", "Dummy MIDI Output")
    }
}

