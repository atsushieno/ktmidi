package dev.atsushieno.ktmidi

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlin.jvm.Volatile

internal abstract class MidiEventLooperBase {
    var starting: Runnable? = null
    var finished: Runnable? = null
    var playbackCompletedToEnd: Runnable? = null

    private var doPause: Boolean = false
    private var doStop: Boolean = false
    var tempoRatio: Double = 1.0

    var state: PlayerState
    internal var eventIdx = 0
    var currentTempo = MidiMusic.DEFAULT_TEMPO
    var currentTimeSignature = MutableList<Byte>(4) { 0 }
    var playDeltaTime: Int = 0

    private var eventLoopSemaphore : Semaphore? = null
    @Volatile
    var clientNeedsSpinWait = false
    @Volatile
    var clientNeedsCloseSpinWait = false

    init {
        state = PlayerState.STOPPED
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

    abstract fun mute()

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
                if (isEventIndexAtEnd())
                    break
                processNextMessage()
                eventIdx++
            }
            doStop = false
            state = PlayerState.STOPPED
            mute()
            if (isEventIndexAtEnd())
                playbackCompletedToEnd?.run()
            finished?.run()
        } finally {
            clientNeedsCloseSpinWait = false
        }
    }

    abstract fun isEventIndexAtEnd() : Boolean

    fun stop() {
        if (state != PlayerState.STOPPED)
            doStop = true
        if (eventLoopSemaphore?.availablePermits == 0)
            eventLoopSemaphore?.release()
    }

    protected abstract suspend fun processNextMessage()
}

internal abstract class MidiEventLooper<TMessage>(private val timer: MidiPlayerTimer) : MidiEventLooperBase() {
    abstract fun getNextMessage() : TMessage
    abstract fun getContextDeltaTimeInSeconds(m: TMessage): Double
    abstract fun getDurationOfEvent(m: TMessage) : Int
    abstract fun updateTempoAndTimeSignatureIfApplicable(m: TMessage)
    abstract fun onEvent(m: TMessage)

    override suspend fun processNextMessage() =
        processMessage(getNextMessage())

    private suspend fun processMessage(m: TMessage) {
        if (seek_processor != null) {
            val result = seek_processor!!.filterMessage(m)
            if (result == SeekFilterResult.PASS_AND_TERMINATE || result == SeekFilterResult.BLOCK_AND_TERMINATE)
                seek_processor = null

            if (result == SeekFilterResult.BLOCK || result == SeekFilterResult.BLOCK_AND_TERMINATE)
                return // ignore this event
        } else {
            val sec = getContextDeltaTimeInSeconds(m)
            if (sec > 0) {
                timer.waitBySeconds(sec)
                playDeltaTime += getDurationOfEvent(m)
            }
        }

        updateTempoAndTimeSignatureIfApplicable(m)

        onEvent(m)
    }

    private var seek_processor: SeekProcessor<TMessage>? = null

    // not sure about the interface, so make it non-public yet.
    internal fun seek(seekProcessor: SeekProcessor<TMessage>, ticks: Int) {
        seek_processor = seekProcessor
        eventIdx = 0
        playDeltaTime = ticks
        mute()
    }
}

abstract class MidiPlayer internal constructor(internal val output: MidiOutput, private var shouldDisposeOutput: Boolean) {

    internal abstract val looper: MidiEventLooperBase

    // FIXME: it is still awkward to have it here. Move it into MidiEventLooper.
    private var syncPlayerTask: Job? = null

    internal var mutedChannels = listOf<Int>()

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

    val timeSignature: List<Byte>
        get() = looper.currentTimeSignature

    val playDeltaTime
        get() = looper.playDeltaTime

    abstract val positionInMilliseconds: Long

    abstract val totalPlayTimeMilliseconds: Int

    fun close() {
        looper.stop()
        looper.close()
        if (shouldDisposeOutput)
            output.close()
    }

    private fun startLoop() {
        looper.clientNeedsSpinWait = true
        syncPlayerTask = GlobalScope.launch {
            looper.playBlocking()
            syncPlayerTask = null
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
                if (syncPlayerTask == null)
                    startLoop()
                looper.play()
            }
        }
    }

    fun pause() {
        if (state == PlayerState.PLAYING)
            looper.pause()
    }

    fun stop() {
        if (state == PlayerState.PAUSED || state == PlayerState.PLAYING)
            looper.stop()
    }

    abstract fun seek(ticks: Int)

    abstract fun setMutedChannels(mutedChannels: Iterable<Int>)
}

enum class PlayerState {
    STOPPED,
    PLAYING,
    PAUSED,
}

interface SeekProcessor<TMessage> {
    fun filterMessage(message: TMessage): SeekFilterResult
}

enum class SeekFilterResult {
    PASS,
    BLOCK,
    PASS_AND_TERMINATE,
    BLOCK_AND_TERMINATE,
}
