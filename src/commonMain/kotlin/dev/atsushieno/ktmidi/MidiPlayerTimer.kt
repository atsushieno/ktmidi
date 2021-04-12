package dev.atsushieno.ktmidi

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

interface MidiPlayerTimer {
    suspend fun waitBy(addedMilliseconds: Int)
    fun stop()
}

class SimpleAdjustingMidiPlayerTimer : MidiPlayerTimer {
    private var lastStarted: Double = 0.0
    private var nominalTotalMills: Double = 0.0

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
    var totalWaitedMilliseconds: Long = 0
    var totalProceededMilliseconds: Long = 0;
    private var shouldTerminate: Boolean = false
    private var disposed: Boolean = false
    private val semaphore = Semaphore(1, 0)

    override fun stop() {
        if (disposed)
            return
        shouldTerminate = true
        if (semaphore.availablePermits == 0)
            semaphore.release()
        disposed = true
    }

    override suspend fun waitBy(addedMilliseconds: Int) {
        while (!shouldTerminate && totalWaitedMilliseconds + addedMilliseconds > totalProceededMilliseconds)
            semaphore.acquire()
        totalWaitedMilliseconds += addedMilliseconds
    }

    open fun proceedBy(addedMilliseconds: Long) {
        if (addedMilliseconds < 0)
            throw IllegalArgumentException("'addedMilliseconds' must be non-negative integer")
        totalProceededMilliseconds += addedMilliseconds
        if (semaphore.availablePermits == 0)
            semaphore.release()
    }
}
