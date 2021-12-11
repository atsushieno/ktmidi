package dev.atsushieno.ktmidi

import io.ktor.utils.io.core.*

// We store UMP in Big Endian this time.
data class Ump(val int1: Int, val int2: Int = 0, val int3: Int = 0, val int4: Int = 0) {
    constructor(long1: Long, long2: Long = 0) : this(
        ((long1 shr 32) and 0xFFFFFFFF).toInt(),
        (long1 and 0xFFFFFFFF).toInt(),
        ((long2 shr 32) and 0xFFFFFFFF).toInt(),
        (long2 and 0xFFFFFFFF).toInt()
    )

    override fun toString(): String {
        return when (messageType) {
            0, 1, 2 -> "[${int1.toString(16)}]"
            3, 4 -> "[${int1.toString(16)}:${int2.toString(16)}]"
            else -> "[${int1.toString(16)}:${int2.toString(16)}:${int3.toString(16)}:${int4.toString(16)}]"
        }
    }

    companion object {
        @Deprecated("This function does not respect endianness.")
        fun fromBytes(bytes: ByteArray, offset: Int, count: Int) =
            sequence {
                var off = offset
                val end = offset + count
                while (off < end) {
                    val i1 = getInt(bytes, off)
                    val ints = when (i1 shr 28) {
                        MidiMessageType.SYSEX8_MDS -> 4
                        MidiMessageType.SYSEX7, MidiMessageType.MIDI2 -> 2
                        else -> 1
                    }
                    when (ints) {
                        1 -> yield(Ump(getInt(bytes, off)))
                        2 -> yield(Ump(getInt(bytes, off), getInt(bytes, off + 4)))
                        4 -> yield(
                            Ump(
                                getInt(bytes, off),
                                getInt(bytes, off + 4),
                                getInt(bytes, off + 8),
                                getInt(bytes, off + 12)
                            )
                        )
                    }
                    off += ints * 4
                }
            }

        private fun getInt(bytes: ByteArray, offset: Int) =
            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                bytes[offset].toUnsigned() +
                        (bytes[offset + 1].toUnsigned() shl 8) +
                        (bytes[offset + 2].toUnsigned() shl 16) +
                        (bytes[offset + 3].toUnsigned() shl 24)
            } else {
                bytes[offset + 3].toUnsigned() +
                        (bytes[offset + 2].toUnsigned() shl 8) +
                        (bytes[offset + 1].toUnsigned() shl 16) +
                        (bytes[offset].toUnsigned() shl 24)
            }
    }
}