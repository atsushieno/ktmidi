package dev.atsushieno.ktmidi

import kotlinx.coroutines.delay
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

interface MidiPlayerTimer
{
    suspend fun waitBy (addedMilliseconds: Int)
}

class SimpleAdjustingMidiPlayerTimer : MidiPlayerTimer
{
    private var lastStarted : Double = 0.0
    private var nominalTotalMills : Double = 0.0

    @OptIn(ExperimentalTime::class)
    override suspend fun waitBy(addedMilliseconds: Int) {
        if (addedMilliseconds > 0) {
            var delta = addedMilliseconds.toLong()
            if (lastStarted != 0.0) {
                val actualTotalMills = TimeSource.Monotonic.markNow().elapsedNow().inMicroseconds - lastStarted
                delta -= (actualTotalMills - nominalTotalMills).toLong()
            } else {
                lastStarted = TimeSource.Monotonic.markNow().elapsedNow().inMicroseconds
            }
            if (delta > 0)
                delay(delta)
            nominalTotalMills += addedMilliseconds
        }
    }
}
