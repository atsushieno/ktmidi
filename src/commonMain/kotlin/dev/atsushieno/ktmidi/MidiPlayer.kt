package dev.atsushieno.ktmidi

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlin.jvm.Volatile

enum class PlayerState {
    STOPPED,
    PLAYING,
    PAUSED,
}

internal class MidiEventLooper(var messages: List<MidiMessage>, private val timer: MidiPlayerTimer,
                               private val deltaTimeSpec: Int
) {
    var starting: Runnable? = null
    var finished: Runnable? = null
    var playbackCompletedToEnd: Runnable? = null

    private var doPause: Boolean = false
    private var doStop: Boolean = false
    var tempoRatio: Double = 1.0

    var state: PlayerState
    private var eventIdx = 0
    var currentTempo = MidiMetaType.DEFAULT_TEMPO
    var currentTimeSignature = ByteArray(4)
    var playDeltaTime: Int = 0
    private val eventReceivedHandlers = arrayListOf<OnMidiEventListener>()

    private var eventLoopSemaphore : Semaphore? = null
    @Volatile var clientNeedsSpinWait = false
    @Volatile var clientNeedsCloseSpinWait = false

    init {
        if (deltaTimeSpec < 0)
            throw UnsupportedOperationException("SMPTe-based delta time is not implemented in this player.")
        state = PlayerState.STOPPED
    }

    fun addOnEventReceivedListener(listener: OnMidiEventListener) {
        eventReceivedHandlers.add(listener)
    }

    fun removeOnEventReceivedListener(listener: OnMidiEventListener) {
        eventReceivedHandlers.remove(listener)
    }

    fun close() {
        if(eventLoopSemaphore?.availablePermits == 0)
            eventLoopSemaphore?.release()
        if (state != PlayerState.STOPPED) {
            clientNeedsCloseSpinWait = true
            stop()
            var dummyCloseCounter = 0
            while (clientNeedsCloseSpinWait) {
                if (dummyCloseCounter++ < 0) //  spinwait, or overflow
                    break
            }
        }
    }

    fun play() {
        state = PlayerState.PLAYING
        eventLoopSemaphore?.release()
    }

    fun mute() {
        for (i in 0..15)
            onEvent(MidiEvent(i + 0xB0, 0x78, 0, null, 0, 0))
    }

    fun pause() {
        doPause = true
        mute()
    }

    suspend fun playBlocking() {
        try {
            starting?.run()

            eventIdx = 0
            playDeltaTime = 0
            eventLoopSemaphore = Semaphore(1, 0)
            var doWait = true
            while (true) {
                if (doWait) {
                    eventLoopSemaphore?.acquire()
                    clientNeedsSpinWait = false
                    doWait = false
                }
                if (doStop)
                    break
                if (doPause) {
                    doWait = true
                    doPause = false
                    state = PlayerState.PAUSED
                    continue
                }
                if (eventIdx == messages.size)
                    break
                processMessage(messages[eventIdx++])
            }
            doStop = false
            state = PlayerState.STOPPED
            mute()
            if (eventIdx == messages.size)
                playbackCompletedToEnd?.run()
            finished?.run()
        } finally {
            clientNeedsCloseSpinWait = false
        }
    }

    private fun getContextDeltaTimeInMilliseconds(deltaTime: Int): Int {
        return (currentTempo.toDouble() / 1000 * deltaTime / deltaTimeSpec / tempoRatio).toInt()
    }

    private suspend fun processMessage(m: MidiMessage) {
        if (seek_processor != null) {
            val result = seek_processor!!.filterMessage(m)
            when (result) {
                SeekFilterResult.PASS_AND_TERMINATE,
                SeekFilterResult.BLOCK_AND_TERMINATE ->
                    seek_processor = null
            }

            when (result) {
                SeekFilterResult.BLOCK,
                SeekFilterResult.BLOCK_AND_TERMINATE ->
                    return // ignore this event
            }
        } else if (m.deltaTime != 0) {
            val ms = getContextDeltaTimeInMilliseconds(m.deltaTime)
            timer.waitBy(ms)
            playDeltaTime += m.deltaTime
        }

        if (m.event.statusByte.toUnsigned() == 0xFF) {
            if (m.event.msb == MidiMetaType.TEMPO)
                currentTempo = MidiMetaType.getTempo(m.event.extraData!!, m.event.extraDataOffset)
            else if (m.event.msb == MidiMetaType.TIME_SIGNATURE && m.event.extraDataLength == 4)
                m.event.extraData!!.copyInto(
                    currentTimeSignature,
                    0,
                    m.event.extraDataOffset,
                    m.event.extraDataOffset + m.event.extraDataLength
                )
        } else
            onEvent(m.event)
    }

    private fun onEvent(m: MidiEvent) {
        for (er in eventReceivedHandlers)
            er.onEvent(m)
    }

    fun stop() {
        if (state != PlayerState.STOPPED)
            doStop = true
        if (eventLoopSemaphore?.availablePermits == 0)
            eventLoopSemaphore?.release()
    }

    private var seek_processor: SeekProcessor? = null

    // not sure about the interface, so make it non-public yet.
    internal fun seek(seekProcessor: SeekProcessor?, ticks: Int) {
        seek_processor = seekProcessor ?: SimpleSeekProcessor(ticks)
        eventIdx = 0
        playDeltaTime = ticks
        mute()
    }
}

// Provides asynchronous player control.
class MidiPlayer {
    constructor(music: MidiMusic, access: MidiAccess, timer: MidiPlayerTimer = SimpleAdjustingMidiPlayerTimer())
            : this(music, access.openOutputAsync(access.outputs.first().id), timer) {
        should_dispose_output = true
    }

    constructor(music: MidiMusic, output: MidiOutput, timer: MidiPlayerTimer = SimpleAdjustingMidiPlayerTimer()) {
        this.music = music
        this.output = output

        messages = SmfTrackMerger.merge(music).tracks[0].messages
        looper = MidiEventLooper(messages, timer, music.deltaTimeSpec)
        looper.starting = Runnable {
            // all control reset on all channels.
            for (i in 0..15) {
                buffer[0] = (i + 0xB0).toByte()
                buffer[1] = 0x79
                buffer[2] = 0
                output.send(buffer, 0, 3, 0)
            }
        }

        val listener = object : OnMidiEventListener {
            override fun onEvent(e: MidiEvent) {
                when (e.eventType) {
                    MidiEventType.NOTE_OFF,
                    MidiEventType.NOTE_ON -> {
                        if (mutedChannels.contains(e.channel.toUnsigned()))
                            return // ignore messages for the masked channel.
                    }
                    MidiEventType.SYSEX,
                    MidiEventType.SYSEX_END -> {
                        if (buffer.size <= e.extraDataLength)
                            buffer = ByteArray(buffer.size * 2)
                        buffer[0] = e.statusByte
                        e.extraData!!.copyInto(buffer, 1, e.extraDataOffset, e.extraDataLength - 1)
                        output.send(buffer, 0, e.extraDataLength + 1, 0)
                        return
                    }
                    MidiEventType.META -> {
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
        addOnEventReceivedListener(listener)
    }

    fun addOnEventReceivedListener(listener: OnMidiEventListener) {
        looper.addOnEventReceivedListener(listener)
    }

    fun removeOnEventReceivedListener(listener: OnMidiEventListener) {
        looper.removeOnEventReceivedListener(listener)
    }

    private val looper: MidiEventLooper

    // FIXME: it is still awkward to have it here. Move it into MidiEventLooper.
    private var sync_player_task: Job? = null
    private val output: MidiOutput
    private val messages: MutableList<MidiMessage>
    private val music: MidiMusic

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
        get() = MidiMusic.getTotalPlayTimeMilliseconds(messages, music.deltaTimeSpec)

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
        looper.seek(null, ticks)
    }

    fun setMutedChannels(mutedChannels: Iterable<Int>) {
        this.mutedChannels = mutedChannels.toList()
        // additionally send all sound off for the muted channels.
        for (ch in 0..15)
            if (!mutedChannels.contains(ch))
                output.send(arrayOf((0xB0 + ch).toByte(), 120, 0).toByteArray(), 0, 3, 0)
    }
}

interface SeekProcessor {
    fun filterMessage(message: MidiMessage): SeekFilterResult
}

enum class SeekFilterResult {
    PASS,
    BLOCK,
    PASS_AND_TERMINATE,
    BLOCK_AND_TERMINATE,
}

internal class SimpleSeekProcessor(ticks: Int) : SeekProcessor {
    private var seek_to: Int = ticks
    private var current: Int = 0

    override fun filterMessage(message: MidiMessage): SeekFilterResult {
        current += message.deltaTime
        if (current >= seek_to)
            return SeekFilterResult.PASS_AND_TERMINATE
        when (message.event.eventType) {
            MidiEventType.NOTE_ON, MidiEventType.NOTE_OFF -> return SeekFilterResult.BLOCK
        }
        return SeekFilterResult.PASS
    }
}
