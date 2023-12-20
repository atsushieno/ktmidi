package dev.atsushieno.ktmidi.citool

import androidx.compose.runtime.mutableStateOf
import dev.atsushieno.ktmidi.*
import dev.atsushieno.ktmidi.ci.MidiCIConstants
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MidiDeviceManager {
    var isResponder = false

    private val emptyMidiAccess = EmptyMidiAccess()
    private val emptyMidiInput: MidiInput
    private val emptyMidiOutput: MidiOutput
    private var midiAccessValue: MidiAccess = emptyMidiAccess

    val initiator = CIInitiatorModel { data ->
        val midi1Bytes = listOf(Midi1Status.SYSEX.toByte()) + data + listOf(Midi1Status.SYSEX_END.toByte())
        sendToAll(midi1Bytes.toByteArray(), 0)
    }
    private val responder = CIResponderModel { data ->
        val midi1Bytes = listOf(Midi1Status.SYSEX.toByte()) + data + listOf(Midi1Status.SYSEX_END.toByte())
        sendToAll(midi1Bytes.toByteArray(), 0)
    }

    var midiAccess: MidiAccess
        get() = midiAccessValue
        set(value) {
            midiAccessValue = value
            midiInput = emptyMidiInput
            midiOutput = emptyMidiOutput
            GlobalScope.launch {
                try {
                    val pcOut = PortCreatorContext(
                        manufacturer = "KtMidi project",
                        applicationName = "KtMidi-CI-Tool",
                        portName = "KtMidi-CI-Tool Virtual Out Port",
                        version = "1.0"
                        //midiProtocol = MidiCIProtocolType.MIDI2, // if applicable
                        //umpGroup = 2
                    )
                    val pcIn = PortCreatorContext(
                        manufacturer = "KtMidi project",
                        applicationName = "KtMidi-CI-Tool",
                        portName = "KtMidi-CI-Tool Virtual In Port",
                        version = "1.0"
                        //midiProtocol = MidiCIProtocolType.MIDI2, // if applicable
                        //umpGroup = 2
                    )

                    virtualMidiInput = midiAccessValue.createVirtualOutputReceiver(pcOut)
                    setupInputEventListener(virtualMidiInput!!)

                    virtualMidiOutput = midiAccessValue.createVirtualInputSender(pcIn)
                } catch (_: Exception) {
                }
            }
        }

    private fun setupInputEventListener(input: MidiInput) {
        input.setMessageReceivedListener { data, start, length, _ ->
            if (data.size > 3 &&
                data[start] == Midi1Status.SYSEX.toByte() &&
                data[start + 1] == MidiCIConstants.UNIVERSAL_SYSEX &&
                data[start + 3] == MidiCIConstants.UNIVERSAL_SYSEX_SUB_ID_MIDI_CI) {
                // it is a MIDI-CI message
                // FIXME: maybe make it exclusive?
                if (isResponder)
                    responder.processCIMessage(data.drop(start + 1).take(length - 2))
                else
                    initiator.processCIMessage(data.drop(start + 1).take(length - 2))
                return@setMessageReceivedListener
            }
        }
    }

    val midiInputPorts : Iterable<MidiPortDetails>
        get() = midiAccess.inputs
    val midiOutputPorts : Iterable<MidiPortDetails>
        get() = midiAccess.outputs

    var midiInputDeviceId: String?
        get() = midiInput.details.id
        set(id) {
            runBlocking {
                midiInput.close()
                midiInput = if (id != null) midiAccessValue.openInput(id) else emptyMidiInput
                midiInputOpened(midiInput)
            }
        }

    var midiOutputDeviceId: String?
        get() = midiOutput.details.id
        set(id) {
            runBlocking {
                midiOutput.close()
                midiOutput = if (id != null) midiAccessValue.openOutput(id) else emptyMidiOutput
                midiOutputError.value = null
                virtualMidiOutputError.value = null
                midiOutputOpened(midiOutput)
            }
        }

    var midiInputOpened : (input: MidiInput) -> Unit = {
        setupInputEventListener(midiInput)
    }
    var midiOutputOpened : (output: MidiOutput) -> Unit = {}

    var midiInput: MidiInput
    var midiOutput: MidiOutput

    // Ideally, there should be distinct pair of virtual ports for Initiator
    // and Responder, but on some MidiAccess backends (namely RtMidi) creating
    // multiple virtual ins or outs results in native crashes (hard to investigate).
    // Therefore, we simply use the same virtual in and out for both purposes.
    private var virtualMidiInput: MidiInput? = null
    private var virtualMidiOutput: MidiOutput? = null

    private var midiOutputError = mutableStateOf<Exception?>(null)
    private var virtualMidiOutputError = mutableStateOf<Exception?>(null)

    fun sendToAll(bytes: ByteArray, timestamp: Long) {
        try {
            if (midiOutputError.value == null)
                midiOutput.send(bytes, 0, bytes.size, timestamp)
        } catch (ex: Exception) {
            midiOutputError.value = ex
        }
        try {
            if (virtualMidiOutputError.value == null)
                virtualMidiOutput?.send(bytes, 0, bytes.size, timestamp)
        } catch (ex: Exception) {
            virtualMidiOutputError.value = ex
        }
    }

    init {
        // suppress warnings (for lack of initialization; runBlocking{} is not regarded as completing synchronously)
        var i: MidiInput? = null
        var o: MidiOutput? = null
        runBlocking {
            i = emptyMidiAccess.openInput(emptyMidiAccess.inputs.first().id)
            o = emptyMidiAccess.openOutput(emptyMidiAccess.outputs.first().id)
        }
        emptyMidiInput = i!!
        emptyMidiOutput = o!!
        midiInput = emptyMidiInput
        midiOutput = emptyMidiOutput
    }
}
