package dev.atsushieno.ktmidi

import dev.atsushieno.ktmidi.umpfactory.*

class MidiMessageType { // MIDI 2.0
    companion object {
        const val UTILITY = 0
        const val SYSTEM = 1
        const val MIDI1 = 2
        const val SYSEX7 = 3
        const val MIDI2 = 4
        const val SYSEX8_MDS = 5
    }
}

class MidiCIProtocolBytes { // MIDI 2.0
    companion object {
        const val TYPE = 0
        const val VERSION = 1
        const val EXTENSIONS = 2
    }
}

class MidiCIProtocolType { // MIDI 2.0
    companion object {
        const val MIDI1 = 1
        const val MIDI2 = 2
    }
}

class MidiCIProtocolValue { // MIDI 2.0
    companion object {
        const val MIDI1 = 0
        const val MIDI2_V1 = 0
    }
}

class MidiCIProtocolExtensions { // MIDI 2.0
    companion object {
        const val JITTER = 1
        const val LARGER = 2
    }
}

class MidiAttributeType { // MIDI 2.0
    companion object {
        const val NONE = 0
        const val MANUFACTURER_SPECIFIC = 1
        const val PROFILE_SPECIFIC = 2
        const val Pitch7_9 = 3
    }
}

class MidiPerNoteManagementFlags { // MIDI 2.0
    companion object {
        const val RESET = 1
        const val DETACH = 2
    }
}

class Midi2SystemMessageType {
    companion object {
        const val NOP = 0
        const val JR_CLOCK = 0x10
        const val JR_TIMESTAMP = 0x20
    }
}

class Midi2BinaryChunkStatus {
    companion object {
        const val SYSEX_IN_ONE_UMP = 0
        const val SYSEX_START = 0x10
        const val SYSEX_CONTINUE = 0x20
        const val SYSEX_END = 0x30
        const val MDS_HEADER = 0x80
        const val MDS_PAYLOAD = 0x90
    }
}

class MidiPerNoteRCC // MIDI 2.0
{
    companion object {
        const val MODULATION = 0x01.toByte()
        const val BREATH = 0x02.toByte()
        const val PITCH_7_25 = 0x03.toByte()
        const val VOLUME = 0x07.toByte()
        const val BALANCE = 0x08.toByte()
        const val PAN = 0x0A.toByte()
        const val EXPRESSION = 0x0B.toByte()
        const val SOUND_CONTROLLER_1 = 0x46.toByte()
        const val SOUND_CONTROLLER_2 = 0x47.toByte()
        const val SOUND_CONTROLLER_3 = 0x48.toByte()
        const val SOUND_CONTROLLER_4 = 0x49.toByte()
        const val SOUND_CONTROLLER_5 = 0x4A.toByte()
        const val SOUND_CONTROLLER_6 = 0x4B.toByte()
        const val SOUND_CONTROLLER_7 = 0x4C.toByte()
        const val SOUND_CONTROLLER_8 = 0x4D.toByte()
        const val SOUND_CONTROLLER_9 = 0x4E.toByte()
        const val SOUND_CONTROLLER_10 = 0x4F.toByte()
        const val EFFECT_1_DEPTH = 0x5B.toByte() // Reverb Send Level by default
        const val EFFECT_2_DEPTH = 0x5C.toByte() // formerly Tremolo Depth
        const val EFFECT_3_DEPTH = 0x5D.toByte() // Chorus Send Level by default
        const val EFFECT_4_DEPTH = 0x5E.toByte() // formerly Celeste (Detune) Depth
        const val EFFECT_5_DEPTH = 0x5F.toByte() // formerly Phaser Depth
    }
}

// We store UMP in Big Endian this time.
data class Ump(val int1: Int, val int2: Int = 0, val int3: Int = 0, val int4: Int = 0) {
    override fun toString(): String {
        return when(messageType) {
            0, 1, 2 -> "[${int1.toString(16)}]"
            3, 4 -> "[${int1.toString(16)}:${int2.toString(16)}]"
            else -> "[${int1.toString(16)}:${int2.toString(16)}:${int3.toString(16)}:${int4.toString(16)}]"
        }
    }
}

class Midi2Track(val messages: MutableList<Ump> = mutableListOf())

class Midi2Music {
    class UmpDeltaTimeComputer: DeltaTimeComputer<Ump>() {
        override fun messageToDeltaTime(message: Ump) = if (message.isJRTimestamp) message.jrTimestamp else 0

        // FIXME: We should come up with some solid draft on this, but so far, META events are
        //  going to be implemented as Sysex8 messages. Starts with [0xFF MetaType] ...
        override fun isMetaEventMessage(message: Ump, metaType: Byte)
            = message.messageType == MidiMessageType.SYSEX8_MDS &&
                message.eventType == Midi2BinaryChunkStatus.SYSEX_IN_ONE_UMP &&
                message.int1 % 0x100 == 0 && // first 4 bytes indicate manufacturer ID ... subID2, filled with 0
                message.int2 / 0x100 == 0 &&
                message.int2 % 0x100 == MidiEventType.META.toInt() &&
                (message.int3 shr 24) == metaType.toUnsigned()

        // 3 bytes in Sysex8 pseudo meta message
        override fun getTempoValue(message: Ump)
            = if (isMetaEventMessage(message, MidiMetaType.TEMPO)) message.int3 % 0x1000000
            else throw IllegalArgumentException("Attempt to calculate tempo from non-meta UMP")
    }

    companion object {
        private val calc = UmpDeltaTimeComputer()

        fun getMetaEventsOfType(messages: Iterable<Ump>, metaType: Byte) =
            calc.getMetaEventsOfType(messages, metaType)

        fun getTotalPlayTimeMilliseconds(messages: Iterable<Ump>, deltaTimeSpec: Int) =
            if (deltaTimeSpec > 0)
                calc.getTotalPlayTimeMilliseconds(messages, deltaTimeSpec)
            else
                messages.filter { m -> m.isJRTimestamp }.sumBy { m -> m.jrTimestamp } / 31250

        fun getPlayTimeMillisecondsAtTick(messages: Iterable<Ump>, ticks: Int, deltaTimeSpec: Int) =
            calc.getPlayTimeMillisecondsAtTick(messages, ticks, deltaTimeSpec)
    }

    val tracks: MutableList<Midi2Track> = mutableListOf()

    // This brings in kind of hack in the UMP content.
    // When a positive value is explicitly given, then it is interpreted as the same qutantization as what SMF does
    // and the actual "ticks" in JR Timestamp messages are delta time (i.e. fake), not 1/31250 msec.
    var deltaTimeSpec: Int = 0

    // There is no "format" specifier in this format but we leave it so far.
    var format: Byte = 0

    fun addTrack(track: Midi2Track) {
        this.tracks.add(track)
    }

    fun getMetaEventsOfType(metaType: Int): Iterable<Pair<Int,Ump>> {
        if (tracks.size > 1)
            return Midi2TrackMerger.merge(this).getMetaEventsOfType(metaType)
        return getMetaEventsOfType(tracks[0].messages, metaType.toByte()).asIterable()
    }

    fun getTotalTicks(): Int {
        if (format != 0.toByte())
            return Midi2TrackMerger.merge(this).getTotalTicks()
        return tracks[0].messages.sumBy { m: Ump -> m.jrTimestamp }
    }

    fun getTotalPlayTimeMilliseconds(): Int {
        if (format != 0.toByte())
            return Midi2TrackMerger.merge(this).getTotalPlayTimeMilliseconds()
        return getTotalPlayTimeMilliseconds(tracks[0].messages, deltaTimeSpec)
    }

    fun getTimePositionInMillisecondsForTick(ticks: Int): Int {
        if (format != 0.toByte())
            return Midi2TrackMerger.merge(this).getTimePositionInMillisecondsForTick(ticks)
        return getPlayTimeMillisecondsAtTick(tracks[0].messages, ticks, deltaTimeSpec)
    }

    init {
        this.format = 1
    }
}

class Midi2TrackMerger(private var source: Midi2Music) {
    companion object {

        fun merge(source: Midi2Music): Midi2Music {
            return Midi2TrackMerger(source).getMergedMessages()
        }
    }

    private fun getMergedMessages(): Midi2Music {
        var l = mutableListOf<Pair<Int,Ump>>()

        for (track in source.tracks) {
            var absTime = 0
            for (mev in track.messages) {
                if (mev.isJRTimestamp)
                    absTime += mev.jrTimestamp
                else
                    l.add(Pair(absTime, mev))
            }
        }

        if (l.size == 0)
            return Midi2Music()

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
            if (l[i].first != prev) {
                indexList.add(i)
                prev = l[i].first
            }
            i++
        }
        val idxOrdered = indexList.sortedBy { n -> l[n].first }

        // now build a new event list based on the sorted blocks.
        val l2 = mutableListOf<Pair<Int,Ump>>()
        var idx: Int
        i = 0
        while (i < idxOrdered.size) {
            idx = idxOrdered[i]
            val absTime = l[idx].first
            while (idx < l.size && l[idx].first == absTime) {
                l2.add(l[idx])
                idx++
            }
            i++
        }
        l = l2

        // now messages should be sorted correctly.

        val l3 = mutableListOf<Ump>()
        var timeAbs = l[0].first
        i = 0
        while (i < l.size) {
            if (i + 1 < l.size) {
                val delta = l[i + 1].first - l[i].first
                if (delta > 0)
                    l3.addAll(umpJRTimestamps(l[i].second.group, delta.toLong()).map { v -> Ump(v) })
                timeAbs += delta
            }
            l3.add(l[i].second)
            i++
        }

        val m = Midi2Music()
        m.format = 0
        m.deltaTimeSpec = source.deltaTimeSpec
        m.tracks.add(Midi2Track(l3))
        return m
    }
}


open class Midi2TrackSplitter(private val source: MutableList<Ump>) {
    companion object {
        fun split(source: MutableList<Ump>): Midi2Music {
            return Midi2TrackSplitter(source).split()
        }
    }

    private var tracks = HashMap<Int, SplitTrack>()

    internal class SplitTrack(val trackID: Int) {

        private var currentTimestamp: Int
        val track = Midi2Track()

        fun addMessage(timestampInsertAt: Int, e: Ump) {
            if (currentTimestamp != timestampInsertAt) {
                val delta = timestampInsertAt - currentTimestamp
                track.messages.addAll(umpJRTimestamps(e.group, delta.toLong()).map {i -> Ump(i)})
            }
            track.messages.add(e)
            currentTimestamp = timestampInsertAt
        }

        init {
            currentTimestamp = 0
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
    open fun getTrackId(e: Ump) = e.groupAndChannel

    private fun split(): Midi2Music {
        var totalDeltaTime = 0
        for (e in source) {
            if (e.isJRTimestamp)
                totalDeltaTime += e.jrTimestamp
            val id: Int = getTrackId(e)
            getTrack(id).addMessage(totalDeltaTime, e)
        }

        val m = Midi2Music()
        for (t in tracks.values)
            m.tracks.add(t.track)
        return m
    }

    init {
        val mtr = SplitTrack(-1)
        tracks[-1] = mtr
    }
}

class Midi2DeltaTimeConverter private constructor(private val source: Midi2Music) {
    companion object {
        fun convertDeltaTimeToJRTimestamp(source: Midi2Music) =
            if (source.deltaTimeSpec > 0) Midi2DeltaTimeConverter(source).deltaTimetoJRTimestamp() else source
    }

    fun deltaTimetoJRTimestamp() : Midi2Music {
        val result = Midi2Music().apply {
            format = source.format
            deltaTimeSpec = 0
        }

        val dtc = Midi2Music.UmpDeltaTimeComputer()

        // first, get all meta events to detect tempo changes for later uses.
        val allTempoChanges = source.getMetaEventsOfType(MidiMetaType.TEMPO.toInt()).toList()

        for (srcTrack in source.tracks) {
            val dstTrack = Midi2Track()
            result.addTrack(dstTrack)

            var currentTicks = 0
            var currentTempo = MidiMetaType.DEFAULT_TEMPO
            var nextTempoChangeIndex = 0

            for (ump in srcTrack.messages) {
                if (ump.isJRTimestamp) {
                    // There may be tempo changes in other tracks, which affects the total length of a
                    // timestamp message when it is converted to milliseconds. We have to calculate it
                    // taking those tempo changes into consideration.
                    var remainingTicks = ump.jrTimestamp
                    var durationMs = 0.0
                    while (nextTempoChangeIndex < allTempoChanges.size && allTempoChanges[nextTempoChangeIndex].first < currentTicks + remainingTicks) {
                        val ticksUntilNextTempoChange = allTempoChanges[nextTempoChangeIndex].first - currentTicks
                        durationMs += getContextDeltaTimeInMilliseconds(ticksUntilNextTempoChange, currentTempo, source.deltaTimeSpec)
                        currentTempo = dtc.getTempoValue(allTempoChanges[nextTempoChangeIndex].second)
                        nextTempoChangeIndex++
                        remainingTicks -= ticksUntilNextTempoChange
                        currentTicks += ticksUntilNextTempoChange
                    }
                    durationMs += getContextDeltaTimeInMilliseconds(remainingTicks, currentTempo, source.deltaTimeSpec)
                    currentTicks += remainingTicks
                    dstTrack.messages.addAll(umpJRTimestamps(ump.group, durationMs / 1000.0).map { i -> Ump(i)})
                }
                else
                    dstTrack.messages.add(ump)
            }
        }

        return result
    }

    private fun getContextDeltaTimeInMilliseconds(ticksUntilNextTempoChange: Int, currentTempo: Int, deltaTimeSpec: Int) : Double =
        currentTempo.toDouble() / 1000 * ticksUntilNextTempoChange / deltaTimeSpec
}
