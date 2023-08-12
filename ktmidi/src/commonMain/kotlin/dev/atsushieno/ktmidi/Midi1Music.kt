@file:Suppress("unused")

package dev.atsushieno.ktmidi

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class Midi1Music {

    @JsExport.Ignore
    internal class SmfDeltaTimeComputer: DeltaTimeComputer<Midi1Event>() {
        override fun messageToDeltaTime(message: Midi1Event) = message.deltaTime

        override fun isTempoMessage(message: Midi1Event) =
            message.message.statusCode.toUnsigned() == Midi1Status.META && message.message.msb.toInt() == MidiMetaType.TEMPO

        override fun getTempoValue(message: Midi1Event): Int {
            val e = message.message as Midi1CompoundMessage
            return getSmfTempo(e.extraData!!, e.extraDataOffset)
        }
    }

    companion object {
        const val DEFAULT_TEMPO = 500000

        /**
         * Calculates ticks per seconds for SMPTE for an SMF delta time division specification raw value.
         * `frameRate` should be one of 24,25,29, and 30. We dare to use UByte as the argument type to indicate that the argument is NOT the raw negative number in deltaTimeSpec.
         */
        fun getSmpteTicksPerSeconds(smfDeltaTimeSpec: Int) =
            getSmpteTicksPerSeconds((-smfDeltaTimeSpec shr 8).toUByte(), smfDeltaTimeSpec and 0xFF)

        private fun getSmpteTicksPerSeconds(nominalFrameRate: UByte, ticksPerFrame: Int) = getActualSmpteFrameRate(nominalFrameRate).toInt() * ticksPerFrame

        // The valid values for SMPTE frameRate are 24, 25, 29, and 30, but 29 means 30 frames per second.
        private fun getActualSmpteFrameRate(nominalFrameRate: UByte) =
            if (nominalFrameRate == 29.toUByte()) 30u else nominalFrameRate

        // Note that the default tempo expects that a quarter note in 0.5 sec. (in 120bpm)
        fun getSmpteDurationInSeconds(smfDeltaTimeSpec: Int, ticks: Int, tempo: Int = DEFAULT_TEMPO, tempoRatio: Double = 1.0): Double =
            tempo.toDouble() / 250_000 * ticks / getSmpteTicksPerSeconds(smfDeltaTimeSpec) / tempoRatio

        // Note that the default tempo expects that a quarter note in 0.5 sec. (in 120bpm)
        fun getSmpteTicksForSeconds(smfDeltaTimeSpec: Int, duration: Double, tempo: Int = DEFAULT_TEMPO, tempoRatio: Double = 1.0): Int =
            (duration * tempoRatio / tempo * 250_000 * getSmpteTicksPerSeconds(smfDeltaTimeSpec)).toInt()

        fun getSmfTempo(data: ByteArray, offset: Int): Int {
            if (data.size < offset + 2)
                throw IndexOutOfBoundsException("data array must be longer than argument offset $offset + 2")
            return (data[offset].toUnsigned() shl 16) + (data[offset + 1].toUnsigned() shl 8) + data[offset + 2]
        }

        fun getSmfBpm(data: ByteArray, offset: Int): Double {
            return 60000000.0 / getSmfTempo(data, offset)
        }

        private val calc = SmfDeltaTimeComputer()

        fun filterEvents(messages: Iterable<Midi1Event>, filter: (Midi1Event) -> Boolean) =
            calc.filterEvents(messages, filter).map { p -> Midi1Event(p.duration.value, p.value.message) }

        fun getTotalPlayTimeMilliseconds(messages: Iterable<Midi1Event>, deltaTimeSpec: Int) = calc.getTotalPlayTimeMilliseconds(messages, deltaTimeSpec)

        fun getPlayTimeMillisecondsAtTick(messages: Iterable<Midi1Event>, ticks: Int, deltaTimeSpec: Int) = calc.getPlayTimeMillisecondsAtTick(messages, ticks, deltaTimeSpec)
    }

    val tracks: MutableList<Midi1Track> = mutableListOf()

    var deltaTimeSpec: Int = 0

    var format: Byte = 0

    fun addTrack(track: Midi1Track) {
        this.tracks.add(track)
    }

    fun filterEvents(filter: (Midi1Event) -> Boolean): Iterable<Midi1Event> {
        if (format != 0.toByte())
            return mergeTracks().filterEvents(filter)
        return filterEvents(tracks[0].events, filter).asIterable()
    }

    fun getTotalTicks(): Int {
        if (format != 0.toByte())
            return mergeTracks().getTotalTicks()
        return tracks[0].events.sumOf { m: Midi1Event -> m.deltaTime }
    }

    fun getTotalPlayTimeMilliseconds(): Int {
        if (format != 0.toByte())
            return mergeTracks().getTotalPlayTimeMilliseconds()
        return getTotalPlayTimeMilliseconds(tracks[0].events, deltaTimeSpec)
    }

    fun getTimePositionInMillisecondsForTick(ticks: Int): Int {
        if (format != 0.toByte())
            return mergeTracks().getTimePositionInMillisecondsForTick(ticks)
        return getPlayTimeMillisecondsAtTick(tracks[0].events, ticks, deltaTimeSpec)
    }

    init {
        this.format = 1
    }
}

class Midi1Track(val events: MutableList<Midi1Event> = mutableListOf()) {
    @Deprecated("Use events property instead", ReplaceWith("events"))
    val messages: MutableList<Midi1Event>
        get() = events
}

class Midi1Event(val deltaTime: Int, val message: Midi1Message) {
    companion object {
        fun encode7BitLength(length: Int): Sequence<Byte> =
            sequence {
                var v = length
                while (v >= 0x80) {
                    yield((v % 0x80 + 0x80).toByte())
                    v /= 0x80
                }
                yield (v.toByte())
            }
    }

    @Deprecated("Use message property instead (you might need casting to Midi1CompoundMessage)")
    val event: Midi1Message by lazy { message }

    override fun toString(): String = "[$deltaTime:$message]"
}

interface Midi1Message {
    companion object {
        fun convert(bytes: ByteArray, index: Int, size: Int) = sequence<Midi1Message> {
            var i = index
            val end = index + size
            while (i < end) {
                if (bytes[i].toUnsigned() == 0xF0) {
                    yield(Midi1CompoundMessage(0xF0, 0, 0, bytes, i, size))
                    i += size
                } else {
                    if (end < i + fixedDataSize(bytes[i]))
                        throw Exception("Received data was incomplete to build MIDI status message for '${bytes[i]}' status.")
                    val z = fixedDataSize(bytes[i])
                    yield(
                        Midi1SimpleMessage(
                            bytes[i].toUnsigned(),
                            (if (z > 0) bytes[i + 1].toUnsigned() else 0),
                            (if (z > 1) bytes[i + 2].toUnsigned() else 0),
                        )
                    )
                    i += z + 1
                }
            }
        }

        fun fixedDataSize(statusByte: Byte): Byte =
            when ((statusByte.toUnsigned() and 0xF0)) {
                0xF0 -> {
                    when (statusByte.toUnsigned()) {
                        0xF1, 0xF3 -> 1
                        0xF2 -> 2
                        else -> 0
                    }
                } // including 0xF7, 0xFF
                0xC0, 0xD0 -> 1
                else -> 2
            }
    }

    val value: Int

    /// Contains status code, and channel for Midi1SimpleMessage.
    val statusByte: Byte
        get() = (value and 0xFF).toByte()

    /// Contains channel status (80-E0), Fn for System messages, or meta event in SMF.
    val statusCode: Byte
        get() =
            when (statusByte.toUnsigned()) {
                Midi1Status.META,
                Midi1Status.SYSEX,
                Midi1Status.SYSEX_END -> this.statusByte
                else -> (value and 0xF0).toByte()
            }
    @Deprecated("Use statusCode property instead", ReplaceWith("statusCode"))
    val eventType: Byte
        get() = statusCode

    val msb: Byte
        get() = ((value and 0xFF00) shr 8).toByte()

    val lsb: Byte
        get() = ((value and 0xFF0000) shr 16).toByte()

    val metaType: Byte
        get() = msb

    val channel: Byte
        get() = (value and 0x0F).toByte()
}

data class Midi1SimpleMessage(override val value: Int) : Midi1Message {
    constructor(type: Int, arg1: Int, arg2: Int)
            : this((type.toUInt() + (arg1.toUInt() shl 8) + (arg2.toUInt() shl 16)).toInt())
}

class Midi1CompoundMessage : Midi1Message {
    constructor (
        type: Int,
        arg1: Int,
        arg2: Int,
        extraData: ByteArray? = null,
        extraOffset: Int = 0,
        extraLength: Int = extraData?.size ?: 0
    ){
        this.value = (type.toUInt() + (arg1.toUInt() shl 8) + (arg2.toUInt() shl 16)).toInt()
        this.extraData = extraData
        this.extraDataOffset = extraOffset
        this.extraDataLength = extraLength
    }
    // A simple (non-F0h, non-FFh) message could be represented in a 32-bit int
    final override val value: Int

    // They are used by SysEx and meta events (if used for SMF)
    // `extraData` *might* contain EndSysEx (F7) byte.
    val extraData: ByteArray?
    val extraDataOffset: Int
    val extraDataLength: Int

    override fun toString(): String {
        return value.toString(16)
    }
}

fun Midi1Music.mergeTracks() : Midi1Music =
    Midi1TrackMerger(this).getMergedMessages()


internal class Midi1TrackMerger(private var source: Midi1Music) {
    internal fun getMergedMessages(): Midi1Music {
        var l = mutableListOf<Midi1Event>()

        for (track in source.tracks) {
            var delta = 0
            for (mev in track.events) {
                delta += mev.deltaTime
                l.add(Midi1Event(delta, mev.message))
            }
        }

        if (l.size == 0) {
            val ret = Midi1Music()
            ret.deltaTimeSpec = source.deltaTimeSpec // empty (why did you need to sort your song file?)
            return ret
        }

        // Simple sorter does not work as expected.
        // For example, it does not always preserve event orders on the same channels when the delta time
        // of event B after event A is 0. It could be sorted either as A->B or B->A, which is no-go for
        // MIDI messages. For example, "ProgramChange at Xmsec. -> NoteOn at Xmsec." must not be sorted as
        // "NoteOn at Xmsec. -> ProgramChange at Xmsec.".
        //
        // To resolve this issue, we have to sort "chunk"  of events, not all single events themselves, so
        // that order of events in the same chunk is preserved.
        // i.e. [AB] at 48 and [CDE] at 0 should be sorted as [CDE] [AB].

        val indexList = mutableListOf<Int>()
        var prev = -1
        var i = 0
        while (i < l.size) {
            if (l[i].deltaTime != prev) {
                indexList.add(i)
                prev = l[i].deltaTime
            }
            i++
        }
        val idxOrdered = indexList.sortedBy { n -> l[n].deltaTime }

        // now build a new event list based on the sorted blocks.
        val l2 = mutableListOf<Midi1Event>()
        var idx: Int
        i = 0
        while (i < idxOrdered.size) {
            idx = idxOrdered[i]
            prev = l[idx].deltaTime
            while (idx < l.size && l[idx].deltaTime == prev) {
                l2.add(l[idx])
                idx++
            }
            i++
        }
        l = l2

        // now messages should be sorted correctly.

        var waitToNext = l[0].deltaTime
        i = 0
        while (i < l.size - 1) {
            val m = l[i].message
            if (m is Midi1SimpleMessage && m.value != 0) { // if non-dummy
                val tmp = l[i + 1].deltaTime - l[i].deltaTime
                l[i] = Midi1Event(waitToNext, l[i].message)
                waitToNext = tmp
            }
            i++
        }
        l[l.size - 1] = Midi1Event(waitToNext, l[l.size - 1].message)

        val music = Midi1Music()
        music.deltaTimeSpec = source.deltaTimeSpec
        music.format = 0
        music.tracks.add(Midi1Track(l))
        return music
    }
}

fun Midi1Track.splitTracksByChannel(deltaTimeSpec: Byte) : Midi1Music =
    Midi1TrackSplitter(events, deltaTimeSpec).split()

open class Midi1TrackSplitter(private val source: MutableList<Midi1Event>, private val deltaTimeSpec: Byte) {
    companion object {
        fun split(source: MutableList<Midi1Event>, deltaTimeSpec: Byte): Midi1Music {
            return Midi1TrackSplitter(source, deltaTimeSpec).split()
        }
    }

    private val tracks = HashMap<Int, SplitTrack>()

    internal class SplitTrack(val trackID: Int) {

        private var totalDeltaTime: Int
        val track: Midi1Track = Midi1Track()

        fun addMessage(deltaInsertAt: Int, e: Midi1Event) {
            val e2 = Midi1Event(deltaInsertAt - totalDeltaTime, e.message)
            track.events.add(e2)
            totalDeltaTime = deltaInsertAt
        }

        init {
            totalDeltaTime = 0
        }
    }

    private fun getTrack(track: Int): SplitTrack {
        var t = tracks[track]
        if (t == null) {
            t = SplitTrack(track)
            tracks[track] = t
        }
        return t
    }

    // Override it to customize track dispatcher. It would be
    // useful to split note messages out from non-note ones,
    // to ease data reading.
    open fun getTrackId(e: Midi1Event): Int {
        return when (e.message.statusCode.toUnsigned()) {
            Midi1Status.META, Midi1Status.SYSEX, Midi1Status.SYSEX_END -> -1
            else -> e.message.channel.toUnsigned()
        }
    }

    fun split(): Midi1Music {
        var totalDeltaTime = 0
        for (e in source) {
            totalDeltaTime += e.deltaTime
            val id: Int = getTrackId(e)
            getTrack(id).addMessage(totalDeltaTime, e)
        }

        val m = Midi1Music()
        m.deltaTimeSpec = deltaTimeSpec.toInt()
        for (t in tracks.values)
            m.tracks.add(t.track)
        return m
    }

    init {
        val mtr = SplitTrack(-1)
        tracks[-1] = mtr
    }
}
