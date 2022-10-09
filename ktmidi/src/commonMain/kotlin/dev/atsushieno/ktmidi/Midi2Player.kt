package dev.atsushieno.ktmidi

import io.ktor.utils.io.core.*
import kotlinx.coroutines.Runnable

internal val Ump.metaEventType : Int
    get() = if (this.messageType != MidiMessageType.SYSEX8_MDS) 0 else (this.int3 shr 8) and 0x7F

internal class Midi2EventLooper(var messages: List<Ump>, private val timer: MidiPlayerTimer, private val deltaTimeSpec: Int)
    : MidiEventLooper<Ump>(timer) {
    override fun isEventIndexAtEnd() = eventIdx == messages.size

    override fun getNextMessage() = messages[eventIdx]

    override fun getContextDeltaTimeInSeconds(m: Ump): Double {
        return if (deltaTimeSpec > 0)
            if (m.isJRTimestamp) currentTempo.toDouble() / 1_000_000 * m.jrTimestamp / deltaTimeSpec / tempoRatio else 0.0
        else
            if (m.isJRTimestamp) m.jrTimestamp * 1.0 / 31250 else 0.0
    }

    override fun getDurationOfEvent(m: Ump) =
        if (m.isJRTimestamp)
            m.jrTimestamp
        else 0

    private val umpConversionBuffer = ByteArray(16)

    override fun updateTempoAndTimeSignatureIfApplicable(m: Ump) {
        if (m.messageType == MidiMessageType.SYSEX8_MDS && m.statusCode == Midi2BinaryChunkStatus.SYSEX_IN_ONE_UMP &&
            m.int2 % 0x100  == MidiMusic.META_EVENT) {
            when ((m.int3.toUnsigned() / 0x100 % 0x100).toInt()) {
                MidiMetaType.TEMPO -> {
                    m.toPlatformBytes(umpConversionBuffer, 0, ByteOrder.BIG_ENDIAN)
                    currentTempo = MidiMusic.getSmfTempo(umpConversionBuffer, 11)
                }
                MidiMetaType.TIME_SIGNATURE -> {
                    m.toPlatformBytes(umpConversionBuffer, 0, ByteOrder.BIG_ENDIAN)
                    currentTimeSignature.clear()
                    currentTimeSignature.addAll(umpConversionBuffer.drop(11).take(4))
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

        val umpTranslatorBuffer = MutableList<Byte>(16) { 0 }
        val umpConversionBuffer = ByteArray(16)
        val sysexBuffer = mutableListOf<Byte>()
        var lastMetaEventType = 0

        messages = music.mergeTracks().tracks[0].messages
        looper = Midi2EventLooper(messages, timer, music.deltaTimeSpec)
        looper.starting = Runnable {
            if (output.midiProtocol == MidiCIProtocolType.MIDI2) {
                for (group in 0..15) {
                    // all control reset on all channels.
                    for (ch in 0..15) {
                        val ump = Ump(UmpFactory.midi1CC(group, ch, MidiCC.RESET_ALL_CONTROLLERS.toByte(), 0))
                        ump.toPlatformBytes(umpConversionBuffer, 0)
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
                        if (output.midiProtocol != MidiCIProtocolType.MIDI2 && (e.statusCode == Midi2BinaryChunkStatus.SYSEX_START || e.statusCode == Midi2BinaryChunkStatus.SYSEX_IN_ONE_UMP)) {
                            sysexBuffer.clear()
                            sysexBuffer.add(0xF0.toByte())
                        }

                        if (output.midiProtocol != MidiCIProtocolType.MIDI2) {
                            // get sysex8 bytes in the right order
                            e.toPlatformBytes(umpConversionBuffer, 0, ByteOrder.BIG_ENDIAN)
                            sysexBuffer.addAll(umpConversionBuffer.drop(2).take(e.sysex7Size))
                        }
                        e.toPlatformBytes(umpConversionBuffer, 0)

                        if (output.midiProtocol == MidiCIProtocolType.MIDI2)
                            output.send(umpConversionBuffer, 0, e.sizeInBytes, 0)
                        else if (e.statusCode == Midi2BinaryChunkStatus.SYSEX_END || e.statusCode == Midi2BinaryChunkStatus.SYSEX_IN_ONE_UMP) {
                            sysexBuffer.add(0xF7.toByte())
                            output.send(sysexBuffer.toByteArray(), 0, sysexBuffer.size, 0)
                        }
                    }
                    MidiMessageType.SYSEX8_MDS -> {
                        // It is not supported for a device (port) that has midiProtocol as MIDI1 to accept Message Type 5.
                        // However it is possible that the UMP sequence contains meta events which are not sent to the device.
                        // Therefore, we throw UnsupportedOperationException only if it is NOT part of a meta event UMP.
                        if (e.statusCode == Midi2BinaryChunkStatus.SYSEX_START || e.statusCode == Midi2BinaryChunkStatus.SYSEX_IN_ONE_UMP) {
                            lastMetaEventType = if (Midi2Music.isMetaEventMessageStarter(e)) e.metaEventType else -1
                            if (lastMetaEventType >= 0)
                                sysexBuffer.clear()
                        }
                        if (lastMetaEventType < 0 && output.midiProtocol != MidiCIProtocolType.MIDI2)
                            throw UnsupportedOperationException("MIDI 2.0 SYSEX8/MDS are not supported on devices that only support MIDI 1.0 protocol.")

                        if (lastMetaEventType >= 0) {
                            // get sysex8 bytes in the right order
                            e.toPlatformBytes(umpConversionBuffer, 0, ByteOrder.BIG_ENDIAN)
                            sysexBuffer.addAll(umpConversionBuffer.drop(3).take(e.sysex8Size - 1)) // -1 is for streamId
                        }
                        e.toPlatformBytes(umpConversionBuffer, 0)

                        if (e.statusCode == Midi2BinaryChunkStatus.SYSEX_END || e.statusCode == Midi2BinaryChunkStatus.SYSEX_IN_ONE_UMP) {
                            when (lastMetaEventType) {
                                // FIXME: implement more meta events?
                                MidiMetaType.TEMPO, MidiMetaType.TIME_SIGNATURE -> looper.updateTempoAndTimeSignatureIfApplicable(e)
                                // for non-pseudo-meta events, just send it to the output. Note that MIDI1 output device does not support sysex8/mds.
                                -1 -> output.send(umpConversionBuffer, 0, e.sizeInBytes, 0)
                            }
                        }
                        else if (lastMetaEventType < 0) // send only non-meta events to device.
                            output.send(umpConversionBuffer, 0, e.sizeInBytes, 0)
                    }
                    MidiMessageType.MIDI2 -> {
                        if (output.midiProtocol == MidiCIProtocolType.MIDI2) {
                            e.toPlatformBytes(umpConversionBuffer, 0)
                            output.send(umpConversionBuffer, 0, e.sizeInBytes, 0)
                        }
                        else {
                            // send only if conversion was successful
                            val size = UmpTranslator.translateSingleUmpToMidi1Bytes(umpTranslatorBuffer, e, 0)
                            if (size > 0)
                                output.send(umpTranslatorBuffer.toByteArray(), 0, size, 0)
                        }
                    }
                    MidiMessageType.MIDI1, MidiMessageType.SYSTEM -> {
                        when (e.statusCode) {
                            MidiChannelStatus.NOTE_OFF,
                            MidiChannelStatus.NOTE_ON -> {
                                if (mutedChannels.contains(e.groupAndChannel))
                                    return // ignore messages for the masked channel.
                            }
                        }
                        if (output.midiProtocol == MidiCIProtocolType.MIDI2) {
                            e.toPlatformBytes(umpConversionBuffer, 0)
                            output.send(umpConversionBuffer, 0, e.sizeInBytes, 0)
                        } else {
                            // all control reset on all channels.
                            umpConversionBuffer[0] = e.statusByte.toByte()
                            umpConversionBuffer[1] = e.midi1Msb.toByte()
                            umpConversionBuffer[2] = e.midi1Lsb.toByte()
                            output.send(umpConversionBuffer, 0, MidiEvent.fixedDataSize(e.statusCode.toByte()).toInt(), 0)
                        }
                    }
                }
            }
        }
        addOnMessageListener(listener)
    }

    private val music: Midi2Music
    internal var messages: MutableList<Ump>
    override val looper: Midi2EventLooper

    fun addOnMessageListener(listener: OnMidi2EventListener) {
        looper.addOnMessageListener(listener)
    }

    fun removeOnMessageListener(listener: OnMidi2EventListener) {
        looper.removeOnMessageListener(listener)
    }

    override val positionInMilliseconds: Long
        get() = music.getTimePositionInMillisecondsForTick(playDeltaTime).toLong()

    override val totalPlayTimeMilliseconds: Int
        get() = Midi2Music.getTotalPlayTimeMilliseconds(messages, music.deltaTimeSpec)

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
        when (message.statusCode) {
            MidiChannelStatus.NOTE_ON, MidiChannelStatus.NOTE_OFF -> return SeekFilterResult.BLOCK
        }
        return SeekFilterResult.PASS
    }
}
