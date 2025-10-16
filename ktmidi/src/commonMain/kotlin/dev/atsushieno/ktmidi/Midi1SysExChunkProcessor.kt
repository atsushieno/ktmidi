package dev.atsushieno.ktmidi

/**
 * Processor class meant to handle large System Exclusive (SysEx) messages.
 */
interface Midi1SysExChunkProcessor {
    /**
     * Process incoming raw data and check for SysEyx Start/End bytes.
     * To handle large SysEx messages correctly, data needs to be stored in a buffer until
     * the SysEx End Byte is received.
     * @param input The received midi data that should be processed
     * @return chunks of midi data, preferably (but not necessarily) each containing one or more fully valid midi messages
     */
    fun process(input: ByteArray): Sequence<ByteArray>
}

/**
 * Default implementation of [Midi1SysExChunkProcessor].
 * Ensures correct handling of large SysEx files according to Midi Specs
 * This includes handling foreign Status Bytes during an ongoing SysEx transfer.
 * In this case, the SysEx transfer is terminated and the incomplete SysEx messages is
 * forwarded so a consuming app might process the partial data.
 */
class DefaultMidi1SysExChunkProcessor: Midi1SysExChunkProcessor {
    // For incomplete SysEx data in between invocations
    private var remaining = ByteArray(0)

    /**
     * Processes incoming MIDI bytes and yields complete messages (as ByteArrays).
     * Incomplete SysEx messages are buffered internally.
     */
    override fun process(input: ByteArray): Sequence<ByteArray> = sequence {
        var buffer: ByteArray
        var bufferPos = 0

        // Start with remaining data (if any) in buffer
        if (remaining.isNotEmpty()) {
            buffer = ByteArray(remaining.size + input.size)
            remaining.copyInto(buffer)
            input.copyInto(buffer, remaining.size)
            bufferPos = remaining.size + input.size
            remaining = ByteArray(0)
        } else {
            buffer = input
            bufferPos = input.size
        }

        var pos = 0
        var msgStart = 0
        var inSysEx = false

        while (pos < bufferPos) {
            val b = buffer[pos]

            when {
                // SysEx Start
                b == 0xF0.toByte() -> {
                    // Yield all bytes before SysEx (e.g. normal channel messages)
                    if (!inSysEx && pos > msgStart) {
                        yield(buffer.copyOfRange(msgStart, pos))
                    }
                    msgStart = pos
                    inSysEx = true
                }

                // SysEx End
                b == 0xF7.toByte() && inSysEx -> {
                    yield(buffer.copyOfRange(msgStart, pos + 1))
                    msgStart = pos + 1
                    inSysEx = false
                }

                // Status byte outside of SysEx (>= 0x80)
                (b.toInt() and 0x80) != 0 && !inSysEx && pos > msgStart -> {
                    yield(buffer.copyOfRange(msgStart, pos))
                    msgStart = pos
                }
            }
            pos++
        }

        // If there is an ongoing (incomplete) SysEx message, save it in 'remaining'
        if (inSysEx) {
            remaining = buffer.copyOfRange(msgStart, bufferPos)
        } else if (msgStart < bufferPos) {
            // Default path, no SysEx
            yield(buffer.copyOfRange(msgStart, bufferPos))
        }
    }
}