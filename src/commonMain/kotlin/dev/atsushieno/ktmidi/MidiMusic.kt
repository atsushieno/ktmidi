@file:Suppress("unused")

package dev.atsushieno.ktmidi

class MidiMusic {
    class SmfDeltaTimeComputer: DeltaTimeComputer<MidiMessage>() {
        override fun messageToDeltaTime(message: MidiMessage) = message.deltaTime

        override fun isMetaEventMessage(message: MidiMessage, metaType: Byte) = message.event.eventType == MidiEventType.META && message.event.msb == metaType

        override fun getTempoValue(message: MidiMessage) = MidiMetaType.getTempo(message.event.extraData!!, message.event.extraDataOffset)
    }

    companion object {
        private val calc = SmfDeltaTimeComputer()

        fun getMetaEventsOfType(messages: Iterable<MidiMessage>, metaType: Byte)
            = calc.getMetaEventsOfType(messages, metaType).map { p -> MidiMessage(p.first, p.second.event) }

        fun getTotalPlayTimeMilliseconds(messages: Iterable<MidiMessage>, deltaTimeSpec: Int) = calc.getTotalPlayTimeMilliseconds(messages, deltaTimeSpec)

        fun getPlayTimeMillisecondsAtTick(messages: Iterable<MidiMessage>, ticks: Int, deltaTimeSpec: Int) = calc.getPlayTimeMillisecondsAtTick(messages, ticks, deltaTimeSpec)
    }

    val tracks: MutableList<MidiTrack> = mutableListOf()

    var deltaTimeSpec: Int = 0

    var format: Byte = 0

    fun addTrack(track: MidiTrack) {
        this.tracks.add(track)
    }

    fun getMetaEventsOfType(metaType: Byte): Iterable<MidiMessage> {
        if (format != 0.toByte())
            return SmfTrackMerger.merge(this).getMetaEventsOfType(metaType)
        return getMetaEventsOfType(tracks[0].messages, metaType).asIterable()
    }

    fun getTotalTicks(): Int {
        if (format != 0.toByte())
            return SmfTrackMerger.merge(this).getTotalTicks()
        return tracks[0].messages.sumBy { m: MidiMessage -> m.deltaTime }
    }

    fun getTotalPlayTimeMilliseconds(): Int {
        if (format != 0.toByte())
            return SmfTrackMerger.merge(this).getTotalPlayTimeMilliseconds()
        return getTotalPlayTimeMilliseconds(tracks[0].messages, deltaTimeSpec)
    }

    fun getTimePositionInMillisecondsForTick(ticks: Int): Int {
        if (format != 0.toByte())
            return SmfTrackMerger.merge(this).getTimePositionInMillisecondsForTick(ticks)
        return getPlayTimeMillisecondsAtTick(tracks[0].messages, ticks, deltaTimeSpec)
    }

    init {
        this.format = 1
    }
}

class MidiTrack(val messages: MutableList<MidiMessage> = mutableListOf())

class MidiMessage(val deltaTime: Int, evt: MidiEvent) {

    val event: MidiEvent = evt
}

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
                    if (end < i + fixedDataSize(bytes[i]))
                        throw Exception("Received data was incomplete to build MIDI status message for '${bytes[i]}' status.")
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

    @ExperimentalUnsignedTypes
    constructor (
        type: Int,
        arg1: Int,
        arg2: Int,
        extraData: ByteArray?,
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
            when (statusByte) {
                MidiEventType.META,
                MidiEventType.SYSEX,
                MidiEventType.SYSEX_END -> this.statusByte
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


class SmfTrackMerger(private var source: MidiMusic) {
    companion object {

        fun merge(source: MidiMusic): MidiMusic {
            return SmfTrackMerger(source).getMergedMessages()
        }
    }

    private fun getMergedMessages(): MidiMusic {
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

open class SmfTrackSplitter(private val source: MutableList<MidiMessage>, private val deltaTimeSpec: Byte) {
    companion object {
        fun split(source: MutableList<MidiMessage>, deltaTimeSpec: Byte): MidiMusic {
            return SmfTrackSplitter(source, deltaTimeSpec).split()
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
    open fun getTrackID(e: MidiMessage): Int {
        return when (e.event.eventType) {
            MidiEventType.META, MidiEventType.SYSEX, MidiEventType.SYSEX_END -> -1
            else -> e.event.channel.toUnsigned()
        }
    }

    private fun split(): MidiMusic {
        var totalDeltaTime = 0
        for (e in source) {
            totalDeltaTime += e.deltaTime
            val id: Int = getTrackID(e)
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
