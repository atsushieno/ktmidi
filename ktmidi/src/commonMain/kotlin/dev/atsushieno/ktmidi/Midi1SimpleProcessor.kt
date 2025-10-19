package dev.atsushieno.ktmidi

import dev.atsushieno.ktmidi.Midi1Message.Companion.fixedDataSize

object Midi1SimpleProcessor: Midi1Processor {
    override fun process(input: ByteArray): Sequence<Midi1Message> =
        convertInternal(ByteSource.Bytes(input))

    override fun process(input: List<Byte>): Sequence<Midi1Message>  =
        convertInternal(ByteSource.BytesList(input))

    private fun convertInternal(bytes: ByteSource): Sequence<Midi1Message> = sequence {
        var i = 0
        val size = bytes.size
        val end = bytes.size
        while (i < end) {
            if (bytes[i].toUnsigned() == 0xF0) {
                yield(Midi1CompoundMessage(0xF0, 0, 0, bytes.copyOfRange(i, i + size).asByteArray()))
                i += size
            } else {
                if (end <= i + fixedDataSize(bytes[i]))
                    throw Midi1Exception("Received data was incomplete to build MIDI status message for '${bytes[i]}' status.")
                val z = fixedDataSize(bytes[i])
                yield(
                    Midi1SimpleMessage(
                        bytes[i].toUnsigned(),
                        (if (z > 0) bytes[i + 1].toUnsigned() else 0),
                        (if (z > 1) bytes[i + 2].toUnsigned() else 0),
                    )
                )
                i += z + 1
            }
        }
    }
}