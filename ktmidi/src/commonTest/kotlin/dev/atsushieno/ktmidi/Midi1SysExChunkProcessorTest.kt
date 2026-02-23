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
            val seq1 = processor.process(sysex.take(10)).flatMap { it }
            // The inputs are still stored.
            assertFalse(seq1.iterator().hasNext(), "round $it: #1")
            val seq2 = processor.process(sysex.drop(10)).flatMap { it }
            // the pending inputs are flushed now (and should not remain: https://github.com/atsushieno/ktmidi/issues/81#issuecomment-2253261161)
            assertContentEquals(sysex, seq2.toList(), "round $it: #2")
        }
    }

    @Test
    fun sysexTerminatedByNonRealTimeStatusByte() {
        val processor = Midi1SysExChunkProcessor()
        val input = listOf(
            0xF0, 0x7E, 0x7F, 0x0D,
            0x90, 0x3C, 0x40
        ).map { it.toByte() }

        val result = processor.process(input).toList()
        assertEquals(2, result.size)
        assertContentEquals(listOf(0xF0, 0x7E, 0x7F, 0x0D).map { it.toByte() }, result[0])
        assertContentEquals(listOf(0x90, 0x3C, 0x40).map { it.toByte() }, result[1])
    }

    @Test
    fun sysexTerminatedByNonRealTimeStatusByteInRemainingBuffer() {
        val processor = Midi1SysExChunkProcessor()
        val input1 = listOf(0xF0, 0x7E, 0x7F).map { it.toByte() }
        val input2 = listOf(0x0D, 0x90, 0x3C, 0x40).map { it.toByte() }

        val result1 = processor.process(input1).toList()
        assertEquals(0, result1.size)

        val result2 = processor.process(input2).toList()
        assertEquals(2, result2.size)
        assertContentEquals(listOf(0xF0, 0x7E, 0x7F, 0x0D).map { it.toByte() }, result2[0])
        assertContentEquals(listOf(0x90, 0x3C, 0x40).map { it.toByte() }, result2[1])
    }

    @Test
    fun realTimeMessagesDuringIncompleteSysex() {
        val processor = Midi1SysExChunkProcessor()
        val input1 = listOf(0xF0, 0x7E, 0xF8, 0x7F).map { it.toByte() }
        val input2 = listOf(0x0D, 0xFE, 0x7E, 0xF7).map { it.toByte() }

        val result1 = processor.process(input1).toList()
        assertEquals(0, result1.size)

        val result2 = processor.process(input2).toList()
        assertEquals(1, result2.size)
        assertContentEquals(
            listOf(0xF0, 0x7E, 0xF8, 0x7F, 0x0D, 0xFE, 0x7E, 0xF7).map { it.toByte() },
            result2[0]
        )
    }

    @Test
    fun realTimeMessagesInCompleteSysex() {
        val processor = Midi1SysExChunkProcessor()
        val input = listOf(0xF0, 0x7E, 0xF8, 0x7F, 0x0D, 0xFE, 0x7E, 0xF7).map { it.toByte() }

        val result = processor.process(input).toList()
        assertEquals(1, result.size)
        assertContentEquals(input, result[0])
    }

    @Test
    fun multipleSysexMessagesTerminatedByStatusBytes() {
        val processor = Midi1SysExChunkProcessor()
        val input = listOf(
            0xF0, 0x01, 0x02,
            0x90, 0x3C, 0x40,
            0xF0, 0x03, 0x04, 0xF7,
            0x80, 0x3C, 0x00
        ).map { it.toByte() }

        val result = processor.process(input).toList()
        assertEquals(4, result.size)
        assertContentEquals(listOf(0xF0, 0x01, 0x02).map { it.toByte() }, result[0])
        assertContentEquals(listOf(0x90, 0x3C, 0x40).map { it.toByte() }, result[1])
        assertContentEquals(listOf(0xF0, 0x03, 0x04, 0xF7).map { it.toByte() }, result[2])
        assertContentEquals(listOf(0x80, 0x3C, 0x00).map { it.toByte() }, result[3])
    }
}