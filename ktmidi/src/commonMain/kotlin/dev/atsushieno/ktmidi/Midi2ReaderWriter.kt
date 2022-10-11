package dev.atsushieno.ktmidi

import io.ktor.utils.io.core.*

// See docs/MIDI2_FORMATS.md for the normative format reference.

fun Midi2Music.write(stream: MutableList<Byte>) {
    val w = Midi2MusicWriter(stream)
    val ints = w.serializeMidi2MusicToInts(this)
    val bytes = ints.flatMap { i32 ->
        sequence {
            // We store them in BIG endian. We need consistent endianness across platforms.
            yield((i32 shr 24).toByte())
            yield(((i32 shr 16) and 0xFF).toByte())
            yield(((i32 shr 8) and 0xFF).toByte())
            yield((i32 and 0xFF).toByte())
        }.asIterable()
    }
    stream.addAll(bytes)
}

internal class Midi2MusicWriter(val stream: MutableList<Byte>) {
    internal fun serializeMidi2MusicToInts(music: Midi2Music): List<Int> {
        val ret = mutableListOf<Int>()
        (0..3).forEach { _ -> ret.add(0xAAAAAAAA.toInt()) }
        ret.add(music.deltaTimeSpec)
        ret.add(music.tracks.size)
        for (track in music.tracks) {
            (0..3).forEach { _ -> ret.add(0xEEEEEEEE.toInt()) }
            ret.add(0) // stub for `size` field, to be replaced later
            val trackStartOffset = ret.size
            for (message in track.messages) {
                when (message.messageType) {
                    5 -> ret.addAll(sequenceOf(message.int1, message.int2, message.int3, message.int4))
                    3, 4 -> ret.addAll(sequenceOf(message.int1, message.int2))
                    else -> ret.add(message.int1)
                }
            }
            ret[trackStartOffset - 1] = (ret.size - trackStartOffset) * 4 // sizeof(Int)
        }
        return ret
    }
}

fun Midi2Music.read(stream: List<Byte>) {
    val r = Midi2MusicReader(this, stream)
    r.readMusic()
}

internal class UmpStreamReader(val reader: Reader, val byteOrder: ByteOrder) {
    fun readInt32(): Int {
        var ret: Int = 0
        for (i in 0..3) {
            if (!reader.canRead())
                throw IllegalArgumentException("Insufficient stream at music file identifier (at $i:  ${reader.position}")
            ret += (reader.readByte().toUnsigned() shl (8 * (if (byteOrder == ByteOrder.BIG_ENDIAN) 3 - i else i)))
        }
        return ret
    }

    fun readUmp(): Ump {
        val int1 = readInt32()
        return when (int1 shr 28) {
            5 -> Ump(int1, readInt32(), readInt32(), readInt32())
            3, 4 -> Ump(int1, readInt32())
            else -> Ump(int1)
        }
    }
}

internal class Midi2MusicReader(val music: Midi2Music, stream: List<Byte>) {

    // We read them in BIG endian regardless of the native platform.
    private val reader = UmpStreamReader(Reader(stream, 0), ByteOrder.BIG_ENDIAN)

    private fun expectIdentifier16(e: Byte) {
        for (i in 0..15)
            if (!reader.reader.canRead())
                throw IllegalArgumentException("Insufficient stream at music file identifier (at $i:  ${reader.reader.position})")
            else if (reader.reader.readByte() != e)
                throw IllegalArgumentException("Unexpected stream content at music file identifier (at $i: ${reader.reader.position - 1})")
    }

    internal fun readMusic() {
        expectIdentifier16(0xAA.toByte())
        music.deltaTimeSpec = reader.readInt32()
        val numTracks = reader.readInt32()
        for (t in 0 until numTracks)
            music.addTrack(readTrack())
    }

    private fun readTrack(): Midi2Track {
        val ret = Midi2Track()
        expectIdentifier16(0xEE.toByte())
        val numBytes = reader.readInt32()
        var readSize = 0
        while (readSize < numBytes) {
            val ump = reader.readUmp()
            readSize += ump.sizeInBytes
            ret.messages.add(ump)
        }
        return ret
    }
}
