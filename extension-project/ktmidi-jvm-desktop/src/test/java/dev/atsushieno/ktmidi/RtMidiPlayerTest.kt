package dev.atsushieno.ktmidi

import kotlinx.coroutines.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RtMidiPlayerTest {

    class AlmostVirtualMidiPlayerTimer : VirtualMidiPlayerTimer() {
        override suspend fun waitBySeconds(addedSeconds: Double) {
            delay(50)
            super.waitBySeconds(addedSeconds)
        }
    }

    @Test
    fun playSimple() {
        // skip running this test on GH Actions Linux VMs (it's kind of hacky check but would be mostly harmless...)
        val homeRunnwrWork = File("/home/runner/work")
        if (System.getProperty("os.name").lowercase().contains("linux") && homeRunnwrWork.exists()) {
            println("Test rtMidiAccessInfo() is skipped on GitHub Actions as ALSA is unavailable.")
            return
        }

        runBlocking {
            delay(100)
            val vt = VirtualMidiPlayerTimer()
            val player = TestHelper.getMidiPlayer(vt, RtMidiAccess())
            player.play()
            vt.proceedBySeconds(10.0)
            delay(200)
            player.pause()
            player.close()
        }
    }
}
