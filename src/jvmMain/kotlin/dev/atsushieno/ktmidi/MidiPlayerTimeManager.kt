package dev.atsushieno.ktmidi

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

@Deprecated("Use MidiPlayer and MidiPlayerTimer instead")
interface MidiPlayerTimeManager
{
    fun waitBy (addedMilliseconds: Int)
}

@Deprecated("Use MidiPlayer and MidiPlayerTimer instead")
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
