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
        ret.add(0x41414141) // AAAA
        ret.add(0x41414141) // AAAA
        ret.add(0x45454545) // EEEE
        ret.add(0x45454545) // EEEE
        ret.add(music.deltaTimeSpec)
        ret.add(music.tracks.size)

        for (track in music.tracks) {
            // "SMF2CLIP"
            ret.add(0x534d4632)
            ret.add(0x434c4950)
            // DCTPQ, DCS(0) prepended
            ret.add(UmpFactory.deltaClockstamp(0))
            ret.add(UmpFactory.dctpq(music.deltaTimeSpec.toUShort()))
            // Start of Clip, DCS(0) prepended
            ret.add(UmpFactory.deltaClockstamp(0))
            ret.addAll(UmpFactory.startOfClip().toInts())

            // contents
            var dcPrepended = false
            for (message in track.messages) {
                if (message.isStartOfClip || message.isDCTPQ)
                    continue // We already emitted it, skipping anything invalid
                if (message.isEndOfClip)
                    continue // we will emit it at the end, skipping anything invalid
                if (message.isDeltaClockstamp) {
                    dcPrepended = true
                    ret.add(message.int1)
                    continue
                }
                if (!dcPrepended)
                    ret.add(0x00400000) // DCS(0)
                dcPrepended = false
                when (message.messageType) {
                    5, 0xD, 0xF -> ret.addAll(sequenceOf(message.int1, message.int2, message.int3, message.int4))
                    3, 4 -> ret.addAll(sequenceOf(message.int1, message.int2))
                    else -> ret.add(message.int1)
                }
            }

            ret.add(UmpFactory.deltaClockstamp(0))
            ret.addAll(UmpFactory.endOfClip().toInts())
        }

        return ret
    }
}

@Deprecated("Use read() overload with removeEmptyDeltaClockstamps argument instead", ReplaceWith("read(stream, true)"))
fun Midi2Music.read(stream: List<Byte>) = read(stream, true)

fun Midi2Music.read(stream: List<Byte>, removeEmptyDeltaClockstamps: Boolean = true) {
    val r = Midi2MusicReader(this, stream)
    r.readMusic(removeEmptyDeltaClockstamps)
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
        return when ((int1.toUInt()) shr 28) {
            5u, 0xDu, 0xFu -> Ump(int1, readInt32(), readInt32(), readInt32())
            3u, 4u -> Ump(int1, readInt32())
            else -> Ump(int1)
        }
    }
}

internal class Midi2MusicReader(val music: Midi2Music, stream: List<Byte>) {

    // We read them in BIG endian regardless of the native platform.
    private val reader = UmpStreamReader(Reader(stream, 0), ByteOrder.BIG_ENDIAN)

    private fun expectASCII(value: String) {
        for (i in value.indices)
            if (!reader.reader.canRead())
                throw IllegalArgumentException("Insufficient stream at music file identifier (at $i:  ${reader.reader.position})")
            else if (reader.reader.readByte().toInt() != value[i].code)
                throw IllegalArgumentException("Unexpected stream content at music file identifier (at $i: ${reader.reader.position - 1}): expected '$value'")
    }

    private fun expectIdentifier16(e: Byte) {
        for (i in 0..15)
            if (!reader.reader.canRead())
                throw IllegalArgumentException("Insufficient stream at music file identifier (at $i:  ${reader.reader.position})")
            else if (reader.reader.readByte() != e)
                throw IllegalArgumentException("Unexpected stream content at music file identifier (at $i: ${reader.reader.position - 1})")
    }

    internal fun readMusic(removeEmptyDeltaClockstamps: Boolean) {
        expectASCII("AAAAAAAA")
        expectASCII("EEEEEEEE")
        music.deltaTimeSpec = reader.readInt32()
        val numTracks = reader.readInt32()
        for (t in 0 until numTracks)
            music.addTrack(readTrack(removeEmptyDeltaClockstamps))
    }

    private fun readTrack(removeEmptyDeltaClockstamps: Boolean): Midi2Track {
        val ret = Midi2Track()
        expectASCII("SMF2CLIP")
        while (true) {
            val ump = reader.readUmp()
            if (removeEmptyDeltaClockstamps && ump.isDeltaClockstamp && ump.deltaClockstamp == 0)
                continue // skip 0 delta time. We will add them when writing to stream anyways.
            ret.messages.add(ump)
            if (ump.isEndOfClip)
                break
        }
        return ret
    }
}
