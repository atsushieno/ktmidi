package dev.atsushieno.ktmidi

// Data Format:
//   identifier: 0xAAAAAAAAAAAAAAAA (16 bytes)
//   i32 deltaTimeSpec
//   i32 numTracks
//   tracks
//        identifier: 0xEEEEEEEEEEEEEEEE (16 bytes)
//       i32 numUMPs
//       umps (i32, i64 or i128)

fun Midi2Music.write(stream: MutableList<Byte>) {
    val w = Midi2MusicWriter(stream)
    val ints = w.serializeMidi2MusicToInts(this)
    val bytes = ints.flatMap { i32 -> sequence {
        yield((i32 shr 24).toByte())
        yield(((i32 shr 16) and 0xFF).toByte())
        yield(((i32 shr 8) and 0xFF).toByte())
        yield((i32 and 0xFF).toByte())
    }.asIterable() }
    stream.addAll(bytes)
}

internal class Midi2MusicWriter(val stream: MutableList<Byte>) {
    internal fun serializeMidi2MusicToInts(music: Midi2Music) : List<Int> {
        val ret = mutableListOf<Int>()
        (0..3).forEach { _ -> ret.add(0xAAAAAAAA.toInt()) }
        ret.add(music.deltaTimeSpec)
        ret.add(music.tracks.size)
        for(track in music.tracks) {
            (0..3).forEach { _ -> ret.add(0xEEEEEEEE.toInt()) }
            ret.add(track.messages.size)
            for (message in track.messages)
                when (message.messageType) {
                    5 -> ret.addAll(sequenceOf(message.int1, message.int2, message.int3, message.int4))
                    3, 4 -> ret.addAll(sequenceOf(message.int1, message.int2))
                    else -> ret.add(message.int1)
                }
        }
        return ret
    }
}

fun Midi2Music.read(stream: List<Byte>) {
    val r = Midi2MusicReader(this, stream)
    r.readMusic()
}

internal class Midi2MusicReader(val music: Midi2Music, stream: List<Byte>) {

    private val reader: Reader = Reader(stream, 0)

    private fun expectIdentifier16(e: Byte) {
        for (i in 0..15)
            if (!reader.canRead())
                throw IllegalArgumentException("Insufficient stream at music file identifier (at $i:  ${reader.position})")
            else if (reader.readByte() != e)
                throw IllegalArgumentException("Unexpected stream content at music file identifier (at $i: ${reader.position - 1})")
    }

    private fun readInt32() : Int {
        var ret: Int = 0
        for (i in 0..3) {
            if (!reader.canRead())
                throw IllegalArgumentException("Insufficient stream at music file identifier (at $i:  ${reader.position}")
            ret += (reader.readByte().toUnsigned() shl (8 * i))
        }
        return ret
    }

    internal fun readMusic() {
        expectIdentifier16(0xAA.toByte())
        music.deltaTimeSpec = readInt32()
        val numTracks = readInt32()
        for(t in 0 until numTracks)
            music.addTrack(readTrack())
    }

    private fun readTrack() : Midi2Track {
        val ret = Midi2Track()
        expectIdentifier16(0xEE.toByte())
        val numUMPs = readInt32()
        for(t in 0 until numUMPs)
            ret.messages.add(readUmp())
        return ret
    }

    private fun readUmp() : Ump {
        val int1 = readInt32()
        return when (int1 shr 28) {
            5 -> Ump(int1, readInt32(), readInt32(), readInt32())
            3, 4 -> Ump(int1, readInt32())
            else -> Ump(int1)
        }
    }
}
