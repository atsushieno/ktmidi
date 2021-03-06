package dev.atsushieno.ktmidi

import kotlinx.coroutines.Runnable
import kotlinx.coroutines.sync.Semaphore
import kotlin.jvm.Volatile

internal class Midi1EventLooper(var messages: List<MidiMessage>, private val timer: MidiPlayerTimer,
                               private val deltaTimeSpec: Int) : MidiEventLooper<MidiMessage>(timer) {
    init {
        if (deltaTimeSpec < 0)
            throw UnsupportedOperationException("SMPTe-based delta time is not implemented in this player.")
    }

    override fun getContextDeltaTimeInSeconds(m: MidiMessage): Double {
        return currentTempo.toDouble() / 1_000_000 * m.deltaTime / deltaTimeSpec / tempoRatio
    }

    override fun getDurationOfEvent(m: MidiMessage) = m.deltaTime

    override fun isEventIndexAtEnd(): Boolean = eventIdx == messages.size

    override fun getNextMessage(): MidiMessage = messages[eventIdx]

    override fun updateTempoAndTimeSignatureIfApplicable(m: MidiMessage) {
        if (m.event.statusByte.toUnsigned() == 0xFF) {
            if (m.event.msb.toInt() == MidiMetaType.TEMPO)
                currentTempo = MidiMusic.getSmfTempo(m.event.extraData!!, m.event.extraDataOffset)
            else if (m.event.msb.toInt() == MidiMetaType.TIME_SIGNATURE && m.event.extraDataLength == 4)
                m.event.extraData!!.copyInto(
                    currentTimeSignature,
                    0,
                    m.event.extraDataOffset,
                    m.event.extraDataOffset + m.event.extraDataLength
                )
        }
    }

    private val messageHandlers = mutableListOf<OnMidiMessageListener>()

    fun addOnMessageListener(listener: OnMidiMessageListener) {
        messageHandlers.add(listener)
    }

    fun removeOnMessageListener(listener: OnMidiMessageListener) {
        messageHandlers.remove(listener)
    }

    override fun onEvent(m: MidiMessage) {
        for (er in messageHandlers)
            er.onMessage(m)

    }

    override fun mute() {
        for (i in 0..15)
            onEvent(MidiMessage(0, MidiEvent(i + MidiChannelStatus.CC, MidiCC.ALL_SOUND_OFF, 0, null, 0, 0)))
    }
}

interface OnMidiMessageListener {
    fun onMessage(m: MidiMessage)
}


// Provides asynchronous player control.
class MidiPlayer : MidiPlayerCommon<MidiMessage> {
    companion object {
        suspend fun create(music: MidiMusic, access: MidiAccess, timer: MidiPlayerTimer = SimpleAdjustingMidiPlayerTimer()) =
            MidiPlayer(music, access.openOutputAsync(access.outputs.first().id), timer, true)
    }

    constructor(music: MidiMusic, output: MidiOutput, timer: MidiPlayerTimer = SimpleAdjustingMidiPlayerTimer(), shouldDisposeOutput: Boolean = false)
        : super(output, shouldDisposeOutput, timer) {

        this.music = music
        messages = music.mergeTracks().tracks[0].messages
        looper = Midi1EventLooper(messages, timer, music.deltaTimeSpec)

        looper.starting = Runnable {
            // all control reset on all channels.
            for (i in 0..15) {
                buffer[0] = (i + MidiChannelStatus.CC).toByte()
                buffer[1] = MidiCC.RESET_ALL_CONTROLLERS.toByte()
                buffer[2] = 0
                output.send(buffer, 0, 3, 0)
            }
        }

        val listener = object : OnMidiMessageListener {
            override fun onMessage(m: MidiMessage) {
                val e = m.event
                when (e.eventType.toUnsigned()) {
                    MidiChannelStatus.NOTE_OFF,
                    MidiChannelStatus.NOTE_ON -> {
                        if (mutedChannels.contains(e.channel.toUnsigned()))
                            return // ignore messages for the masked channel.
                    }
                    MidiMusic.SYSEX_EVENT, MidiMusic.SYSEX_END -> {
                        if (buffer.size <= e.extraDataLength)
                            buffer = ByteArray(buffer.size * 2)
                        buffer[0] = e.statusByte
                        e.extraData!!.copyInto(buffer, 1, e.extraDataOffset, e.extraDataLength - 1)
                        output.send(buffer, 0, e.extraDataLength + 1, 0)
                        return
                    }
                    MidiMusic.META_EVENT -> {
                        // do nothing.
                        return
                    }
                }
                val size = MidiEvent.fixedDataSize(e.statusByte)
                buffer[0] = e.statusByte
                buffer[1] = e.msb
                buffer[2] = e.lsb
                output.send(buffer, 0, size + 1, 0)
            }
        }
        addOnMessageListener(listener)
    }

    private val music: MidiMusic
    private var buffer = ByteArray(0x100)

    fun addOnMessageListener(listener: OnMidiMessageListener) {
        (looper as Midi1EventLooper).addOnMessageListener(listener)
    }

    fun removeOnMessageListener(listener: OnMidiMessageListener) {
        (looper as Midi1EventLooper).removeOnMessageListener(listener)
    }

    override val positionInMilliseconds: Long
        get() = music.getTimePositionInMillisecondsForTick(playDeltaTime).toLong()

    override val totalPlayTimeMilliseconds: Int
        get() = MidiMusic.getTotalPlayTimeMilliseconds(messages, music.deltaTimeSpec)

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

internal class SimpleMidi1SeekProcessor(ticks: Int) : SeekProcessor<MidiMessage> {
    private var seek_to: Int = ticks
    private var current: Int = 0

    override fun filterMessage(message: MidiMessage): SeekFilterResult {
        current += message.deltaTime
        if (current >= seek_to)
            return SeekFilterResult.PASS_AND_TERMINATE
        when (message.event.eventType.toUnsigned()) {
            MidiChannelStatus.NOTE_ON, MidiChannelStatus.NOTE_OFF -> return SeekFilterResult.BLOCK
        }
        return SeekFilterResult.PASS
    }
}
