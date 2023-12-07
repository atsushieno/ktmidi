package dev.atsushieno.ktmidi

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

class MidiPlayerTimerTest {
    @OptIn(ExperimentalTime::class)
    // FIXME: make it non-intermittent and enable it again.
    //@Test
    fun simpleTimerTest() {
        runBlocking {
            val timeSource = TimeSource.Monotonic
            val start = timeSource.markNow()
            val timer = SimpleAdjustingMidiPlayerTimer(timeSource)
            for (i in 0 .. 4) {
                timer.waitBySeconds(1.0)
                val delta = start.elapsedNow().inWholeMilliseconds
                assertTrue(delta >= 1000.0 * (i + 1), "wait never happened: $delta msec. at $i")
                assertTrue(delta < 1000.0 * (i + 2), "wait taking too much time: $delta msec. at $i")
            }
        }
    }
}
