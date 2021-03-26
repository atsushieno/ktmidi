package dev.atsushieno.ktmidi

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

interface MidiPlayerTimer
{
    suspend fun waitBy (addedMilliseconds: Int)
    fun stop()
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

    override fun stop() {}
}

open class VirtualMidiPlayerTimer : MidiPlayerTimer {
    var totalWaitedMilliseconds : Long = 0
    var totalProceededMilliseconds: Long = 0;
    private var shouldTerminate: Boolean = false
    private var disposed : Boolean = false
    private var waitJobInLoop : Job? = null

    override fun stop ()
    {
        if (disposed)
            return
        shouldTerminate = true
        waitJobInLoop?.cancel()
        disposed = true
    }

    override suspend fun waitBy(addedMilliseconds: Int) {
        while (!shouldTerminate && totalWaitedMilliseconds + addedMilliseconds > totalProceededMilliseconds) {
            waitJobInLoop = GlobalScope.launch {
                delay(Int.MAX_VALUE.toLong())
            }
            waitJobInLoop?.join()
        }
        totalWaitedMilliseconds += addedMilliseconds
    }

    open fun proceedBy ( addedMilliseconds: Long)
    {
        if (addedMilliseconds < 0)
            throw IllegalArgumentException ("'addedMilliseconds' must be non-negative integer")
        totalProceededMilliseconds += addedMilliseconds
        waitJobInLoop?.cancel()
    }
}
