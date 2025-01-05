@file:Suppress("unused")

package dev.atsushieno.ktmidi

@Deprecated("Use Midi1Music which provides more consistent concepts")
class MidiMusic {

    internal class SmfDeltaTimeComputer: DeltaTimeComputer<MidiMessage>() {
        override fun messageToDeltaTime(message: MidiMessage) = message.deltaTime

        override fun isTempoMessage(message: MidiMessage) =
            message.event.eventType.toUnsigned() == META_EVENT && message.event.msb.toInt() == MidiMetaType.TEMPO

        override fun getTempoValue(message: MidiMessage) = getSmfTempo(message.event.extraData!!, message.event.extraDataOffset)
    }

    companion object {
        const val DEFAULT_TEMPO = 500000

        const val SYSEX_EVENT = 0xF0
        const val SYSEX_END = 0xF7
        const val META_EVENT = 0xFF

        /**
         * Calculates ticks per seconds for SMPTE for a SMF delta time division specification raw value.
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

        fun filterEvents(messages: Iterable<MidiMessage>, filter: (MidiMessage) -> Boolean) =
            calc.filterEvents(messages, filter).map { p -> MidiMessage(p.duration.value, p.value.event) }

        fun getTotalPlayTimeMilliseconds(messages: Iterable<MidiMessage>, deltaTimeSpec: Int) = calc.getTotalPlayTimeMilliseconds(messages, deltaTimeSpec)

        fun getPlayTimeMillisecondsAtTick(messages: Iterable<MidiMessage>, ticks: Int, deltaTimeSpec: Int) = calc.getPlayTimeMillisecondsAtTick(messages, ticks, deltaTimeSpec)
    }

    val tracks: MutableList<MidiTrack> = mutableListOf()

    var deltaTimeSpec: Int = 0

    var format: Byte = 0

    fun addTrack(track: MidiTrack) {
        this.tracks.add(track)
    }

    fun filterEvents(filter: (MidiMessage) -> Boolean): Iterable<MidiMessage> {
        if (format != 0.toByte())
            return mergeTracks().filterEvents(filter)
        return filterEvents(tracks[0].messages, filter).asIterable()
    }

    fun getTotalTicks(): Int {
        if (format != 0.toByte())
            return mergeTracks().getTotalTicks()
        return tracks[0].messages.sumOf { m: MidiMessage -> m.deltaTime }
    }

    fun getTotalPlayTimeMilliseconds(): Int {
        if (format != 0.toByte())
            return mergeTracks().getTotalPlayTimeMilliseconds()
        return getTotalPlayTimeMilliseconds(tracks[0].messages, deltaTimeSpec)
    }

    fun getTimePositionInMillisecondsForTick(ticks: Int): Int {
        if (format != 0.toByte())
            return mergeTracks().getTimePositionInMillisecondsForTick(ticks)
        return getPlayTimeMillisecondsAtTick(tracks[0].messages, ticks, deltaTimeSpec)
    }

    init {
        this.format = 1
    }
}

@Deprecated("Use Midi1Track which provides more consistent concepts")
class MidiTrack(val messages: MutableList<MidiMessage> = mutableListOf())

@Deprecated("Use Midi1Event (note that there is also Midi1Message which is NOT the type to update from this one)")
class MidiMessage(val deltaTime: Int, evt: MidiEvent) {
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

    val event: MidiEvent = evt

    override fun toString(): String = "[$deltaTime:$event]"
}

@Deprecated("Use Midi1Message (note that there is also Midi1Event which is NOT the type to update from this one)")
class MidiEvent // MIDI 1.0 only
{
    companion object {

        fun convert(bytes: ByteArray, index: Int, size: Int) = sequence {
            var i = index
            val end = index + size
            while (i < end) {
                if (bytes[i].toUnsigned() == 0xF0) {
                    yield(MidiEvent(0xF0, 0, 0, bytes, i, size))
                    i += size
                } else {
                    if (end <= i + fixedDataSize(bytes[i]))
                        throw Midi1Exception("Received data was incomplete to build MIDI status message for '${bytes[i]}' status.")
                    val z = fixedDataSize(bytes[i])
                    yield(
                        MidiEvent(
                            bytes[i].toUnsigned(),
                            (if (z > 0) bytes[i + 1].toUnsigned() else 0),
                            (if (z > 1) bytes[i + 2].toUnsigned() else 0),
                            null,
                            0,
                            0
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

    constructor (value: Int) {
        this.value = value
        this.extraData = null
        this.extraDataOffset = 0
        this.extraDataLength = 0
    }

    constructor (
        type: Int,
        arg1: Int,
        arg2: Int,
        extraData: ByteArray? = null,
        extraOffset: Int = 0,
        extraLength: Int = extraData?.size ?: 0
    ) {
        this.value = (type.toUInt() + (arg1.toUInt() shl 8) + (arg2.toUInt() shl 16)).toInt()
        this.extraData = extraData
        this.extraDataOffset = extraOffset
        this.extraDataLength = extraLength
    }

    var value: Int = 0

    // This expects EndSysEx byte _inclusive_ for F0 message.
    val extraData: ByteArray?
    val extraDataOffset: Int
    val extraDataLength: Int

    val statusByte: Byte
        get() = (value and 0xFF).toByte()

    val eventType: Byte
        get() =
            when (statusByte.toUnsigned()) {
                MidiMusic.META_EVENT,
                MidiMusic.SYSEX_EVENT,
                MidiMusic.SYSEX_END -> this.statusByte
                else -> (value and 0xF0).toByte()
            }

    val msb: Byte
        get() = ((value and 0xFF00) shr 8).toByte()


    val lsb: Byte
        get() = ((value and 0xFF0000) shr 16).toByte()

    val metaType: Byte
        get() = msb

    val channel: Byte
        get() = (value and 0x0F).toByte()

    override fun toString(): String {
        return value.toString(16)
    }
}

@Deprecated("Use Midi1Music.mergeTracks()")
fun MidiMusic.mergeTracks() : MidiMusic =
    MidiTrackMerger(this).getMergedMessages()


@Deprecated("Use Midi1TrackMerger")
internal class MidiTrackMerger(private var source: MidiMusic) {
    internal fun getMergedMessages(): MidiMusic {
        var l = mutableListOf<MidiMessage>()

        for (track in source.tracks) {
            var delta = 0
            for (mev in track.messages) {
                delta += mev.deltaTime
                l.add(MidiMessage(delta, mev.event))
            }
        }

        if (l.size == 0) {
            val ret = MidiMusic()
            ret.deltaTimeSpec = source.deltaTimeSpec // empty (why did you need to sort your song file?)
            return ret
        }

        // Simple sorter does not work as expected.
        // For example, it does not always preserve event orders on the same channels when the delta time
        // of event B after event A is 0. It could be sorted either as A->B or B->A, which is no-go for
        // MIDI messages. For example, "ProgramChange at Xmsec. -> NoteOn at Xmsec." must not be sorted as
        // "NoteOn at Xmsec. -> ProgramChange at Xmsec.".
        //
        // To resolve this ieeue, we have to sort "chunk"  of events, not all single events themselves, so
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
        val l2 = mutableListOf<MidiMessage>()
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
            if (l[i].event.value != 0) { // if non-dummy
                val tmp = l[i + 1].deltaTime - l[i].deltaTime
                l[i] = MidiMessage(waitToNext, l[i].event)
                waitToNext = tmp
            }
            i++
        }
        l[l.size - 1] = MidiMessage(waitToNext, l[l.size - 1].event)

        val m = MidiMusic()
        m.deltaTimeSpec = source.deltaTimeSpec
        m.format = 0
        m.tracks.add(MidiTrack(l))
        return m
    }
}

fun MidiTrack.splitTracksByChannel(deltaTimeSpec: Byte) : MidiMusic =
    MidiTrackSplitter(messages, deltaTimeSpec).split()

@Deprecated("Use Midi1TrackSplitter")
open class MidiTrackSplitter(private val source: MutableList<MidiMessage>, private val deltaTimeSpec: Byte) {
    companion object {
        fun split(source: MutableList<MidiMessage>, deltaTimeSpec: Byte): MidiMusic {
            return MidiTrackSplitter(source, deltaTimeSpec).split()
        }
    }

    private val tracks = HashMap<Int, SplitTrack>()

    internal class SplitTrack(val trackID: Int) {

        private var totalDeltaTime: Int
        val track: MidiTrack = MidiTrack()

        fun addMessage(deltaInsertAt: Int, e: MidiMessage) {
            val e2 = MidiMessage(deltaInsertAt - totalDeltaTime, e.event)
            track.messages.add(e2)
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
    open fun getTrackId(e: MidiMessage): Int {
        return when (e.event.eventType.toUnsigned()) {
            MidiMusic.META_EVENT, MidiMusic.SYSEX_EVENT, MidiMusic.SYSEX_END -> -1
            else -> e.event.channel.toUnsigned()
        }
    }

    fun split(): MidiMusic {
        var totalDeltaTime = 0
        for (e in source) {
            totalDeltaTime += e.deltaTime
            val id: Int = getTrackId(e)
            getTrack(id).addMessage(totalDeltaTime, e)
        }

        val m = MidiMusic()
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
