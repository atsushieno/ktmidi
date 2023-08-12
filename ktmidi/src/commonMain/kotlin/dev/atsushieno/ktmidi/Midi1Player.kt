package dev.atsushieno.ktmidi

import kotlinx.coroutines.Runnable

internal class Midi1EventLooper(var messages: List<Midi1Event>, private val timer: MidiPlayerTimer,
                                private val deltaTimeSpec: Int) : MidiEventLooper<Midi1Event>(timer) {
    override fun getContextDeltaTimeInSeconds(m: Midi1Event): Double {
        return if (deltaTimeSpec < 0)
            Midi1Music.getSmpteDurationInSeconds(deltaTimeSpec, m.deltaTime, currentTempo, tempoRatio)
        else
            currentTempo.toDouble() / 1_000_000 * m.deltaTime / deltaTimeSpec / tempoRatio
    }

    override fun getDurationOfEvent(m: Midi1Event) = m.deltaTime

    override fun isEventIndexAtEnd(): Boolean = eventIdx == messages.size

    override fun getNextEvent(): Midi1Event = messages[eventIdx]

    override fun updateTempoAndTimeSignatureIfApplicable(m: Midi1Event) {
        if (m.message.statusByte.toUnsigned() == 0xFF) {
            val e = m.message as Midi1CompoundMessage
            if (e.msb.toInt() == MidiMetaType.TEMPO)
                currentTempo = Midi1Music.getSmfTempo(e.extraData!!, e.extraDataOffset)
            else if (e.msb.toInt() == MidiMetaType.TIME_SIGNATURE && e.extraDataLength == 4) {
                currentTimeSignature.clear()
                currentTimeSignature.addAll(e.extraData!!.drop(e.extraDataOffset).take(e.extraDataLength))
            }
        }
    }

    val messageHandlers = mutableListOf<OnMidi1EventListener>()

    override fun onEvent(m: Midi1Event) {
        for (er in messageHandlers)
            er.onEvent(m)

    }

    override fun mute() {
        for (i in 0..15)
            onEvent(Midi1Event(0, Midi1SimpleMessage(i + MidiChannelStatus.CC, MidiCC.ALL_SOUND_OFF, 0)))
    }
}

fun interface OnMidi1EventListener {
    fun onEvent(m: Midi1Event)
}

@Deprecated("Use OnMidi1EventListener")
fun interface OnMidiMessageListener {
    fun onMessage(m: MidiMessage)
}


// Provides asynchronous player control.
class Midi1Player : MidiPlayer {
    companion object {
        suspend fun create(music: Midi1Music, access: MidiAccess, timer: MidiPlayerTimer = SimpleAdjustingMidiPlayerTimer()) =
            Midi1Player(music, access.openOutput(access.outputs.first().id), timer, true)
    }

    constructor(music: Midi1Music, output: MidiOutput, timer: MidiPlayerTimer = SimpleAdjustingMidiPlayerTimer(), shouldDisposeOutput: Boolean = false)
        : super(output, shouldDisposeOutput) {

        this.music = music
        events = music.mergeTracks().tracks[0].events
        looper = Midi1EventLooper(events, timer, music.deltaTimeSpec)

        looper.starting = Runnable {
            // all control reset on all channels.
            for (i in 0..15) {
                buffer[0] = (i + MidiChannelStatus.CC).toByte()
                buffer[1] = MidiCC.RESET_ALL_CONTROLLERS.toByte()
                buffer[2] = 0
                output.send(buffer, 0, 3, 0)
            }
        }

        val listener = object : OnMidi1EventListener {
            override fun onEvent(e: Midi1Event) {
                val m = e.message
                when (m.statusCode.toUnsigned()) {
                    MidiChannelStatus.NOTE_OFF,
                    MidiChannelStatus.NOTE_ON -> {
                        if (mutedChannels.contains(m.channel.toUnsigned()))
                            return // ignore messages for the masked channel.
                    }
                    Midi1Status.SYSEX -> {
                        val s = m as Midi1CompoundMessage
                        if (buffer.size <= m.extraDataLength + 1) // +1 for possible F7 filling
                            buffer = ByteArray(buffer.size * 2)
                        buffer[0] = m.statusByte
                        m.extraData!!.copyInto(buffer, 1, s.extraDataOffset, s.extraDataLength)
                        if (m.extraData[s.extraDataOffset + s.extraDataLength - 1] != 0xF7.toByte())
                            buffer[s.extraDataOffset + s.extraDataLength + 1] = 0xF7.toByte()
                        output.send(buffer, 0, s.extraDataLength + 2, 0)
                        return
                    }
                    Midi1Status.SYSEX_END -> {
                        // do nothing. It is automatically filled
                        return
                    }
                    Midi1Status.META -> {
                        // do nothing.
                        return
                    }
                }
                val size = Midi1Message.fixedDataSize(m.statusByte)
                buffer[0] = m.statusByte
                buffer[1] = m.msb
                buffer[2] = m.lsb
                output.send(buffer, 0, size + 1, 0)
            }
        }
        addOnEventListener(listener)
    }

    private val music: Midi1Music
    private var buffer = ByteArray(0x100)
    internal var events: MutableList<Midi1Event>
    final override val looper: Midi1EventLooper

    fun addOnEventListener(listener: OnMidi1EventListener) {
        looper.messageHandlers.add(listener)
    }

    fun removeOnEventListener(listener: OnMidi1EventListener) {
        looper.messageHandlers.remove(listener)
    }

    override val positionInMilliseconds: Long
        get() = music.getTimePositionInMillisecondsForTick(playDeltaTime).toLong()

    override val totalPlayTimeMilliseconds: Int
        get() = Midi1Music.getTotalPlayTimeMilliseconds(events, music.deltaTimeSpec)

    override fun seek(ticks: Int) {
        looper.seek(SimpleMidi1SeekProcessor(ticks), ticks)
    }

    override fun setMutedChannels(mutedChannels: Iterable<Int>) {
        this.mutedChannels = mutedChannels.toList()
        // additionally send all sound off for the muted channels.
        for (ch in 0..15)
            if (!mutedChannels.contains(ch))
                output.send(arrayOf((0xB0 + ch).toByte(), 120, 0).toByteArray(), 0, 3, 0)
    }
}

internal class SimpleMidi1SeekProcessor(ticks: Int) : SeekProcessor<Midi1Event> {
    private var seekTo: Int = ticks
    private var current: Int = 0

    override fun filterEvent(evt: Midi1Event): SeekFilterResult {
        current += evt.deltaTime
        if (current >= seekTo)
            return SeekFilterResult.PASS_AND_TERMINATE
        when (evt.message.statusCode.toUnsigned()) {
            MidiChannelStatus.NOTE_ON, MidiChannelStatus.NOTE_OFF -> return SeekFilterResult.BLOCK
        }
        return SeekFilterResult.PASS
    }
}
