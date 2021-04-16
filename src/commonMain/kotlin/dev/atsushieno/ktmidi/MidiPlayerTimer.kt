package dev.atsushieno.ktmidi

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource

interface MidiPlayerTimer {
    suspend fun waitBy(addedMilliseconds: Int)
    fun stop()
}

@OptIn(ExperimentalTime::class)
class SimpleAdjustingMidiPlayerTimer : MidiPlayerTimer {
    private lateinit var startedTime: TimeMark
    var nominalTotalMills: Double = 0.0

    override suspend fun waitBy(addedMilliseconds: Int) {
        if (addedMilliseconds > 0) {
            var delta = addedMilliseconds.toLong()
            if (::startedTime.isInitialized) {
                val actualTotalMills = startedTime.elapsedNow().inMilliseconds
                delta -= (actualTotalMills - nominalTotalMills).toLong()
            } else {
                startedTime = TimeSource.Monotonic.markNow()
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
