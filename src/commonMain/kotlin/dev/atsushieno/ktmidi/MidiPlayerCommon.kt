package dev.atsushieno.ktmidi

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch


abstract class MidiPlayerCommon<TMessage>(internal val output: MidiOutput, private var shouldDisposeOutput: Boolean, timer: MidiPlayerTimer) {

    internal lateinit var messages: MutableList<TMessage>
    internal lateinit var looper: MidiEventLooper<TMessage>

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

    // You can break the data at your own risk but I take performance precedence.
    val timeSignature
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
        when (state) {
            PlayerState.PAUSED,
            PlayerState.PLAYING -> looper.stop()
        }
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
