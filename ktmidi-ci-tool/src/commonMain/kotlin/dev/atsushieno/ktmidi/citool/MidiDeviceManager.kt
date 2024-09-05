package dev.atsushieno.ktmidi.citool

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import dev.atsushieno.ktmidi.*

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
                applicationName = "KtMidi-CI-Tool V-In",
                portName = "KtMidi-CI-Tool Virtual Out Port",
                version = "1.0",
                midiProtocol = MidiTransportProtocol.MIDI1,
            )
            val pcIn = PortCreatorContext(
                manufacturer = "KtMidi project",
                applicationName = "KtMidi-CI-Tool V-Out",
                portName = "KtMidi-CI-Tool Virtual In Port",
                version = "1.0",
                midiProtocol = MidiTransportProtocol.MIDI1,
            )

            if (midiAccessValue.canCreateVirtualPort(pcIn) && midiAccessValue.canCreateVirtualPort(pcOut)) {
                virtualMidiInput = midiAccessValue.createVirtualOutputReceiver(pcOut)
                midiInputOpened.forEach { it(virtualMidiInput!!) }
                virtualMidiOutput = midiAccessValue.createVirtualInputSender(pcIn)
            }

            if (!midiAccessValue.supportsUmpTransport)
                return

            val pcOut2 = PortCreatorContext(
                manufacturer = "KtMidi project",
                applicationName = "KtMidi-CI-Tool UMP V-In",
                portName = "KtMidi-CI-Tool UMP Virtual Out Port",
                version = "1.0",
                midiProtocol = MidiTransportProtocol.UMP,
            )
            val pcIn2 = PortCreatorContext(
                manufacturer = "KtMidi project",
                applicationName = "KtMidi-CI-Tool UMP V-Out",
                portName = "KtMidi-CI-Tool UMP Virtual In Port",
                version = "1.0",
                midiProtocol = MidiTransportProtocol.UMP,
            )

            if (midiAccessValue.canCreateVirtualPort(pcIn2) && midiAccessValue.canCreateVirtualPort(pcOut2)) {
                virtualMidiInput2 = midiAccessValue.createVirtualOutputReceiver(pcOut2)
                midiInputOpened.forEach { it(virtualMidiInput2!!) }
                virtualMidiOutput2 = midiAccessValue.createVirtualInputSender(pcIn2)
            }
        } catch (ex: Exception) {
            println(ex)
            ex.printStackTrace()
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
    private var virtualMidiInput2: MidiInput? = null
    private var virtualMidiOutput2: MidiOutput? = null

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

    // `bytes` is a MIDI1 transport stream,
    // so every time it tries to send to UMP port it needs to be translated to UMP.
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
            if (virtualMidiOutput2?.details?.midiTransportProtocol == MidiTransportProtocol.UMP) {
                if (virtualMidiOutputError.value == null)
                    virtualMidiOutput2!!.send(translateMidi1BytesToUmp(bytes, group), timestampInNanoseconds)
            }
            if (virtualMidiOutput?.details?.midiTransportProtocol != MidiTransportProtocol.UMP) {
                if (virtualMidiOutputError.value == null)
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
