package dev.atsushieno.ktmidi

import kotlinx.coroutines.Runnable

internal class Midi2EventLooper(var messages: List<Ump>, private val timer: MidiPlayerTimer, private val deltaTimeSpec: Int)
    : MidiEventLooper<Ump>(timer) {
    override fun isEventIndexAtEnd() = eventIdx == messages.size

    override fun getNextMessage() = messages[eventIdx]

    override fun getContextDeltaTimeInSeconds(m: Ump): Double {
        if (deltaTimeSpec > 0)
            return if(m.isJRTimestamp) currentTempo.toDouble() / 1_000_000 * m.jrTimestamp / tempoRatio else 0.0
        else
            TODO("Not implemented") // simpler JR Timestamp though
    }

    override fun getDurationOfEvent(m: Ump) = if (m.isJRTimestamp) m.jrTimestamp else 0

    private val umpConversionBuffer = ByteArray(16)

    override fun updateTempoAndTimeSignatureIfApplicable(m: Ump) {
        if (m.messageType == MidiMessageType.SYSEX8_MDS && m.eventType == Midi2BinaryChunkStatus.SYSEX_IN_ONE_UMP &&
            m.int2 % 0x100  == MidiMusic.META_EVENT) {
            when ((m.int3 / 0x100_00_00)) {
                MidiMetaType.TEMPO -> {
                    m.copyInto(umpConversionBuffer, 0)
                    currentTempo = MidiMusic.getSmfTempo(umpConversionBuffer, 12)
                }
                MidiMetaType.TIME_SIGNATURE -> {
                    m.copyInto(umpConversionBuffer, 0)
                    currentTimeSignature.clear()
                    currentTimeSignature.addAll(umpConversionBuffer.drop(12).take(4))
                }
                else -> {}
            }
        }
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
        // all sound off on all channels.
        for (group in 0..15)
            for (ch in 0..15)
                onEvent(Ump(UmpFactory.midi1CC(group, ch, MidiCC.ALL_SOUND_OFF.toByte(), 0)))
    }
}

interface OnMidi2EventListener {
    fun onEvent(e: Ump)
}

// Provides asynchronous player control.
class Midi2Player : MidiPlayer {
    companion object {
        suspend fun create(music: Midi2Music, access: MidiAccess, timer: MidiPlayerTimer = SimpleAdjustingMidiPlayerTimer()) =
            Midi2Player(music, access.openOutputAsync(access.outputs.first().id), timer, true)
    }

    constructor(music: Midi2Music, output: MidiOutput, timer: MidiPlayerTimer = SimpleAdjustingMidiPlayerTimer(), shouldDisposeOutput: Boolean = false)
            : super(output, shouldDisposeOutput) {
        this.music = music

        val umpConversionBuffer = ByteArray(16)

        messages = music.mergeTracks().tracks[0].messages
        looper = Midi2EventLooper(messages, timer, music.deltaTimeSpec)
        looper.starting = Runnable {
            if (output.midiProtocol == MidiCIProtocolValue.MIDI2_V1) {
                for (group in 0..15) {
                    // all control reset on all channels.
                    for (ch in 0..15) {
                        val ump = Ump(UmpFactory.midi1CC(group, ch, MidiCC.RESET_ALL_CONTROLLERS.toByte(), 0))
                        ump.copyInto(umpConversionBuffer, 0)
                        output.send(umpConversionBuffer, 0, ump.sizeInBytes, 0)
                    }
                }
            } else {
                // all control reset on all channels.
                for (i in 0..15) {
                    umpConversionBuffer[0] = (i + MidiChannelStatus.CC).toByte()
                    umpConversionBuffer[1] = MidiCC.RESET_ALL_CONTROLLERS.toByte()
                    umpConversionBuffer[2] = 0
                    output.send(umpConversionBuffer, 0, 3, 0)
                }
            }
        }

        val listener = object : OnMidi2EventListener {
            override fun onEvent(e: Ump) {
                when (e.messageType) {
                    MidiMessageType.SYSEX7 -> {
                        when (e.eventType) {
                            Midi2BinaryChunkStatus.SYSEX_IN_ONE_UMP -> {
                                if (output.midiProtocol == MidiCIProtocolValue.MIDI2_V1) {
                                    e.copyInto(umpConversionBuffer, 0)
                                    output.send(umpConversionBuffer, 0, e.sizeInBytes, 0)
                                }
                                else {
                                    e.copyInto(umpConversionBuffer, 0)
                                    umpConversionBuffer[1] = 0xF0.toByte()
                                    // FIXME: verify size and content
                                    output.send(umpConversionBuffer, 1, e.sizeInBytes, 0)
                                }
                            }
                            else -> TODO("Longer sysex Not yet implemented")
                        }
                    }
                    MidiMessageType.SYSEX8_MDS -> {
                        when (e.eventType) {
                            Midi2BinaryChunkStatus.SYSEX_IN_ONE_UMP -> {
                                if (output.midiProtocol == MidiCIProtocolValue.MIDI2_V1) {
                                    e.copyInto(umpConversionBuffer, 0)
                                    output.send(umpConversionBuffer, 0, e.sizeInBytes, 0)
                                }
                                else
                                    throw UnsupportedOperationException("MIDI 2.0 SYSEX8/MDS not supported on devices that only support MIDI 1.0 protocol.")
                            }
                            else -> TODO("Longer sysex Not yet implemented")
                        }
                    }
                    MidiMessageType.MIDI2 -> {
                        if (output.midiProtocol == MidiCIProtocolValue.MIDI2_V1) {
                            e.copyInto(umpConversionBuffer, 0)
                            output.send(umpConversionBuffer, 0, e.sizeInBytes, 0)
                        }
                        else
                            TODO("Not implemented")
                    }
                    MidiMessageType.MIDI1 -> {
                        when (e.eventType) {
                            MidiChannelStatus.NOTE_OFF,
                            MidiChannelStatus.NOTE_ON -> {
                                if (mutedChannels.contains(e.groupAndChannel))
                                    return // ignore messages for the masked channel.
                            }
                        }
                        if (output.midiProtocol == MidiCIProtocolValue.MIDI2_V1) {
                            e.copyInto(umpConversionBuffer, 0)
                            output.send(umpConversionBuffer, 0, e.sizeInBytes, 0)
                        } else {
                            // all control reset on all channels.
                            umpConversionBuffer[0] = e.statusByte.toByte()
                            umpConversionBuffer[1] = e.midi1Msb.toByte()
                            umpConversionBuffer[2] = e.midi1Lsb.toByte()
                            output.send(umpConversionBuffer, 0, 3, 0)
                        }
                    }
                }
            }
        }
        addOnMessageListener(listener)
    }

    private val music: Midi2Music
    internal lateinit var messages: MutableList<Ump>

    fun addOnMessageListener(listener: OnMidi2EventListener) {
        (looper as Midi2EventLooper).addOnMessageListener(listener)
    }

    fun removeOnMessageListener(listener: OnMidi2EventListener) {
        (looper as Midi2EventLooper).removeOnMessageListener(listener)
    }

    override val positionInMilliseconds: Long
        get() = music.getTimePositionInMillisecondsForTick(playDeltaTime).toLong()

    override val totalPlayTimeMilliseconds: Int
        get() = Midi2Music.getTotalPlayTimeMilliseconds(messages, music.deltaTimeSpec)

    override fun seek(ticks: Int) {
        (looper as MidiEventLooper<Ump>).seek(Midi2SimpleSeekProcessor(ticks), ticks)
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
        when (message.eventType) {
            MidiChannelStatus.NOTE_ON, MidiChannelStatus.NOTE_OFF -> return SeekFilterResult.BLOCK
        }
        return SeekFilterResult.PASS
    }
}
