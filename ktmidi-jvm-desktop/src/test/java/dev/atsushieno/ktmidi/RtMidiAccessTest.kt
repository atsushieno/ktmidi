package dev.atsushieno.ktmidi

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class RtMidiAccessTest {
    @Test
    fun rtMidiAccessInfo() {
        // skip running this test on GH Actions Linux VMs (it's kind of hacky check but would be mostly harmless...)
        val homeRunnerWork = File("/home/runner/work")
        if (System.getProperty("os.name").lowercase().contains("linux") && homeRunnerWork.exists()) {
            println("Test rtMidiAccessInfo() is skipped on GitHub Actions as ALSA is unavailable.")
            return
        }
        // it does not work on M1 mac and arm64 Linux either (lack of deps)
        if (System.getProperty("os.arch") == "aarch64") {
            println("Test rtMidiAccessInfo() is skipped as rtmidi-jna aarch64 builds are not available.")
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