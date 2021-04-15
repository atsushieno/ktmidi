package dev.atsushieno.ktmidi

import kotlin.math.min

class SmfWriter(val stream: MutableList<Byte>) {

    var disableRunningStatus: Boolean = false

    private fun writeShort(v: Short) {
        stream.add((v / 0x100).toByte())
        stream.add((v % 0x100).toByte())
    }

    private fun writeInt(v: Int) {
        stream.add((v / 0x1000000).toByte())
        stream.add((v / 0x10000 and 0xFF).toByte())
        stream.add((v / 0x100 and 0xFF).toByte())
        stream.add((v % 0x100).toByte())
    }

    fun writeMusic(music: MidiMusic) {
        writeHeader(music.format.toShort(), music.tracks.size.toShort(), music.deltaTimeSpec.toShort())
        for (track in music.tracks)
            writeTrack(track)
    }

    fun writeHeader(format: Short, tracks: Short, deltaTimeSpec: Short) {
        stream.add('M'.toByte())
        stream.add('T'.toByte())
        stream.add('h'.toByte())
        stream.add('d'.toByte())
        writeShort(0)
        writeShort(6)
        writeShort(format)
        writeShort(tracks)
        writeShort(deltaTimeSpec)
    }

    var metaEventWriter: (Boolean, MidiMessage, MutableList<Byte>) -> Int =
        SmfWriterExtension.DEFAULT_META_EVENT_WRITER

    fun writeTrack(track: MidiTrack) {
        stream.add('M'.toByte())
        stream.add('T'.toByte())
        stream.add('r'.toByte())
        stream.add('k'.toByte())
        writeInt(getTrackDataSize(track))

        var running_status: Byte = 0

        for (e in track.messages) {
            write7BitVariableInteger(e.deltaTime)
            when (e.event.eventType) {
                MidiEventType.META -> metaEventWriter(false, e, stream)
                MidiEventType.SYSEX, MidiEventType.SYSEX_END -> {
                    stream.add(e.event.eventType)
                    if (e.event.extraData != null) {
                        write7BitVariableInteger(e.event.extraDataLength)
                        if (e.event.extraDataLength > 0)
                            stream.addAll(e.event.extraData.slice(
                                IntRange(e.event.extraDataOffset, e.event.extraDataOffset + e.event.extraDataLength - 1)))
                    }
                }
                else -> {
                    if (disableRunningStatus || e.event.statusByte != running_status)
                        stream.add(e.event.statusByte)
                    val len = MidiEvent.fixedDataSize(e.event.eventType)
                    stream.add(e.event.msb)
                    if (len > 1)
                        stream.add(e.event.lsb)
                    if (len > 2)
                        throw Exception("Unexpected data size: $len")
                }
            }
            running_status = e.event.statusByte
        }
    }

    private fun getVariantLength(value: Int): Int {
        if (value < 0)
            throw IllegalArgumentException("Length must be non-negative integer: $value")
        if (value == 0)
            return 1
        var ret = 0
        var x: Int = value
        while (x != 0) {
            ret++
            x = x shr 7
        }
        return ret
    }

    private fun getTrackDataSize(track: MidiTrack): Int {
        var size = 0
        var runningStatus: Byte = 0
        for (e in track.messages) {
            // delta time
            size += getVariantLength(e.deltaTime)

            // arguments
            when (e.event.eventType) {
                MidiEventType.META -> size += metaEventWriter(true, e, mutableListOf())
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

    private fun write7BitVariableInteger(value: Int) {
        write7BitVariableInteger(value, false)
    }

    private fun write7BitVariableInteger(value: Int, shifted: Boolean) {
        if (value == 0) {
            stream.add((if (shifted) 0x80 else 0).toByte())
            return
        }
        if (value >= 0x80)
            write7BitVariableInteger(value shr 7, true)
        stream.add(((value and 0x7F) + if (shifted) 0x80 else 0).toByte())
    }

}

class SmfWriterExtension {
    companion object {

        val DEFAULT_META_EVENT_WRITER: (Boolean, MidiMessage, MutableList<Byte>) -> Int =
            { b, m, o -> defaultMetaWriterFunc(b, m, o) }

        private fun defaultMetaWriterFunc(lengthMode: Boolean, e: MidiMessage, stream: MutableList<Byte>): Int {
            if (lengthMode) {
                // [0x00] 0xFF metaType size ... (note that for more than one meta event it requires step count of 0).
                val repeatCount: Int = e.event.extraDataLength / 0x7F
                if (repeatCount == 0)
                    return 3 + e.event.extraDataLength
                val mod: Int = e.event.extraDataLength % 0x7F
                return repeatCount * (4 + 0x7F) - 1 + if (mod > 0) 4 + mod else 0
            }

            if (e.event.extraData == null)
                return 0

            var written = 0
            val total: Int = e.event.extraDataLength
            var passed = false // manually rewritten do-while loop...
            while (!passed || written < total) {
                passed = true
                if (written > 0)
                    stream.add(0.toByte()) // step
                stream.add(0xFF.toByte())
                stream.add(e.event.metaType)
                val size = min(0x7F, total - written)
                stream.add(size.toByte())
                val offset = e.event.extraDataOffset + written
                if (size > 0)
                    stream.addAll(e.event.extraData.slice(IntRange(offset, offset + size - 1)))
                written += size
            }
            return 0
        }

        val vsqMetaTextSplitter: (Boolean, MidiMessage, MutableList<Byte>) -> Int =
            { b, m, o -> vsqMetaTextSplitterFunc(b, m, o) }

        private fun vsqMetaTextSplitterFunc(lengthMode: Boolean, e: MidiMessage, stream: MutableList<Byte>): Int {
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
                return repeatCount * (12 + 0x77) - 1 + if (mod > 0) 12 + mod else 0
            }

            var written = 0
            val total: Int = e.event.extraDataLength
            val idx = 0
            do {
                if (written > 0)
                    stream.add(0.toByte()) // step
                stream.add(0xFF.toByte())
                stream.add(e.event.metaType)
                val size = min(0x77, total - written)
                stream.add((size + 8).toByte())
                stream.addAll("DM:${idx.toString(16)}:".map { c -> c.toByte() }.toTypedArray())
                val offset = e.event.extraDataOffset + written
                if (size > 0)
                    stream.addAll(e.event.extraData.slice(IntRange(offset, offset + size - 1)))
                written += size
            } while (written < total)
            return 0
        }
    }
}

fun MidiMusic.read(stream: List<Byte>) {
    val r = SmfReader(this, stream)
    r.readMusic()
}

internal class Reader(private val stream: List<Byte>, private var index: Int) {
    fun canRead() : Boolean = index < stream.size
    fun read(dst: ByteArray, startOffset: Int, endOffsetInclusive: Int) : Int {
        val len = min(stream.size - index, endOffsetInclusive - 1 - startOffset)
        if (len > 0)
            stream.slice(IntRange(index, index + len - 1).apply { index += len})
                .toByteArray().copyInto(dst, startOffset, endOffsetInclusive)
        return len
    }
    val position = index
    fun peekByte() : Byte = stream[index]
    fun readByte() : Byte = stream[index++]
}

internal class SmfReader(val music: MidiMusic, stream: List<Byte>) {

    private val reader: Reader = Reader(stream, 0)

    private val data = music

    fun readMusic() {
        if (readByte() != 'M'.toByte()
            || readByte() != 'T'.toByte()
            || readByte() != 'h'.toByte()
            || readByte() != 'd'.toByte()
        )
            throw parseError("MThd is expected")
        if (readInt32() != 6)
            throw parseError("Unexpected data size (should be 6)")
        data.format = readInt16().toByte()
        val tracks = readInt16()
        data.deltaTimeSpec = readInt16().toInt()
        for (i in 0 until tracks)
            data.tracks.add(readTrack())
    }

    private fun readTrack(): MidiTrack {
        val tr = MidiTrack()
        if (
            readByte() != 'M'.toByte()
            || readByte() != 'T'.toByte()
            || readByte() != 'r'.toByte()
            || readByte() != 'k'.toByte()
        )
            throw parseError("MTrk is expected")
        val trackSize = readInt32()
        currentTrackSize = 0
        var total = 0
        while (currentTrackSize < trackSize) {
            val delta = readVariableLength()
            tr.messages.add(readMessage(delta))
            total += delta
        }
        if (currentTrackSize != trackSize)
            throw parseError("Size information mismatch")
        return tr
    }

    private var currentTrackSize: Int = 0
    private var runningStatus: Int = 0

    private fun readMessage(deltaTime: Int): MidiMessage {
        val b = peekByte().toUnsigned()
        runningStatus = if (b < 0x80) runningStatus else readByte().toUnsigned()
        val len: Int
        when (runningStatus) {
            MidiEventType.SYSEX.toUnsigned(), MidiEventType.SYSEX_END.toUnsigned(), MidiEventType.META.toUnsigned() -> {
                val metaType = if (runningStatus == MidiEventType.META.toUnsigned()) readByte() else 0
                len = readVariableLength()
                val args = ByteArray(len)
                if (len > 0)
                    readBytes(args)
                return MidiMessage(deltaTime, MidiEvent(runningStatus, metaType.toUnsigned(), 0, args, 0, len))
            }
            else -> {
                var value = runningStatus
                value += readByte().toUnsigned() shl 8
                if (MidiEvent.fixedDataSize(runningStatus.toByte()) == 2.toByte())
                    value += readByte().toUnsigned() shl 16
                return MidiMessage(deltaTime, MidiEvent(value))
            }
        }
    }

    private fun readBytes(args: ByteArray) {
        currentTrackSize += args.size
        var start = 0
        val len = reader.read(args, start, start + args.size - 1)
        if (len < args.size - start)
            throw parseError("The stream is insufficient to read ${args.size} bytes specified in the SMF message. Only $len bytes read.")
    }

    private fun readVariableLength(): Int {
        var v = 0
        var i = 0
        while (i < 4) {
            val b = readByte().toUnsigned()
            v = (v shl 7) + b
            if (b < 0x80)
                return v
            v -= 0x80
            i++
        }
        throw parseError("Delta time specification exceeds the 4-byte limitation.")
    }

    private fun peekByte(): Byte {
        if (!reader.canRead())
            throw parseError("Insufficient stream. Failed to read a byte.")
        return reader.peekByte()
    }

    private fun readByte(): Byte {
        currentTrackSize++
        if (!reader.canRead())
            throw parseError("Insufficient stream. Failed to read a byte.")
        return reader.readByte()
    }

    private fun readInt16(): Short {
        return ((readByte().toUnsigned() shl 8) + readByte().toUnsigned()).toShort()
    }

    private fun readInt32(): Int {
        return (((readByte().toUnsigned() shl 8) + readByte().toUnsigned() shl 8) + readByte().toUnsigned() shl 8) + readByte().toUnsigned()
    }

    private fun parseError(msg: String): Exception {
        return parseError(msg, null)
    }

    private fun parseError(msg: String, innerException: Exception?): Exception {
        if (innerException == null)
            throw SmfParserException("$msg (at ${reader.position})")
        else
            throw SmfParserException("$msg (at ${reader.position})", innerException)
    }
}

class SmfParserException : Exception {
    constructor () : this("SMF parser error")
    constructor (message: String) : super(message)
    constructor (message: String, innerException: Exception) : super(message, innerException)
}

