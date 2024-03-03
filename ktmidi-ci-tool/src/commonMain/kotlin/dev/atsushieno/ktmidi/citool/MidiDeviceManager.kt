package dev.atsushieno.ktmidi.citool

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import dev.atsushieno.ktmidi.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
//import kotlinx.coroutines.runBlocking

class MidiDeviceManager {
    private val emptyMidiAccess = EmptyMidiAccess()
    private var emptyMidiInput: MidiInput = EmptyMidiAccess.input
    private var emptyMidiOutput: MidiOutput = EmptyMidiAccess.output
    private var midiAccessValue: MidiAccess = emptyMidiAccess

    var midiAccess: MidiAccess
        get() = midiAccessValue
        set(value) {
            midiAccessValue = value
            midiInput = emptyMidiInput
            midiOutput = emptyMidiOutput
        }
    suspend fun setupVirtualPorts() {
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

    val midiInputDeviceId: String?
        get() = midiInput.details.id

    var midiInputOpened = mutableListOf<(input: MidiInput) -> Unit>()
    var midiOutputOpened = mutableListOf<(output: MidiOutput) -> Unit>()

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

    val midiOutputSent = mutableListOf<(bytes: ByteArray, timestamp: Long)->Unit>()

    suspend fun setInputDevice(id: String?) {
        midiInput.close()
        midiInput = if (id != null) midiAccessValue.openInput(id) else emptyMidiInput
        midiInputOpened.forEach { it(midiInput) }
    }

    suspend fun setOutputDevice(id: String) {
        midiOutput.close()
        midiOutput = midiAccessValue.openOutput(id)
        Snapshot.withMutableSnapshot {
            midiOutputError.value = null
            virtualMidiOutputError.value = null
        }
        midiOutputOpened.forEach { it(midiOutput) }
    }

    fun translateMidi1BytesToUmp(bytes: ByteArray, group: Byte): ByteArray =
        Midi1ToUmpTranslatorContext(bytes.toList(), group = group.toInt())
            .also { UmpTranslator.translateMidi1BytesToUmp(it) }
            .output.map { it.toPlatformNativeBytes() }.flatMap { it.toList() }.toByteArray()

    private fun MidiOutput.send(bytes: ByteArray, timestampInNanoseconds: Long) =
        send(bytes, 0, bytes.size, timestampInNanoseconds)

    fun sendToAll(group: Byte, bytes: ByteArray, timestampInNanoseconds: Long) {
        try {
            if (midiOutputError.value == null) {
                if (midiOutput.details.midiTransportProtocol == MidiTransportProtocol.UMP)
                    midiOutput.send(translateMidi1BytesToUmp(bytes, group), timestampInNanoseconds)
                else
                    midiOutput.send(bytes, 0, bytes.size, timestampInNanoseconds)
            }
        } catch (ex: Exception) {
            Snapshot.withMutableSnapshot { midiOutputError.value = ex }
        }
        try {
            if (virtualMidiOutputError.value == null && virtualMidiOutput != null) {
                if (virtualMidiOutput!!.details.midiTransportProtocol == MidiTransportProtocol.UMP)
                    virtualMidiOutput!!.send(translateMidi1BytesToUmp(bytes, group), timestampInNanoseconds)
                else
                    virtualMidiOutput!!.send(bytes, 0, bytes.size, timestampInNanoseconds)
            }
        } catch (ex: Exception) {
            Snapshot.withMutableSnapshot { virtualMidiOutputError.value = ex }
        }
        midiOutputSent.forEach { it(bytes, timestampInNanoseconds) }
    }

    init {
        midiInput = EmptyMidiAccess.input
        midiOutput = EmptyMidiAccess.output
    }
}
