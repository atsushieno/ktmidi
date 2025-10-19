package dev.atsushieno.ktmidi

import kotlin.test.Test
import kotlin.test.assertContentEquals
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
}