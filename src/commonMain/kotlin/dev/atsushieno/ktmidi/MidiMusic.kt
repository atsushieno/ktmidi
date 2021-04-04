@file:Suppress("unused")

package dev.atsushieno.ktmidi

import kotlin.math.floor

fun Byte.toUnsigned() = if (this < 0) 256 + this else this.toInt()

class MidiMusic {
    companion object {
        fun getMetaEventsOfType(messages: Iterable<MidiMessage>, metaType: Byte) = sequence {
            var v = 0
            for (m in messages) {
                v += m.deltaTime
                if (m.event.eventType == MidiEventType.META && m.event.msb == metaType)
                    yield(MidiMessage(v, m.event))
            }
        }

        fun getTotalPlayTimeMilliseconds(messages: MutableList<MidiMessage>, deltaTimeSpec: Int): Int {
            return getPlayTimeMillisecondsAtTick(messages, messages.sumBy { m -> m.deltaTime }, deltaTimeSpec)
        }

        fun getPlayTimeMillisecondsAtTick(messages: List<MidiMessage>, ticks: Int, deltaTimeSpec: Int): Int {
            if (deltaTimeSpec < 0)
                throw UnsupportedOperationException("non-tick based DeltaTime")
            else {
                var tempo: Int = MidiMetaType.DEFAULT_TEMPO
                var v = 0.0
                var t = 0
                for (m in messages) {
                    val deltaTime = if (t + m.deltaTime < ticks) m.deltaTime else ticks - t
                    v += tempo.toDouble() / 1000 * deltaTime / deltaTimeSpec
                    if (deltaTime != m.deltaTime)
                        break
                    t += m.deltaTime
                    if (m.event.eventType == MidiEventType.META && m.event.msb == MidiMetaType.TEMPO)
                        tempo = MidiMetaType.getTempo(m.event.extraData!!, m.event.extraDataOffset)
                }
                return v.toInt()
            }
        }
    }

    val tracks: MutableList<MidiTrack> = ArrayList()

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

class MidiTrack {
    constructor ()
            : this(ArrayList<MidiMessage>())

    constructor(messages: MutableList<MidiMessage>?) {
        if (messages == null)
            throw IllegalArgumentException("null messages")
        this.messages = messages
    }

    var messages: MutableList<MidiMessage> = ArrayList()

    fun addMessage(msg: MidiMessage) {
        messages.add(msg)
    }
}

class MidiMessage(val deltaTime: Int, evt: MidiEvent) {

    val event: MidiEvent = evt
}

class MidiCC {
    companion object {

        const val BANK_SELECT = 0x00.toByte()
        const val MODULATION = 0x01.toByte()
        const val BREATH = 0x02.toByte()
        const val FOOT = 0x04.toByte()
        const val PORTAMENTO_TIME = 0x05.toByte()
        const val DTE_MSB = 0x06.toByte()
        const val VOLUME = 0x07.toByte()
        const val BALANCE = 0x08.toByte()
        const val PAN = 0x0A.toByte()
        const val EXPRESSION = 0x0B.toByte()
        const val EFFECT_CONTROL_1 = 0x0C.toByte()
        const val EFFECT_CONTROL_2 = 0x0D.toByte()
        const val GENERAL_1 = 0x10.toByte()
        const val GENERAL_2 = 0x11.toByte()
        const val GENERAL_3 = 0x12.toByte()
        const val GENERAL_4 = 0x13.toByte()
        const val BANK_SELECT_LSB = 0x20.toByte()
        const val MODULATION_LSB = 0x21.toByte()
        const val BREATH_LSB = 0x22.toByte()
        const val FOOT_LSB = 0x24.toByte()
        const val PORTAMENTO_TIME_LSB = 0x25.toByte()
        const val DTE_LSB = 0x26.toByte()
        const val VOLUME_LSB = 0x27.toByte()
        const val BALANCE_LSB = 0x28.toByte()
        const val PAN_LSB = 0x2A.toByte()
        const val EXPRESSION_LSB = 0x2B.toByte()
        const val EFFECT_1_LSB = 0x2C.toByte()
        const val EFFECT_2_LSB = 0x2D.toByte()
        const val GENERAL_1_LSB = 0x30.toByte()
        const val GENERAL_2_LSB = 0x31.toByte()
        const val GENERAL_3_LSB = 0x32.toByte()
        const val GENERAL_4_LSB = 0x33.toByte()
        const val HOLD = 0x40.toByte()
        const val PORTAMENTO_SWITCH = 0x41.toByte()
        const val SOSTENUTO = 0x42.toByte()
        const val SOFT_PEDAL = 0x43.toByte()
        const val LEGATO = 0x44.toByte()
        const val HOLD_2 = 0x45.toByte()
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
        const val GENERAL_5 = 0x50.toByte()
        const val GENERAL_6 = 0x51.toByte()
        const val GENERAL_7 = 0x52.toByte()
        const val GENERAL_8 = 0x53.toByte()
        const val PORTAMENTO_CONTROL = 0x54.toByte()
        const val RSD = 0x5B.toByte()
        const val EFFECT_1 = 0x5B.toByte()
        const val TREMOLO = 0x5C.toByte()
        const val EFFECT_2 = 0x5C.toByte()
        const val CSD = 0x5D.toByte()
        const val EFFECT_3 = 0x5D.toByte()
        const val CELESTE = 0x5E.toByte()
        const val EFFECT_4 = 0x5E.toByte()
        const val PHASER = 0x5F.toByte()
        const val EFFECT_5 = 0x5F.toByte()
        const val DTE_INCREMENT = 0x60.toByte()
        const val DTE_DECREMENT = 0x61.toByte()
        const val NRPN_LSB = 0x62.toByte()
        const val NRPN_MSB = 0x63.toByte()
        const val RPN_LSB = 0x64.toByte()
        const val RPN_MSB = 0x65.toByte()

        // Channel mode messages
        const val ALL_SOUND_OFF = 0x78.toByte()
        const val RESET_ALL_CONTROLLERS = 0x79.toByte()
        const val LOCAL_CONTROL = 0x7A.toByte()
        const val ALL_NOTES_OFF = 0x7B.toByte()
        const val OMNI_MODE_OFF = 0x7C.toByte()
        const val OMNI_MODE_ON = 0x7D.toByte()
        const val POLY_MODE_OFF = 0x7E.toByte()
        const val POLY_MODE_ON = 0x7F.toByte()
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

class MidiRpnType {
    companion object {

        const val PITCH_BEND_SENSITIVITY = 0.toByte()
        const val FINE_TUNING = 1.toByte()
        const val COARSE_TUNING = 2.toByte()
        const val TUNING_PROGRAM = 3.toByte()
        const val TUNING_BANK_SELECT = 4.toByte()
        const val MODULATION_DEPTH = 5.toByte()
    }
}

class MidiMetaType {
    companion object {

        const val SEQUENCE_NUMBER = 0x00.toByte()
        const val TEXT = 0x01.toByte()
        const val COPYRIGHT = 0x02.toByte()
        const val TRACK_NAME = 0x03.toByte()
        const val INSTRUMENT_NAME = 0x04.toByte()
        const val LYRIC = 0x05.toByte()
        const val MARKER = 0x06.toByte()
        const val CUE = 0x07.toByte()
        const val CHANNEL_PREFIX = 0x20.toByte()
        const val END_OF_TRACK = 0x2F.toByte()
        const val TEMPO = 0x51.toByte()
        const val SMTPE_OFFSET = 0x54.toByte()
        const val TIME_SIGNATURE = 0x58.toByte()
        const val KEY_SIGNATURE = 0x59.toByte()
        const val SEQUENCER_SPECIFIC = 0x7F.toByte()

        const val DEFAULT_TEMPO = 500000

        fun getTempo(data: ByteArray, offset: Int): Int {
            if (data.size < offset + 2)
                throw IndexOutOfBoundsException("data array must be longer than argument offset $offset + 2")
            return (data[offset].toUnsigned() shl 16) + (data[offset + 1].toUnsigned() shl 8) + data[offset + 2]
        }

        fun getBpm(data: ByteArray, offset: Int): Double {
            return 60000000.0 / getTempo(data, offset)
        }
    }
}

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

class MidiEventType {
    companion object {

        // MIDI 2.0-specific
        const val PER_NOTE_RCC: Byte = 0x00.toByte()
        const val PER_NOTE_ACC: Byte = 0x10.toByte()
        const val RPN: Byte = 0x20.toByte()
        const val NRPN: Byte = 0x30.toByte()
        const val RELATIVE_RPN: Byte = 0x40.toByte()
        const val RELATIVE_NRPN: Byte = 0x50.toByte()
        const val PER_NOTE_PITCH: Byte = 0x60.toByte()
        const val PER_NOTE_MANAGEMENT: Byte = 0xF0.toByte()

        // MIDI 1.0/2.0 common
        const val NOTE_OFF: Byte = 0x80.toByte()
        const val NOTE_ON: Byte = 0x90.toByte()
        const val PAF: Byte = 0xA0.toByte()
        const val CC: Byte = 0xB0.toByte()
        const val PROGRAM: Byte = 0xC0.toByte()
        const val CAF: Byte = 0xD0.toByte()
        const val PITCH: Byte = 0xE0.toByte()

        // MIDI 1.0-specific
        const val SYSEX: Byte = 0xF0.toByte()
        const val MTC_QUARTER_FRAME: Byte = 0xF1.toByte()
        const val SONG_POSITION_POINTER: Byte = 0xF2.toByte()
        const val SONG_SELECT: Byte = 0xF3.toByte()
        const val TUNE_REQUEST: Byte = 0xF6.toByte()
        const val SYSEX_END: Byte = 0xF7.toByte()
        const val MIDI_CLOCK: Byte = 0xF8.toByte()
        const val MIDI_TICK: Byte = 0xF9.toByte()
        const val MIDI_START: Byte = 0xFA.toByte()
        const val MIDI_CONTINUE: Byte = 0xFB.toByte()
        const val MIDI_STOP: Byte = 0xFC.toByte()
        const val ACTIVE_SENSE: Byte = 0xFE.toByte()
        const val RESET: Byte = 0xFF.toByte()
        const val META: Byte = 0xFF.toByte()
    }
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



class SmfTrackMerger(private var source: MidiMusic) {
    companion object {

        fun merge(source: MidiMusic): MidiMusic {
            return SmfTrackMerger(source).getMergedMessages()
        }
    }

    // FIXME: it should rather be implemented to iterate all
    // tracks with index to messages, pick the track which contains
    // the nearest event and push the events into the merged queue.
    // It's simpler, and costs less by removing sort operation
    // over thousands of events.
    private fun getMergedMessages(): MidiMusic {
        var l = ArrayList<MidiMessage>()

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

        // Sort() does not always work as expected.
        // For example, it does not always preserve event
        // orders on the same channels when the delta time
        // of event B after event A is 0. It could be sorted
        // either as A->B or B->A.
        //
        // To resolve this ieeue, we have to sort "chunk"
        // of events, not all single events themselves, so
        // that order of events in the same chunk is preserved
        // i.e. [AB] at 48 and [CDE] at 0 should be sorted as
        // [CDE] [AB].

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
        val idxOrdered = indexList.sortedBy { i -> l[i].deltaTime }
        //idxl.sortWith(Comparator { i1, i2 -> l[i1].deltaTime - l[i2].deltaTime })

        // now build a new event list based on the sorted blocks.
        val l2 = ArrayList<MidiMessage>(l.size)
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

class SmfTrackSplitter(var source: MutableList<MidiMessage>, deltaTimeSpec: Byte) {
    companion object {
        fun split(source: MutableList<MidiMessage>, deltaTimeSpec: Byte): MidiMusic {
            return SmfTrackSplitter(source, deltaTimeSpec).split()
        }
    }

    private var delta_time_spec = deltaTimeSpec
    private var tracks = HashMap<Int, SplitTrack>()

    internal class SplitTrack(var trackID: Int) {

        var totalDeltaTime: Int
        var track: MidiTrack = MidiTrack()

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
    private fun getTrackID(e: MidiMessage): Int {
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
        m.deltaTimeSpec = delta_time_spec.toInt()
        for (t in tracks.values)
            m.tracks.add(t.track)
        return m
    }

    init {
        val mtr = SplitTrack(-1)
        tracks[-1] = mtr
    }
}
