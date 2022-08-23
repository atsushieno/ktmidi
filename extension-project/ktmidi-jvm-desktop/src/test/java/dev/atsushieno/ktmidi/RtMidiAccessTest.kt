package dev.atsushieno.ktmidi

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class RtMidiAccessTest {
    @Test
    fun rtMidiAccessInfo() {
        // skip running this test on GH Actions Linux VMs (it's kind of hacky check but would be mostly harmless...)
        val homeRunnwrWork = File("/home/runner/work")
        if (System.getProperty("os.name").lowercase().contains("linux") && homeRunnwrWork.exists()) {
            println("Test rtMidiAccessInfo() is skipped on GitHub Actions as ALSA is unavailable.")
            return
        }

        val rtmidi = RtMidiAccess()
        
        rtmidi.inputs.forEach {
            assertTrue(it.id.isNotEmpty(), "input.id")
            assertTrue(it.name!!.isNotEmpty(), "input.name for id: '${it.id}'")
        }
        rtmidi.outputs.forEach {
            assertTrue(it.id.isNotEmpty(), "output.id")
            assertTrue(it.name!!.isNotEmpty(), "output.name for id: '${it.id}'")
        }
    }
}