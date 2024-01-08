package dev.atsushieno.ktmidi.ci

class PropertyChunkManager {
    data class Chunk(val timestamp: Long, val requestId: Byte, val header: List<Byte>, val data: MutableList<Byte>)

    private val chunks = mutableListOf<Chunk>()

    fun addPendingChunk(timestamp: Long, requestId: Byte, header: List<Byte>, data: List<Byte>) {
        var chunk = chunks.firstOrNull { it.requestId == requestId }
        if (chunk == null) {
            chunk = Chunk(timestamp, requestId, header, mutableListOf())
            chunks.add(chunk)
        }
        chunk.data.addAll(data)
    }

    fun finishPendingChunk(requestId: Byte, data: List<Byte>): Pair<List<Byte>,List<Byte>> {
        val chunk = chunks.removeAt(chunks.indexOfFirst { it.requestId == requestId })
        chunk.data.addAll(data)
        return Pair(chunk.header, chunk.data)
    }
}