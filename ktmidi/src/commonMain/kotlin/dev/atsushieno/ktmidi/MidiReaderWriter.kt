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
        stream.add('M'.code.toByte())
        stream.add('T'.code.toByte())
        stream.add('h'.code.toByte())
        stream.add('d'.code.toByte())
        writeShort(0)
        writeShort(6)
        writeShort(format)
        writeShort(tracks)
        writeShort(deltaTimeSpec)
    }

    var metaEventWriter: (Boolean, MidiMessage, MutableList<Byte>) -> Int =
        SmfWriterExtension.DEFAULT_META_EVENT_WRITER

    fun writeTrack(track: MidiTrack) {
        stream.add('M'.code.toByte())
        stream.add('T'.code.toByte())
        stream.add('r'.code.toByte())
        stream.add('k'.code.toByte())
        writeInt(getTrackDataSize(track))

        var running_status: Byte = 0

        for (e in track.messages) {
            write7bitEncodedInt(e.deltaTime)
            when (e.event.eventType.toUnsigned()) {
                MidiMusic.META_EVENT -> metaEventWriter(false, e, stream)
                MidiMusic.SYSEX_EVENT -> {
                    stream.add(e.event.eventType)
                    val seBytes = e.event.extraData
                    if (seBytes != null) {
                        if (e.event.extraDataLength > 0)
                            stream.addAll(seBytes.drop(e.event.extraDataOffset).take(e.event.extraDataLength))
                    }
                    // supply SYSEX_END only if missing.
                    if (seBytes == null || seBytes[e.event.extraDataOffset + e.event.extraDataLength - 1] != MidiMusic.SYSEX_END.toByte())
                        stream.add(MidiMusic.SYSEX_END.toByte())
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

    private fun get7BitEncodedLength(value: Int): Int {
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
            size += get7BitEncodedLength(e.deltaTime)

            // arguments
            when (e.event.eventType.toUnsigned()) {
                MidiMusic.META_EVENT -> size += metaEventWriter(true, e, mutableListOf())
                MidiMusic.SYSEX_EVENT -> {
                    size++
                    if (e.event.extraData != null) {
                        size += e.event.extraDataLength
                        if (e.event.extraData[e.event.extraDataOffset + e.event.extraDataLength - 1] != 0xF7.toByte())
                            size++ // supply SYSEX_END
                    }
                    else
                        size++ // SYSEX_END
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

    private fun write7bitEncodedInt(value: Int) {
        write7bitEncodedInt(value, false)
    }

    private fun write7bitEncodedInt(value: Int, shifted: Boolean) {
        if (value == 0) {
            stream.add((if (shifted) 0x80 else 0).toByte())
            return
        }
        if (value >= 0x80)
            write7bitEncodedInt(value shr 7, true)
        stream.add(((value and 0x7F) + if (shifted) 0x80 else 0).toByte())
    }

}

class SmfWriterExtension {
    companion object {

        val DEFAULT_META_EVENT_WRITER: (Boolean, MidiMessage, MutableList<Byte>) -> Int =
            { b, m, o -> defaultMetaWriterFunc(b, m, o) }

        private fun defaultMetaWriterFunc(onlyCountLength: Boolean, e: MidiMessage, stream: MutableList<Byte>): Int {
            if (onlyCountLength) {
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
                stream.addAll("DM:${idx.toString(16)}:".map { c -> c.code.toByte() }.toTypedArray())
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
        val len = min(stream.size - index, endOffsetInclusive - startOffset)
        if (len > 0) {
            stream.subList(index, index + len).toByteArray().copyInto(dst, startOffset, 0, len)
            index += len
        }
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
        if (readByte() != 'M'.code.toByte()
            || readByte() != 'T'.code.toByte()
            || readByte() != 'h'.code.toByte()
            || readByte() != 'd'.code.toByte()
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
            readByte() != 'M'.code.toByte()
            || readByte() != 'T'.code.toByte()
            || readByte() != 'r'.code.toByte()
            || readByte() != 'k'.code.toByte()
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
            MidiMusic.SYSEX_EVENT -> {
                val args = mutableListOf<Byte>()
                while (true) {
                    val ch = readByte()
                    if (ch == MidiMusic.SYSEX_END.toByte())
                        break
                    else
                        args.add(ch)
                }
                return MidiMessage(deltaTime, MidiEvent(runningStatus, 0, 0, args.toByteArray(), 0, args.size))
            }
            MidiMusic.META_EVENT -> {
                val metaType = readByte()
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
        val len = reader.read(args, start, start + args.size)
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

