package dev.atsushieno.ktmidi

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource

interface MidiPlayerTimer {
    suspend fun waitBySeconds(addedSeconds: Double)
    fun stop()
}

@OptIn(ExperimentalTime::class)
class SimpleAdjustingMidiPlayerTimer(private val timeSource: TimeSource = TimeSource.Monotonic) : MidiPlayerTimer {
    private lateinit var startedTime: TimeMark
    private var nominalTotalSeconds: Double = 0.0

    override suspend fun waitBySeconds(addedSeconds: Double) {
        if (addedSeconds > 0) {
            var delta = addedSeconds
            if (::startedTime.isInitialized) {
                val actualTotalSeconds = startedTime.elapsedNow().inWholeMicroseconds / 1_000_000.0
                delta -= actualTotalSeconds - nominalTotalSeconds
            } else {
                startedTime = timeSource.markNow()
            }
            if (delta > 0)
                delay((delta * 1000).toLong())
            nominalTotalSeconds += addedSeconds
        }
    }

    override fun stop() {}
}

open class VirtualMidiPlayerTimer : MidiPlayerTimer {
    var totalWaitedSeconds: Double = 0.0
    var totalProceededSeconds: Double = 0.0
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

    override suspend fun waitBySeconds(addedSeconds: Double) {
        while (!shouldTerminate && totalWaitedSeconds + addedSeconds > totalProceededSeconds)
            semaphore.acquire()
        totalWaitedSeconds += addedSeconds
    }

    open fun proceedBySeconds(addedSeconds: Double) {
        if (addedSeconds < 0)
            throw IllegalArgumentException("'addedSeconds' must be non-negative value")
        totalProceededSeconds += addedSeconds
        if (semaphore.availablePermits == 0)
            semaphore.release()
    }
}
