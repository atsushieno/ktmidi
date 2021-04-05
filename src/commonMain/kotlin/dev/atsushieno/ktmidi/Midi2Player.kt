package dev.atsushieno.ktmidi

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch

internal class Midi2EventLooper(var messages: List<Ump>, private val timer: MidiPlayerTimer) : MidiEventLooper<Ump>(timer) {
    override fun isEventIndexAtEnd() = eventIdx == messages.size

    override fun getNextMessage() = messages[eventIdx]

    override fun getContextDeltaTimeInMilliseconds(m: Ump): Int {
        // FIXME: it is a fake implementation, not the right calculation
        return if(m.isJRTimestamp) (currentTempo.toDouble() / 1000 * m.getJRTimestampValue() / tempoRatio).toInt() else 0
    }

    override fun getDurationOfEvent(m: Ump) = if (m.isJRTimestamp) m.getJRTimestampValue() else 0

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
class Midi2Player {
    constructor(music: Midi2Music, access: MidiAccess, timer: MidiPlayerTimer = SimpleAdjustingMidiPlayerTimer())
            : this(music, access.openOutputAsync(access.outputs.first().id), timer) {
        should_dispose_output = true
    }

    constructor(music: Midi2Music, output: MidiOutput, timer: MidiPlayerTimer = SimpleAdjustingMidiPlayerTimer()) {
        this.music = music
        this.output = output

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

    fun addOnMessageListener(listener: OnMidi2EventListener) {
        looper.addOnMessageListener(listener)
    }

    fun removeOnMessageListener(listener: OnMidi2EventListener) {
        looper.removeOnMessageListener(listener)
    }

    private val looper: Midi2EventLooper

    // FIXME: it is still awkward to have it here. Move it into MidiEventLooper.
    private var sync_player_task: Job? = null
    private val output: MidiOutput
    private val messages: List<Ump>
    private val music: Midi2Music

    private var should_dispose_output: Boolean = false
    private var buffer = ByteArray(0x100)
    private var mutedChannels = listOf<Int>()

    var finished: Runnable?
        get() = looper.finished
        set(v) {
            looper.finished = v
        }

    var playbackCompletedToEnd: Runnable?
        get() = looper.playbackCompletedToEnd
        set(v) {
            looper.playbackCompletedToEnd = v
        }

    val state: PlayerState
        get() = looper.state

    var tempoChangeRatio: Double
        get() = looper.tempoRatio
        set(v) {
            looper.tempoRatio = v
        }

    var tempo: Int
        get() = looper.currentTempo
        set(v) {
            looper.currentTempo = v
        }

    val bpm: Int
        get() = (60.0 / tempo * 1000000.0).toInt()

    // You can break the data at your own risk but I take performance precedence.
    val timeSignature
        get() = looper.currentTimeSignature

    val playDeltaTime
        get() = looper.playDeltaTime

    val positionInMilliseconds: Long
        get() = music.getTimePositionInMillisecondsForTick(playDeltaTime).toLong()

    val totalPlayTimeMilliseconds: Int
        get() = Midi2Music.getTotalPlayTimeMilliseconds(messages)

    fun close() {
        looper.stop()
        looper.close()
        if (should_dispose_output)
            output.close()
    }

    private fun startLoop() {
        looper.clientNeedsSpinWait = true
        sync_player_task = GlobalScope.launch {
            looper.playBlocking()
            sync_player_task = null
        }
        var counter = 0
        while (looper.clientNeedsSpinWait) {
            counter++ //  spinwait, or overflow
        }
    }

    fun play() {
        when (state) {
            PlayerState.PLAYING -> return // do nothing
            PlayerState.PAUSED -> {
                looper.play(); return; }
            PlayerState.STOPPED -> {
                if (sync_player_task == null)
                    startLoop()
                looper.play()
            }
        }
    }

    fun pause() {
        when (state) {
            PlayerState.PLAYING -> {
                looper.pause(); return;
            }
            else -> return
        }
    }

    fun stop() {
        when (state) {
            PlayerState.PAUSED,
            PlayerState.PLAYING -> looper.stop()
        }
    }

    fun seek(ticks: Int) {
        looper.seek(Midi2SimpleSeekProcessor(ticks), ticks)
    }

    fun setMutedChannels(mutedChannels: Iterable<Int>) {
        this.mutedChannels = mutedChannels.toList()
        // additionally send all sound off for the muted channels.
        for (ch in 0..15)
            if (!mutedChannels.contains(ch))
                output.send(arrayOf((0xB0 + ch).toByte(), 120, 0).toByteArray(), 0, 3, 0)
    }
}

internal class Midi2SimpleSeekProcessor(ticks: Int) : SeekProcessor<Ump> {
    private var seekTo: Int = ticks
    private var current: Int = 0

    override fun filterMessage(message: Ump): SeekFilterResult {
        current += if(message.isJRTimestamp) message.getJRTimestampValue() else 0
        if (current >= seekTo)
            return SeekFilterResult.PASS_AND_TERMINATE
        when (message.eventType.toByte()) {
            MidiEventType.NOTE_ON, MidiEventType.NOTE_OFF -> return SeekFilterResult.BLOCK
        }
        return SeekFilterResult.PASS
    }
}
