package dev.atsushieno.ktmidi

import kotlinx.coroutines.Runnable

internal class Midi2EventLooper(var messages: List<Ump>, private val timer: MidiPlayerTimer) : MidiEventLooper<Ump>(timer) {
    override fun isEventIndexAtEnd() = eventIdx == messages.size

    override fun getNextMessage() = messages[eventIdx]

    override fun getContextDeltaTimeInMilliseconds(m: Ump): Int {
        // FIXME: it is a fake implementation, not the right calculation
        return if(m.isJRTimestamp) (currentTempo.toDouble() / 1000 * m.jrTimestamp / tempoRatio).toInt() else 0
    }

    override fun getDurationOfEvent(m: Ump) = if (m.isJRTimestamp) m.jrTimestamp else 0

    override fun updateTempoAndTimeSignatureIfApplicable(m: Ump) {
        TODO("Not yet implemented")
    }

    private val messageHandlers = mutableListOf<OnMidi2EventListener>()

    fun addOnMessageListener(listener: OnMidi2EventListener) {
        messageHandlers.add(listener)
    }

    fun removeOnMessageListener(listener: OnMidi2EventListener) {
        messageHandlers.remove(listener)
    }

    override fun onEvent(m: Ump) {
        // Here we do not filter out JR Timetamp messages, as the receivers might want to know the expected time value.
        for (er in messageHandlers)
            er.onEvent(m)

    }

    override fun mute() {
        TODO("Not yet implemented")
    }
}

interface OnMidi2EventListener {
    fun onEvent(e: Ump)
}

// Provides asynchronous player control.
class Midi2Player : MidiPlayerCommon<Ump> {
    constructor(music: Midi2Music, access: MidiAccess, timer: MidiPlayerTimer = SimpleAdjustingMidiPlayerTimer())
            : this(music, access.openOutputAsync(access.outputs.first().id), timer, true)

    constructor(music: Midi2Music, output: MidiOutput, timer: MidiPlayerTimer = SimpleAdjustingMidiPlayerTimer(), shouldDisposeOutput: Boolean = false)
            : super(output, shouldDisposeOutput, timer) {
        this.music = music

        messages = Midi2TrackMerger.merge(music.tracks).tracks[0].messages
        looper = Midi2EventLooper(messages, timer)
        looper.starting = Runnable {
            // FIXME: implement
            TODO("Not yet implemented")
            /*
            // all control reset on all channels.
            for (i in 0..15) {
                buffer[0] = (i + 0xB0).toByte()
                buffer[1] = 0x79
                buffer[2] = 0
                output.send(buffer, 0, 3, 0)
            }
            */
        }

        val listener = object : OnMidi2EventListener {
            override fun onEvent(e: Ump) {
                when (e.eventType.toByte()) {
                    MidiEventType.NOTE_OFF,
                    MidiEventType.NOTE_ON -> {
                        if (mutedChannels.contains(e.groupAndChannel))
                            return // ignore messages for the masked channel.
                    }
                    MidiEventType.SYSEX,
                    MidiEventType.SYSEX_END -> {
                        // FIXME: implement here
                        TODO("Not yet implemented")
                        /*
                        if (buffer.size <= e.extraDataLength)
                            buffer = ByteArray(buffer.size * 2)
                        buffer[0] = e.statusByte
                        e.extraData!!.copyInto(buffer, 1, e.extraDataOffset, e.extraDataLength - 1)
                        output.send(buffer, 0, e.extraDataLength + 1, 0)
                        */
                        return
                    }
                    MidiEventType.META -> {
                        // do nothing.
                        return
                    }
                }
                // FIXME: implement
                TODO("Not yet implemented")
                /*
                val size = MidiEvent.fixedDataSize(e.statusByte)
                buffer[0] = e.statusByte
                buffer[1] = e.msb
                buffer[2] = e.lsb
                output.send(buffer, 0, size + 1, 0)
                */
            }
        }
        addOnMessageListener(listener)
    }

    private val music: Midi2Music

    fun addOnMessageListener(listener: OnMidi2EventListener) {
        (looper as Midi2EventLooper).addOnMessageListener(listener)
    }

    fun removeOnMessageListener(listener: OnMidi2EventListener) {
        (looper as Midi2EventLooper).removeOnMessageListener(listener)
    }

    override val positionInMilliseconds: Long
        get() = music.getTimePositionInMillisecondsForTick(playDeltaTime).toLong()

    override val totalPlayTimeMilliseconds: Int
        get() = Midi2Music.getTotalPlayTimeMilliseconds(messages)

    override fun seek(ticks: Int) {
        looper.seek(Midi2SimpleSeekProcessor(ticks), ticks)
    }

    override fun setMutedChannels(mutedChannels: Iterable<Int>) {
        this.mutedChannels = mutedChannels.toList()
        // additionally send all sound off for the muted channels.
        TODO("Not implemented")
    }
}

internal class Midi2SimpleSeekProcessor(ticks: Int) : SeekProcessor<Ump> {
    private var seekTo: Int = ticks
    private var current: Int = 0

    override fun filterMessage(message: Ump): SeekFilterResult {
        current += if(message.isJRTimestamp) message.jrTimestamp else 0
        if (current >= seekTo)
            return SeekFilterResult.PASS_AND_TERMINATE
        when (message.eventType.toByte()) {
            MidiEventType.NOTE_ON, MidiEventType.NOTE_OFF -> return SeekFilterResult.BLOCK
        }
        return SeekFilterResult.PASS
    }
}
