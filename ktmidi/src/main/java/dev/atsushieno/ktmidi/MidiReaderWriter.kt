package dev.atsushieno.ktmidi

import java.io.InputStream
import java.io.OutputStream

internal fun OutputStream.writeByte(b : Byte)
{
    val arr = byteArrayOf(b)
    this.write(arr, 0, 1)
}

class SmfWriter(var stream: OutputStream) {

    var disableRunningStatus : Boolean = false

    private fun writeShort (v: Short)
    {
        stream.writeByte ((v / 0x100).toByte())
        stream.writeByte ((v % 0x100).toByte())
    }

    private fun writeInt (v : Int)
    {
        stream.writeByte ((v / 0x1000000).toByte())
        stream.writeByte ((v / 0x10000 and 0xFF).toByte())
        stream.writeByte ((v / 0x100 and 0xFF).toByte())
        stream.writeByte ((v % 0x100).toByte())
    }

    fun writeMusic (music : MidiMusic)
    {
        writeHeader (music.format.toShort(), music.tracks.size.toShort(), music.deltaTimeSpec.toShort())
        for (track in music.tracks)
            writeTrack (track)
    }

    fun writeHeader (format : Short, tracks : Short, deltaTimeSpec : Short)
    {
        stream.write (byteArrayOf('M'.toByte(), 'T'.toByte(), 'h'.toByte(), 'd'.toByte()), 0, 4)
        writeShort (0)
        writeShort (6)
        writeShort (format)
        writeShort (tracks)
        writeShort (deltaTimeSpec)
    }

    var metaEventWriter : (Boolean, MidiMessage, OutputStream?) -> Int = SmfWriterExtension.DEFAULT_META_EVENT_WRITER

    fun writeTrack (track : MidiTrack)
    {
        stream.write (byteArrayOf('M'.toByte(), 'T'.toByte(), 'r'.toByte(), 'k'.toByte()), 0, 4)
        writeInt (getTrackDataSize (track))

        var running_status : Byte = 0

        for (e in track.messages) {
            write7BitVariableInteger (e.deltaTime)
            when (e.event.eventType) {
                MidiEventType.META -> metaEventWriter(false, e, stream)
                MidiEventType.SYSEX, MidiEventType.SYSEX_END -> {
                    stream.writeByte(e.event.eventType)
                    if (e.event.extraData != null) {
                        write7BitVariableInteger(e.event.extraDataLength)
                        stream.write(e.event.extraData, e.event.extraDataOffset, e.event.extraDataLength)
                    }
                }
                else -> {
                    if (disableRunningStatus || e.event.statusByte != running_status)
                        stream.writeByte(e.event.statusByte)
                    val len = MidiEvent.fixedDataSize (e.event.eventType)
                    stream.writeByte(e.event.msb)
                    if (len > 1)
                        stream.writeByte(e.event.lsb)
                    if (len > 2)
                        throw Exception ("Unexpected data size: $len")
                }
            }
            running_status = e.event.statusByte
        }
    }

    private fun getVariantLength (value : Int) : Int
    {
        if (value < 0)
            throw IllegalArgumentException (String.format ("Length must be non-negative integer: %d", value))
        if (value == 0)
            return 1
        var ret = 0
        var x : Int = value
        while (x != 0) {
            ret++
            x = x shr 7
        }
        return ret
    }

    private fun getTrackDataSize (track : MidiTrack ) : Int
    {
        var size = 0
        var runningStatus : Byte = 0
        for (e in track.messages) {
            // delta time
            size += getVariantLength (e.deltaTime)

            // arguments
            when (e.event.eventType) {
                MidiEventType.META -> size += metaEventWriter (true, e, null)
                MidiEventType.SYSEX, MidiEventType.SYSEX_END -> {
                    size++
                    if (e.event.extraData != null) {
                        size += getVariantLength(e.event.extraDataLength)
                        size += e.event.extraDataLength
                    }
                }
                else -> {
                    // message type & channel
                    if (disableRunningStatus || runningStatus != e.event.statusByte)
                        size++
                    size += MidiEvent.fixedDataSize(e.event.eventType)
                }
            }

            runningStatus = e.event.statusByte
        }
        return size
    }

    private fun write7BitVariableInteger (value : Int)
    {
        write7BitVariableInteger (value, false)
    }

    private fun write7BitVariableInteger (value : Int, shifted : Boolean)
    {
        if (value == 0) {
            stream.writeByte ((if (shifted) 0x80 else 0).toByte())
            return
        }
        if (value >= 0x80)
            write7BitVariableInteger (value shr 7, true)
        stream.writeByte (((value and 0x7F) + if (shifted) 0x80 else 0).toByte())
    }

}

class SmfWriterExtension
{
    companion object {

        val DEFAULT_META_EVENT_WRITER : (Boolean, MidiMessage, OutputStream?) -> Int = { b, m, o -> defaultMetaWriterFunc (b,m,o) }

        private fun defaultMetaWriterFunc (lengthMode : Boolean, e : MidiMessage , stream : OutputStream?) : Int
        {
            if (e.event.extraData == null || stream == null)
                return 0

            if (lengthMode) {
                // [0x00] 0xFF metaType size ... (note that for more than one meta event it requires step count of 0).
                val repeatCount : Int = e.event.extraDataLength / 0x7F
                if (repeatCount == 0)
                    return 3 + e.event.extraDataLength
                val mod : Int = e.event.extraDataLength % 0x7F
                return repeatCount * (4 + 0x7F) - 1 + if (mod > 0) 4 + mod else 0
            }

            var written = 0
            val total : Int = e . event.extraDataLength
            while (written < total) {
                if (written > 0)
                    stream.writeByte(0) // step
                stream.writeByte(0xFF.toByte())
                stream.writeByte(e.event.metaType)
                val size = Math.min(0x7F, total - written)
                stream.writeByte(size.toByte())
                stream.write(e.event.extraData, e.event.extraDataOffset + written, size)
                written += size
            }
            return 0
        }

        val vsqMetaTextSplitter : (Boolean, MidiMessage, OutputStream) -> Int = { b, m, o -> vsqMetaTextSplitterFunc (b,m,o) }

        private fun vsqMetaTextSplitterFunc (lengthMode : Boolean, e : MidiMessage , stream : OutputStream?) : Int
        {
            if (e.event.extraData == null)
                return 0

            // The split should not be applied to "Master Track"
            if (e.event.extraDataLength < 0x80) {
                return DEFAULT_META_EVENT_WRITER(lengthMode, e, stream)
            }

            if (lengthMode) {
                // { [0x00] 0xFF metaType DM:xxxx:... } * repeat + 0x00 0xFF metaType DM:xxxx:mod...
                // (note that for more than one meta event it requires step count of 0).
                val repeatCount = e.event.extraDataLength / 0x77
                if (repeatCount == 0)
                    return 11 + e.event.extraDataLength
                val mod = e.event.extraDataLength % 0x77
                return repeatCount * (12 + 0x77) - 1 + if (mod > 0) 12+mod else 0
            }

            if (stream == null)
                return 0


            var written = 0
            val total: Int = e.event.extraDataLength
            var idx = 0
            do {
                if (written > 0)
                    stream.writeByte(0.toByte()) // step
                stream.writeByte(0xFF.toByte())
                stream.writeByte(e.event.metaType)
                val size = Math.min(0x77, total - written)
                stream.writeByte((size + 8).toByte())
                stream.write(String.format("DM:{0:D04}:", idx++).toByteArray(), 0, 8)
                stream.write(e.event.extraData, e.event.extraDataOffset + written, size)
                written += size
            } while (written < total)
            return 0
        }
    }
}

class SmfReader(private var stream: InputStream) {

    companion object {
        fun read (stream : InputStream) : MidiMusic {
            val r = SmfReader(stream)
            r.read()
            return r.music
        }
    }

    var music = MidiMusic ()

    private val data = music

    fun read () {
        if (readByte() != 'M'.toByte()
            || readByte() != 'T'.toByte()
            || readByte() != 'h'.toByte()
            || readByte() != 'd'.toByte())
            throw parseError("MThd is expected")
        if (readInt32() != 6)
            throw parseError("Unexpected data size (should be 6)")
        data.format = readInt16().toByte()
        val tracks = readInt16()
        data.deltaTimeSpec = readInt16().toByte()
        for (i in 0 until tracks)
            data.tracks.add(readTrack())
    }

    private fun readTrack () : MidiTrack {
        val tr = MidiTrack()
        if (
            readByte() != 'M'.toByte()
            || readByte() != 'T'.toByte()
            || readByte() != 'r'.toByte()
            || readByte() != 'k'.toByte())
            throw parseError("MTrk is expected")
        val trackSize = readInt32()
        current_track_size = 0
        var total = 0
        while (current_track_size < trackSize) {
            val delta = readVariableLength()
            tr.messages.add(readMessage(delta))
            total += delta
        }
        if (current_track_size != trackSize)
            throw parseError("Size information mismatch")
        return tr
    }

    private var current_track_size : Int = 0
    private var running_status : Int = 0

    private fun readMessage (deltaTime : Int) : MidiMessage
    {
        val b = peekByte ().toUnsigned()
        running_status = if (b < 0x80) running_status else readByte ().toUnsigned()
        val len: Int
        when (running_status) {
            MidiEventType.SYSEX.toUnsigned(), MidiEventType.SYSEX_END.toUnsigned(), MidiEventType.META.toUnsigned() -> {
                val metaType = if (running_status == MidiEventType.META.toUnsigned()) readByte () else 0
                len = readVariableLength()
                val args = ByteArray(len)
                if (len > 0)
                    readBytes(args)
                return MidiMessage (deltaTime, MidiEvent (running_status, metaType.toUnsigned(), 0, args, 0, len))
            }
            else -> {
                var value = running_status
                value += readByte().toUnsigned() shl 8
                if (MidiEvent.fixedDataSize(running_status.toByte()) == 2.toByte())
                    value += readByte().toUnsigned() shl 16
                return MidiMessage (deltaTime, MidiEvent (value))
            }
        }
    }

    private fun readBytes (args : ByteArray)
    {
        current_track_size += args.size
        var start = 0
        if (peek_byte >= 0) {
            args [0] = peek_byte.toByte()
            peek_byte = -1
            start = 1
        }
        val len = stream.read (args, start, args.size - start)
        try {
            if (len < args.size - start)
                throw parseError (String.format ("The stream is insufficient to read %d bytes specified in the SMF message. Only %d bytes read.", args.size, len))
        } finally {
            stream_position += len
        }
    }

    private fun readVariableLength () : Int
    {
        var v = 0
        var i = 0
        while (i < 4) {
            val b = readByte ().toUnsigned()
            v = (v shl 7) + b
            if (b < 0x80)
                return v
            v -= 0x80
            i++
        }
        throw parseError ("Delta time specification exceeds the 4-byte limitation.")
    }

    private var peek_byte : Int = -1
    private var stream_position : Int = 0

    private fun peekByte () : Byte
    {
        if (peek_byte < 0)
            peek_byte = stream.read()
        if (peek_byte < 0)
            throw parseError ("Insufficient stream. Failed to read a byte.")
        return peek_byte.toByte()
    }

    private fun readByte () : Byte
    {
        try {

            current_track_size++
            if (peek_byte >= 0) {
                val b = peek_byte.toByte()
                peek_byte = -1
                return b
            }
            val ret = stream.read()
            if (ret < 0)
                throw parseError ("Insufficient stream. Failed to read a byte.")
            return ret.toByte()

        } finally {
            stream_position++
        }
    }

    private fun readInt16 () : Short
    {
        return ((readByte ().toUnsigned() shl 8) + readByte ().toUnsigned()).toShort()
    }

    private fun readInt32 () : Int
    {
        return (((readByte ().toUnsigned() shl 8) + readByte ().toUnsigned() shl 8) + readByte ().toUnsigned() shl 8) + readByte ().toUnsigned()
    }

    private fun parseError (msg : String) : Exception
    {
        return parseError (msg, null)
    }

    private fun parseError (msg : String, innerException : Exception? ) : Exception
    {
        if (innerException == null)
            throw SmfParserException (String.format ("$msg(at %s)", stream_position))
        else
            throw SmfParserException (String.format ("$msg(at %s)", stream_position), innerException)
    }
}

class SmfParserException : Exception
{
    constructor () : this ("SMF parser error")
    constructor (message : String) : super (message)
    constructor (message : String, innerException : Exception) : super (message, innerException)
}

class SmfTrackMerger(private var source: MidiMusic) {
    companion object {

        fun merge (source : MidiMusic) : MidiMusic
        {
            return SmfTrackMerger (source).getMergedMessages ()
        }
    }

    // FIXME: it should rather be implemented to iterate all
    // tracks with index to messages, pick the track which contains
    // the nearest event and push the events into the merged queue.
    // It's simpler, and costs less by removing sort operation
    // over thousands of events.
    private fun getMergedMessages () : MidiMusic {
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

        val idxl = ArrayList<Int>(l.size)
        idxl.add(0)
        var prev = 0
        var i = 0
        while (i < l.size) {
            if (l[i].deltaTime != prev) {
                idxl.add(i)
                prev = l[i].deltaTime
            }
            i++
        }
        idxl.sortWith(Comparator{ i1, i2 -> l [i1].deltaTime-l [i2].deltaTime})

        // now build a new event list based on the sorted blocks.
        val l2 = ArrayList<MidiMessage>(l.size)
        var idx: Int
        i = 0
        while (i < idxl.size) {
            idx = idxl[i]
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
    private var tracks = HashMap<Int,SplitTrack> ()

    internal class SplitTrack(var trackID: Int) {

        var totalDeltaTime : Int
        var track : MidiTrack = MidiTrack ()

        fun addMessage (deltaInsertAt : Int, e : MidiMessage)
        {
            val e2 = MidiMessage (deltaInsertAt - totalDeltaTime, e.event)
            track.messages.add (e2)
            totalDeltaTime = deltaInsertAt
        }

        init {
            totalDeltaTime = 0
        }
    }

    private fun getTrack (track : Int) : SplitTrack
    {
        var t = tracks [track]
        if (t == null) {
            t = SplitTrack (track)
            tracks [track] = t
        }
        return t
    }

    // Override it to customize track dispatcher. It would be
    // useful to split note messages out from non-note ones,
    // to ease data reading.
    private fun getTrackID (e : MidiMessage) : Int
    {
        return when (e.event.eventType) {
            MidiEventType.META, MidiEventType.SYSEX, MidiEventType.SYSEX_END -> -1
            else -> e.event.channel.toUnsigned()
        }
    }

    private fun split () : MidiMusic
    {
        var totalDeltaTime = 0
        for (e in source) {
            totalDeltaTime += e.deltaTime
            val id: Int = getTrackID(e)
            getTrack(id).addMessage(totalDeltaTime, e)
        }

        val m = MidiMusic ()
        m.deltaTimeSpec = delta_time_spec
        for (t in tracks.values)
            m.tracks.add (t.track)
        return m
    }

    init {
        val mtr = SplitTrack (-1)
        tracks[-1] = mtr
    }
}
