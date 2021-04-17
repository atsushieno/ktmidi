package dev.atsushieno.ktmidi

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

class MidiPlayerTimerTest {
    @OptIn(ExperimentalTime::class)
    @Test
    fun simpleTimerTest() {
        runBlocking {
            val timer = SimpleAdjustingMidiPlayerTimer()
            val start = TimeSource.Monotonic.markNow()
            for (i in 0..4) {
                timer.waitBySeconds(0.4)
                val delta = start.elapsedNow().inMilliseconds
                assertTrue(delta >= 400.0 * (i + 1), "wait never happened: $delta msec. at $i")
                assertTrue(delta < 400.0 * (i + 2), "wait taking too much time: $delta msec. at $i")
            }
        }
    }
}
