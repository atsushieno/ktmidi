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

    override fun getContextDeltaTimeInMilliseconds(m: MidiMessage): Int {
        return (currentTempo.toDouble() / 1000 * m.deltaTime / deltaTimeSpec / tempoRatio).toInt()
    }

    override fun getDurationOfEvent(m: MidiMessage) = m.deltaTime

    override fun isEventIndexAtEnd(): Boolean = eventIdx == messages.size

    override fun getNextMessage(): MidiMessage = messages[eventIdx]

    override fun updateTempoAndTimeSignatureIfApplicable(m: MidiMessage) {
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
            onEvent(MidiMessage(0, MidiEvent(i + 0xB0, 0x78, 0, null, 0, 0)))
    }
}

interface OnMidiMessageListener {
    fun onMessage(m: MidiMessage)
}

internal abstract class MidiEventLooper<TMessage>(private val timer: MidiPlayerTimer) {
    var starting: Runnable? = null
    var finished: Runnable? = null
    var playbackCompletedToEnd: Runnable? = null

    private var doPause: Boolean = false
    private var doStop: Boolean = false
    var tempoRatio: Double = 1.0

    var state: PlayerState
    internal var eventIdx = 0
    var currentTempo = MidiMetaType.DEFAULT_TEMPO
    var currentTimeSignature = ByteArray(4)
    var playDeltaTime: Int = 0

    private var eventLoopSemaphore : Semaphore? = null
    @Volatile var clientNeedsSpinWait = false
    @Volatile var clientNeedsCloseSpinWait = false

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
                processMessage(getNextMessage())
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
    abstract fun getNextMessage() : TMessage
    abstract fun getContextDeltaTimeInMilliseconds(m: TMessage): Int
    abstract fun getDurationOfEvent(m: TMessage) : Int
    abstract fun updateTempoAndTimeSignatureIfApplicable(m: TMessage)
    abstract fun onEvent(m: TMessage)

    private suspend fun processMessage(m: TMessage) {
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
        } else {
            val ms = getContextDeltaTimeInMilliseconds(m)
            if (ms > 0) {
                timer.waitBy(ms)
                playDeltaTime += getDurationOfEvent(m)
            }
        }

        updateTempoAndTimeSignatureIfApplicable(m)

        onEvent(m)
    }

    fun stop() {
        if (state != PlayerState.STOPPED)
            doStop = true
        if (eventLoopSemaphore?.availablePermits == 0)
            eventLoopSemaphore?.release()
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

// Provides asynchronous player control.
class MidiPlayer : MidiPlayerCommon<MidiMessage> {
    constructor(music: MidiMusic, access: MidiAccess, timer: MidiPlayerTimer = SimpleAdjustingMidiPlayerTimer())
            : this(music, access.openOutputAsync(access.outputs.first().id), timer, true)

    constructor(music: MidiMusic, output: MidiOutput, timer: MidiPlayerTimer = SimpleAdjustingMidiPlayerTimer(), shouldDisposeOutput: Boolean = false)
        : super(output, shouldDisposeOutput, timer) {

        this.music = music
        messages = SmfTrackMerger.merge(music).tracks[0].messages
        looper = Midi1EventLooper(messages, timer, music.deltaTimeSpec)

        looper.starting = Runnable {
            // all control reset on all channels.
            for (i in 0..15) {
                buffer[0] = (i + MidiEventType.CC).toByte()
                buffer[1] = MidiCC.RESET_ALL_CONTROLLERS
                buffer[2] = 0
                output.send(buffer, 0, 3, 0)
            }
        }

        val listener = object : OnMidiMessageListener {
            override fun onMessage(m: MidiMessage) {
                val e = m.event
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
        when (message.event.eventType) {
            MidiEventType.NOTE_ON, MidiEventType.NOTE_OFF -> return SeekFilterResult.BLOCK
        }
        return SeekFilterResult.PASS
    }
}
