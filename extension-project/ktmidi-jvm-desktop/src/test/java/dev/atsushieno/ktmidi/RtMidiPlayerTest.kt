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
        if (!File("/dev/snd/seq").exists())
            return
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
