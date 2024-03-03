package dev.atsushieno.ktmidi.ci

/**
 * Manages Property Exchange body chunking (PE messages could be split into
 * multiple MIDI-CI SysEx message chunks like UMP SysEx8, but in its own manner
 * so that they could be packages within SysEx7).
 *
 * It is to manage *incoming* messages. Outgoing message chunking is not supported.
 *
 * Each of `ClientConnection` and `MidiCIDevice` have a `PropertyChunkManager`.
 */
class PropertyChunkManager {
    data class Chunk(val timestamp: Long, val sourceMUID: Int, val requestId: Byte, val header: List<Byte>, val data: MutableList<Byte>)

    private val chunks = mutableListOf<Chunk>()

    fun addPendingChunk(timestamp: Long, sourceMUID: Int, requestId: Byte, header: List<Byte>, data: List<Byte>) {
        var chunk = chunks.firstOrNull { it.sourceMUID == sourceMUID && it.requestId == requestId }
        if (chunk == null) {
            chunk = Chunk(timestamp, sourceMUID, requestId, header, mutableListOf())
            chunks.add(chunk)
        }
        chunk.data.addAll(data)
    }

    fun finishPendingChunk(sourceMUID: Int, requestId: Byte, data: List<Byte>): Pair<List<Byte>,List<Byte>> {
        val chunk = chunks.removeAt(chunks.indexOfFirst { it.sourceMUID == sourceMUID && it.requestId == requestId })
        chunk.data.addAll(data)
        return Pair(chunk.header, chunk.data)
    }
}