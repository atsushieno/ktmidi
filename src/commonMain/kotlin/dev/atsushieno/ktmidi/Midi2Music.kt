package dev.atsushieno.ktmidi

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

class MidiPerNoteManagementFlags { // MIDI 2.0
    companion object {
        const val RESET = 1
        const val DETACH = 2
    }
}

class Midi2SystemMessageType {
    companion object {
        const val NOP = 0
        const val JR_CLOCK = 1
        const val JR_TIMESTAMP = 2
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
class Ump(val int1: Int, val int2: Int = 0, val int3: Int = 0, val int4: Int = 0) {

    val groupByte: Int
        get() = int1 shr 24

    // First half of the 1st. byte
    // 32bit: UMP category 0 (NOP / Clock), 1 (System) and 2 (MIDI 1.0)
    // 64bit: UMP category 3 (SysEx7) and 4 (MIDI 2.0)
    // 128bit: UMP category 5 (SysEx8 and Mixed Data Set)
    val category: Int
        get() = (int1 shr 28) and 0x7

    // Second half of the 1st. byte
    val group: Int
        get() = (int1 shr 24) and 0xF

    // 2nd. byte
    val statusByte: Int
        get() = (int1 shr 16) and 0xFF

    // First half of the 2nd. byte.
    // This makes sense only for MIDI 1.0, MIDI 2.0, and System messages
    val eventType: Int
        get() =
            when (category) {
                MidiMessageType.MIDI1, MidiMessageType.MIDI2 -> statusByte and 0xF0
                else -> statusByte
            }

    // Second half of the 2nd. byte
    val channelInGroup: Int // 0..15
        get() = statusByte and 0xF

    val groupAndChannel: Int // 0..255
        get() = group shl 4 and channelInGroup

    // 3rd. byte for MIDI 1.0 message
    val midi1Msb: Int
        get() = (int1 and 0xFF00) shr 8

    // 4th. byte for MIDI 1.0 message
    val midi1Lsb: Int
        get() = (int1 and 0xFF0000) shr 16

    override fun toString(): String {
        return when(category) {
            0, 1, 2 -> "[${int1.toString(16)}]"
            3, 4 -> "[${int1.toString(16)}:${int2.toString(16)}]"
            else -> "[${int1.toString(16)}:${int2.toString(16)}:${int3.toString(16)}:${int4.toString(16)}]"
        }
    }
}

class Midi2Track(val messages: MutableList<Ump> = mutableListOf())

class Midi2Music {
    companion object {
        fun getMetaEventsOfType(messages: Iterable<Ump>, metaType: Int) = sequence {
            var v = 0
            for (m in messages) {
                if (m.category == MidiMessageType.UTILITY && m.eventType == Midi2SystemMessageType.JR_TIMESTAMP)
                    v += (m.midi1Msb shl 8) + m.midi1Lsb
                // FIXME: We should come up with some solid draft on this, but so far, META events are
                //  going to be implemented as Sysex7 messages.
                if (m.category == MidiMessageType.SYSEX7 && m.midi1Msb == MidiEventType.META.toInt() && m.midi1Lsb == metaType)
                    yield(Pair(v, m))
            }
        }.asIterable()

        fun getTotalPlayTimeMilliseconds(messages: Iterable<Ump>) =
            messages.filter { m -> m.category == MidiMessageType.UTILITY && m.eventType == Midi2SystemMessageType.JR_TIMESTAMP }
                .sumBy { m -> (m.midi1Msb shl 8) + m.midi1Lsb }
    }

    val tracks: MutableList<Midi2Track> = mutableListOf()

    // Not sure if we can support them.
    var deltaTimeSpec: Int = 0

    var format: Byte = 0

    fun addTrack(track: Midi2Track) {
        this.tracks.add(track)
    }

    fun getMetaEventsOfType(metaType: Int): Iterable<Pair<Int,Ump>> {
        // FIXME: implement merger
        //if (format != 0.toByte())
        //    return SmfTrackMerger.merge(this).getMetaEventsOfType(metaType)
        return getMetaEventsOfType(tracks[0].messages, metaType).asIterable()
    }

    /*
    fun getTotalTicks(): Int {
        // FIXME: implement merger
        //if (format != 0.toByte())
        //    return SmfTrackMerger.merge(this).getTotalTicks()
        return tracks[0].messages.sumBy { m: MidiMessage -> m.deltaTime }
    }
    */

    fun getTotalPlayTimeMilliseconds(): Int {
        // FIXME: implement merger
        //if (format != 0.toByte())
        //    return SmfTrackMerger.merge(this).getTotalPlayTimeMilliseconds()
        return getTotalPlayTimeMilliseconds(tracks[0].messages)
    }

    /*
    fun getTimePositionInMillisecondsForTick(ticks: Int): Int {
        // FIXME: implement merger
        //if (format != 0.toByte())
        //    return SmfTrackMerger.merge(this).getTimePositionInMillisecondsForTick(ticks)
        return getPlayTimeMillisecondsAtTick(tracks[0].messages, ticks, deltaTimeSpec)
    }
    */

    init {
        this.format = 1
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
                for (i in 0 until delta / 0x10000)
                    track.messages.add(Ump((e.int1 and 0x0F000000 or 0x200000) + 0xFFFF))
                while (delta > 0)
                    track.messages.add(Ump((e.int1 and 0x0F000000 or 0x200000) + delta % 0x10000))
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
    open fun getTrackID(e: Ump) = e.groupAndChannel

    private fun split(): Midi2Music {
        var totalDeltaTime = 0
        for (e in source) {
            if (e.category == MidiMessageType.UTILITY && e.eventType == Midi2SystemMessageType.JR_TIMESTAMP)
                totalDeltaTime += (e.midi1Msb shl 16) + e.midi1Lsb
            val id: Int = getTrackID(e)
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
