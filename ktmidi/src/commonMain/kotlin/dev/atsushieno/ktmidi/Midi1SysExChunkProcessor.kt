package dev.atsushieno.ktmidi

class Midi1SysExChunkProcessor {
    val remaining = mutableListOf<Byte>()
    // Returns a sequence of "event list" that are "complete" i.e. not pending.
    // Any incomplete sysex buffer is stored in `remaining`.
    // If there is no buffer and the input is an incomplete SysEx buffer, then only an empty sequence is returned.
    fun process(input: List<Byte>): Sequence<List<Byte>> = sequence {
        if (remaining.isNotEmpty()) {
            val f7Pos = input.indexOf(0xF7.toByte())
            if (f7Pos < 0)
                remaining.addAll(input)
            else {
                yield(remaining + input.take(f7Pos + 1))
                // process the remaining recursively
                yieldAll(process(input.drop(f7Pos + 1)))
            }
        } else {
            // If sysex is found then check if it is incomplete.
            // F0 must occur only as the beginning of SysEx, so simply check it by indexOf().
            val f0Pos = input.indexOf(0xF0.toByte())
            if (f0Pos < 0)
                yield(input)
            else {
                yield(input.take(f0Pos))
                val f7Pos = input.indexOf(0xF7.toByte())
                if (f7Pos < 0)
                    remaining.addAll(input)
                else {
                    yield(input.take(f7Pos + 1))
                    // process the remaining recursively
                    yieldAll(process(input.drop(f7Pos + 1)))
                }
            }
        }
    }
}
