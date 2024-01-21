package dev.atsushieno.ktmidi.citool

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import dev.atsushieno.ktmidi.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
//import kotlinx.coroutines.runBlocking

class MidiDeviceManager {
    private val emptyMidiAccess = EmptyMidiAccess()
    private lateinit var emptyMidiInput: MidiInput
    private lateinit var emptyMidiOutput: MidiOutput
    private var midiAccessValue: MidiAccess = emptyMidiAccess

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
                    midiInputOpened.forEach { it(virtualMidiInput!!) }

                    virtualMidiOutput = midiAccessValue.createVirtualInputSender(pcIn)
                } catch (_: Exception) {
                }
            }
        }

    var midiInputDeviceId: String?
        get() = midiInput.details.id
        set(id) {
            GlobalScope.launch {
                midiInput.close()
                midiInput = if (id != null) midiAccessValue.openInput(id) else emptyMidiInput
                midiInputOpened.forEach { it(midiInput) }
            }
        }

    var midiOutputDeviceId: String?
        get() = midiOutput.details.id
        set(id) {
            // FIXME: it used to be runBlocking(), but replaced for wasmJs support.
            //  We would probably depend on GUI coroutine context
            GlobalScope.launch {
                midiOutput.close()
                midiOutput = if (id != null) midiAccessValue.openOutput(id) else emptyMidiOutput
                Snapshot.withMutableSnapshot {
                    midiOutputError.value = null
                    virtualMidiOutputError.value = null
                }
                midiOutputOpened.forEach { it(midiOutput) }
            }
        }

    var midiInputOpened = mutableListOf<(input: MidiInput) -> Unit>()
    var midiOutputOpened = mutableListOf<(output: MidiOutput) -> Unit>()

    lateinit var midiInput: MidiInput
    lateinit var midiOutput: MidiOutput

    // Ideally, there should be distinct pair of virtual ports for Initiator
    // and Responder, but on some MidiAccess backends (namely RtMidi) creating
    // multiple virtual ins or outs results in native crashes (hard to investigate).
    // Therefore, we simply use the same virtual in and out for both purposes.
    private var virtualMidiInput: MidiInput? = null
    private var virtualMidiOutput: MidiOutput? = null

    private var midiOutputError = mutableStateOf<Exception?>(null)
    private var virtualMidiOutputError = mutableStateOf<Exception?>(null)

    val midiOutputSent = mutableListOf<(bytes: ByteArray, timestamp: Long)->Unit>()

    fun sendToAll(bytes: ByteArray, timestamp: Long) {
        try {
            if (midiOutputError.value == null)
                midiOutput.send(bytes, 0, bytes.size, timestamp)
        } catch (ex: Exception) {
            Snapshot.withMutableSnapshot { midiOutputError.value = ex }
        }
        try {
            if (virtualMidiOutputError.value == null)
                virtualMidiOutput?.send(bytes, 0, bytes.size, timestamp)
        } catch (ex: Exception) {
            Snapshot.withMutableSnapshot { virtualMidiOutputError.value = ex }
        }
        midiOutputSent.forEach { it(bytes, timestamp) }
    }

    init {
        emptyMidiInput = EmptyMidiAccess.input
        emptyMidiOutput = EmptyMidiAccess.output
        midiInput = EmptyMidiAccess.input
        midiOutput = EmptyMidiAccess.output
    }
}
