package dev.atsushieno.ktmidi

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class Midi1SysExChunkProcessorTest {
    @Test
    fun process1() {
        val processor = Midi1SysExChunkProcessor()
        (1..3).forEach {
            val sysex = listOf(0xF0,
                // invalidate MUID example
                0x7E, 0x7F, 0x0D, 0x7E, 1,
                0x10, 0x10, 0x10, 0x10, 0x7F, 0x7F, 0x7F, 0x7F, 0x20, 0x20, 0x20, 0x20,
                0xF7).map { it.toByte() }
            val seq1 = processor.process(sysex.take(10))
            // The inputs are still stored.
            assertFalse(seq1.iterator().hasNext(), "round $it: #1")
            val seq2 = processor.process(sysex.drop(10))
            // the pending inputs are flushed now (and should not remain: https://github.com/atsushieno/ktmidi/issues/81#issuecomment-2253261161)
            assertContentEquals(sysex, seq2.map { (it as Midi1CompoundMessage).extraData!!.toList() }.first(), "round $it: #2")
        }
    }

    // Helper: process a list of ints as bytes and return List<ByteArray>
    private fun Midi1SysExChunkProcessor.processBytes(vararg bytes: Int): List<ByteArray> {
        return process(bytes.map { it.toByte() })
            .map {
                when(it){
                    is Midi1CompoundMessage -> it.extraData!!
                    else -> byteArrayOf(it.statusByte, it.msb, it.lsb)
                }
            }.toList()
    }

    @Test
    fun `simple channel message`() {
        val processor = Midi1SysExChunkProcessor()
        val result = processor.processBytes(0x90, 0x40, 0x7F)
        assertEquals(1, result.size)
        assertContentEquals(byteArrayOf(0x90.toByte(), 0x40, 0x7F), result[0])
    }

    @Test
    fun `complete SysEx message`() {
        val processor = Midi1SysExChunkProcessor()
        val result = processor.processBytes(0xF0, 0x43, 0x12, 0x45, 0xF7)
        assertEquals(1, result.size)
        assertContentEquals(byteArrayOf(0xF0.toByte(), 0x43, 0x12, 0x45, 0xF7.toByte()), result[0])
    }

    @Test
    fun `incomplete SysEx buffered until next call`() {
        val processor = Midi1SysExChunkProcessor()
        val part1 = processor.processBytes(0xF0, 0x43, 0x12)
        val part2 = processor.processBytes(0x45, 0xF7)

        assertEquals(0, part1.size, "No message yet, SysEx incomplete")
        assertEquals(1, part2.size)
        assertContentEquals(byteArrayOf(0xF0.toByte(), 0x43, 0x12, 0x45, 0xF7.toByte()), part2[0])
    }

    @Test
    fun `status byte inside SysEx cancels message when returnCancelledSysEx true`() {
        val processor = Midi1SysExChunkProcessor(returnCancelledSysEx = true)
        val result = processor.processBytes(0xF0, 0x43, 0x12, 0x90, 0x40, 0x7F)

        assertEquals(2, result.size)
        assertContentEquals(byteArrayOf(0xF0.toByte(), 0x43, 0x12), result[0])
        assertContentEquals(byteArrayOf(0x90.toByte(), 0x40, 0x7F), result[1])
    }

    @Test
    fun `status byte inside SysEx drops message when returnCancelledSysEx false`() {
        val processor = Midi1SysExChunkProcessor(returnCancelledSysEx = false)
        val result = processor.processBytes(0xF0, 0x43, 0x12, 0x90, 0x40, 0x7F)

        assertEquals(1, result.size)
        assertContentEquals(byteArrayOf(0x90.toByte(), 0x40, 0x7F), result[0])
    }

    @Test
    fun `new SysEx starts before previous ends`() {
        val processor = Midi1SysExChunkProcessor(returnCancelledSysEx = true)
        val result = processor.processBytes(0xF0, 0x43, 0x12, 0xF0, 0x01, 0x02, 0xF7)

        assertEquals(2, result.size)
        assertContentEquals(byteArrayOf(0xF0.toByte(), 0x43, 0x12), result[0])
        assertContentEquals(byteArrayOf(0xF0.toByte(), 0x01, 0x02, 0xF7.toByte()), result[1])
    }

    @Test
    fun `two back to back SysEx messages`() {
        val processor = Midi1SysExChunkProcessor()
        val result = processor.processBytes(
            0xF0, 0x01, 0x02, 0xF7,
            0xF0, 0x03, 0x04, 0xF7
        )

        assertEquals(2, result.size)
        assertContentEquals(byteArrayOf(0xF0.toByte(), 0x01, 0x02, 0xF7.toByte()), result[0])
        assertContentEquals(byteArrayOf(0xF0.toByte(), 0x03, 0x04, 0xF7.toByte()), result[1])
    }

    @Test
    fun `status byte immediately after SysEx end`() {
        val processor = Midi1SysExChunkProcessor()
        val result = processor.processBytes(0xF0, 0x43, 0xF7, 0x90, 0x40, 0x7F)

        assertEquals(2, result.size)
        assertContentEquals(byteArrayOf(0xF0.toByte(), 0x43, 0xF7.toByte()), result[0])
        assertContentEquals(byteArrayOf(0x90.toByte(), 0x40, 0x7F), result[1])
    }
}