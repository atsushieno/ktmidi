package dev.atsushieno.ktmidi

/**
 * Ensures correct handling of large SysEx files according to Midi Specs
 * This includes handling foreign Status Bytes during an ongoing SysEx transfer.
 * In this case, the SysEx transfer is terminated and the incomplete SysEx messages is
 * forwarded so a consuming app might process the partial data.
 * @param returnCancelledSysEx if true, incomplete SysEx messages will be yielded to the sequence
 * if the first received status byte after F0 is any other than F7. If false, the incomplete
 * message is discarded
 */
class Midi1SysExChunkProcessor(val returnCancelledSysEx: Boolean = false): Midi1Processor {
    // For incomplete SysEx data in between invocations
    private var remaining: ByteSource? = null

    override fun process(input: List<Byte>): Sequence<Midi1Message> =
        process(ByteSource.BytesList(input))

    override fun process(input: ByteArray): Sequence<Midi1Message> =
        process(ByteSource.Bytes(input))

    /**
     * Processes incoming MIDI bytes and yields complete messages (as ByteArrays).
     * Incomplete SysEx messages are buffered internally.
     */
    private fun process(input: ByteSource): Sequence<Midi1Message> = sequence {
        var bufferPos = 0

        // Start with remaining data (if any) in buffer
        val buffer = remaining?.append(input) ?: input
        bufferPos = buffer.size
        remaining = null

        var pos = 0
        var msgStart = 0
        var inSysEx = false

        while (pos < bufferPos) {
            val b = buffer[pos]

            when {
                // SysEx End
                b == 0xF7.toByte() && inSysEx -> {
                    yield(buffer.copyOfRange(msgStart, pos + 1))
                    msgStart = pos + 1
                    inSysEx = false
                }

                // Status byte inside SysEx
                (b.toInt() and 0x80) != 0 && inSysEx -> {
                    if (returnCancelledSysEx && pos > msgStart) {
                        yield(buffer.copyOfRange(msgStart, pos))
                    }
                    // If new SysEx, we'll start it below
                    msgStart = pos
                    inSysEx = b == 0xF0.toByte()
                }

                // SysEx Start
                b == 0xF0.toByte() -> {
                    msgStart = pos
                    inSysEx = true
                }

                // Status byte outside of SysEx
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
    }.flatMap { item -> when(item) {
        is ByteSource.Bytes -> Midi1SimpleProcessor.process(item.asByteArray())
        is ByteSource.BytesList -> Midi1SimpleProcessor.process(item.asList())
    } }
}