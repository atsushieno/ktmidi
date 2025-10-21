package dev.atsushieno.ktmidi

class Midi1SysExChunkProcessor {
    val remaining = mutableListOf<Byte>()
    private fun isStatusByte(byte: Byte): Boolean = (byte.toInt() and 0x80) != 0

    private fun isRealTimeMessage(byte: Byte): Boolean {
        val value = byte.toInt() and 0xFF
        return value >= 0xF8
    }

    private fun findSysExTermination(input: List<Byte>, startPos: Int = 0): Int {
        for (i in startPos until input.size) {
            val byte = input[i]
            val value = byte.toInt() and 0xFF

            if (value == 0xF7)
                return i

            if (isStatusByte(byte) && !isRealTimeMessage(byte) && value != 0xF0)
                return i - 1
        }
        return -1
    }

    // Returns a sequence of "event list" that are "complete" i.e. not pending.
    // Any incomplete sysex buffer is stored in `remaining`.
    // If there is no buffer and the input is an incomplete SysEx buffer, then only an empty sequence is returned.
    fun process(input: List<Byte>): Sequence<List<Byte>> = sequence {
        if (remaining.isNotEmpty()) {
            val terminationPos = findSysExTermination(input)
            if (terminationPos < 0) {
                remaining.addAll(input)
            } else {
                val endByte = input[terminationPos]
                val isEOX = (endByte.toInt() and 0xFF) == 0xF7

                if (isEOX) {
                    yield(remaining + input.take(terminationPos + 1))
                    remaining.clear()
                    yieldAll(process(input.drop(terminationPos + 1)))
                } else {
                    yield(remaining + input.take(terminationPos + 1))
                    remaining.clear()
                    yieldAll(process(input.drop(terminationPos + 1)))
                }
            }
        } else {
            // If sysex is found then check if it is incomplete.
            // F0 must occur only as the beginning of SysEx, so simply check it by indexOf().
            val f0Pos = input.indexOf(0xF0.toByte())
            if (f0Pos < 0) {
                if (input.isNotEmpty())
                    yield(input)
            } else {
                if (f0Pos > 0)
                    yield(input.take(f0Pos))
                val terminationPos = findSysExTermination(input, f0Pos + 1)
                if (terminationPos < 0) {
                    remaining.addAll(input.drop(f0Pos))
                } else {
                    val endByte = input[terminationPos]
                    val isEOX = (endByte.toInt() and 0xFF) == 0xF7

                    if (isEOX) {
                        yield(input.subList(f0Pos, terminationPos + 1))
                        yieldAll(process(input.drop(terminationPos + 1)))
                    } else {
                        yield(input.subList(f0Pos, terminationPos + 1))
                        yieldAll(process(input.drop(terminationPos + 1)))
                    }
                }
            }
        }
    }
}
