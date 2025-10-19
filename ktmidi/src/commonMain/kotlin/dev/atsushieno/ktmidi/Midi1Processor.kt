package dev.atsushieno.ktmidi

/**
 * Processor that converts raw byte data to Midi1Messages.
 * Implementations are [Midi1SimpleProcessor] and [Midi1SysExChunkProcessor].
 */
interface Midi1Processor {
    /**
     * Process incoming raw data and convert it to a Sequence of Midi1Messages
     * @param input The received midi data that should be processed
     * @return a sequence of the parsed Midi1Messages
     */
    fun process(input: ByteArray): Sequence<Midi1Message>
    fun process(input: List<Byte>): Sequence<Midi1Message>
}