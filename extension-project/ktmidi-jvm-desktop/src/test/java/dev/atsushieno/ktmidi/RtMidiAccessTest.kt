package dev.atsushieno.ktmidi

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class RtMidiAccessTest {
    @Test
    fun rtMidiAccessInfo() {
        // skip running this test on Linux VMs.
        val seq = File("/dev/snd/seq")
        if (System.getProperty("os.name").lowercase().contains("linux") && (!seq.exists() || !seq.canRead() || !seq.canWrite())) {
            println("Test rtMidiAccessInfo() is skipped as ALSA is unavailable.")
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