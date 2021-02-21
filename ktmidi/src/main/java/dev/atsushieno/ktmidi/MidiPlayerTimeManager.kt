package dev.atsushieno.ktmidi

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.locks.ReentrantLock

interface MidiPlayerTimeManager
{
    fun waitBy (addedMilliseconds: Int)
}

class VirtualMidiPlayerTimeManager : MidiPlayerTimeManager, AutoCloseable
{
    var wait_handle = ReentrantLock().newCondition()
    var total_waited_milliseconds: Long = 0
    var total_proceeded_milliseconds : Long = 0
    var should_terminate: Boolean = false
    var disposed : Boolean = false

    override fun close() {
        abort ()
    }

    fun abort ()
    {
        if (disposed)
            return
        should_terminate = true
        wait_handle.signal()
        disposed = true
    }

    override fun waitBy (addedMilliseconds: Int)
    {
        while (!should_terminate && total_waited_milliseconds + addedMilliseconds > total_proceeded_milliseconds) {
            wait_handle.await()
        }
        total_waited_milliseconds += addedMilliseconds
    }

    fun proceedBy (addedMilliseconds: Int)
    {
        if (addedMilliseconds < 0)
            throw IllegalArgumentException ("Argument must be non-negative integer")
        total_proceeded_milliseconds += addedMilliseconds
        wait_handle.signal()
    }
}

class SimpleAdjustingMidiPlayerTimeManager : MidiPlayerTimeManager
{
    var last_started : Long = 0
    var nominal_total_mills : Long = 0

    override fun waitBy(addedMilliseconds: Int) {
        if (addedMilliseconds > 0) {
            var delta = addedMilliseconds.toLong()
            if (last_started != 0.toLong()) {
                val actualTotalMills = System.currentTimeMillis() - last_started
                delta -= actualTotalMills - nominal_total_mills
            } else {
                last_started = System.currentTimeMillis()
            }
            if (delta > 0)
                runBlocking { delay(delta) }
            nominal_total_mills += addedMilliseconds
        }
    }
}
