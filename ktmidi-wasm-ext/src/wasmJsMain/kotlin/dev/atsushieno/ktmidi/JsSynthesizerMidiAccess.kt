package dev.atsushieno.ktmidi

//import org.jetbrains.compose.resources.InternalResourceApi
//import org.jetbrains.compose.resources.readResourceBytes
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array

private fun createSynthesizer(): ISynthesizer = js("new JSSynth.Synthesizer()")
private fun waitForReady(runnable: ()->Unit): Nothing = js("JSSynth.waitForReady().then(runnable)")

class JsSynthesizerMidiAccess(private val sfBinary: ArrayBuffer, sfName: String) : MidiAccess() {
    companion object {

        /*
        @Suppress("CAST_NEVER_SUCCEEDS")
        @OptIn(InternalResourceApi::class)
        suspend fun createDefault() =
            create(readResourceBytes("FluidR3_GM.sf2") as ArrayBuffer, "FluidR3_GM.sf2")
        */

        fun create(sfBinary: ArrayBuffer, sfName: String): JsSynthesizerMidiAccess {
            var synth: JsSynthesizerMidiAccess? = null
            println("!!! JsSynthesizerMidiAccess.create() start.")
            try {
                waitForReady {
                    println("!!! in waitForReady().")
                    synth = JsSynthesizerMidiAccess(sfBinary, sfName)
                    println("!!! waitForReady() done.")
                }
                println("!!! JsSynthesizerMidiAccess.create() done.")
            } catch (ex: Exception) {
                println(ex)
                throw ex
            }
            return synth!!
        }
    }

    private val details = JsSynthesizerMidiOutputDetails(sfName)

    override val name: String
        get() = "JsSynthesizer"
    override val inputs: Iterable<MidiPortDetails>
        get() = listOf()
    override val outputs: Iterable<MidiPortDetails>
        get() = listOf(details)

    override suspend fun openInput(portId: String): MidiInput {
        throw UnsupportedOperationException()
    }

    override suspend fun openOutput(portId: String): MidiOutput {
        val synth = createSynthesizer()
        synth.init(44100.0)
        if (sfBinary.byteLength > 0)
            synth.loadSFont(sfBinary)
        return JsSynthesizerMidiOutput(details, synth)
    }

    internal class JsSynthesizerMidiOutputDetails(
        private val soundFontName: String,
        override val version: String = "1.0"
    ) : MidiPortDetails {
        override val id: String = soundFontName
        override val manufacturer: String
            get() = "JsSynthesizerMidiAccess"
        override val name: String
            get() = "js-synthesizer: $soundFontName"
        override val midiTransportProtocol: Int
            get() = MidiTransportProtocol.MIDI1
    }

    internal class JsSynthesizerMidiOutput(
        override val details: MidiPortDetails,
        private val synth: ISynthesizer
    ) : MidiOutput {
        private var state = MidiPortConnectionState.OPEN

        override fun send(mevent: ByteArray, offset: Int, length: Int, timestampInNanoseconds: Long) {
            Midi1Message.convert(mevent, offset, length, Midi1SysExChunkProcessor()).onEach {
                when(it.statusCode.toInt()) {
                    MidiChannelStatus.NOTE_OFF -> synth.midiNoteOff(it.channel, it.msb)
                    MidiChannelStatus.NOTE_ON -> synth.midiNoteOn(it.channel, it.msb, it.lsb)
                    MidiChannelStatus.PAF -> synth.midiKeyPressure(it.channel, it.msb, it.lsb)
                    MidiChannelStatus.CC -> synth.midiControl(it.channel, it.msb, it.lsb)
                    MidiChannelStatus.PROGRAM -> synth.midiProgramChange(it.channel, it.msb)
                    MidiChannelStatus.CAF -> synth.midiChannelPressure(it.channel, it.msb)
                    MidiChannelStatus.PITCH_BEND -> synth.midiPitchBend(it.channel, (it.msb * 128 + it.lsb).toShort())
                    else ->
                        if (it.statusByte.toInt() == Midi1Status.SYSEX)
                            synth.midiSysEx((it as Midi1CompoundMessage).extraData!! as Uint8Array)
                }
            }
        }

        override val connectionState: MidiPortConnectionState
            get() = state

        override fun close() {
            synth.close()
            state = MidiPortConnectionState.CLOSED
        }
    }
}
