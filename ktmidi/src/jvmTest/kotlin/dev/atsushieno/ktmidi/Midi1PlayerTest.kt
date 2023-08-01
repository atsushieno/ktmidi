package dev.atsushieno.ktmidi

import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Midi1PlayerTest {

    class AlmostVirtualMidiPlayerTimer : VirtualMidiPlayerTimer() {
        override suspend fun waitBySeconds(addedSeconds: Double) {
            delay(50)
            super.waitBySeconds(addedSeconds)
        }
    }

    @Test
    fun playSimple() {
        runBlocking {
            delay(100)
            val vt = VirtualMidiPlayerTimer()
            val player = TestHelper.getMidiPlayer(vt)
            player.play()
            vt.proceedBySeconds(10.0)
            delay(200)
            player.pause()
            player.close()
        }
    }

    /*
    @Test
    fun playRtMidi () {
        val vt = AlmostVirtualMidiPlayerTimeManager ()
        val player = TestHelper.getMidiPlayer (vt, RtMidi.RtMidiAccess ())
        player.play ()
        vt.proceedBy (200000)
        player.pause ()
        player.dispose ()
    }
    */

    /*
    @Test
    public void playPortMidi () {
        val vt = AlmostVirtualMidiPlayerTimeManager ()
        val player = TestHelper.getMidiPlayer (vt, PortMidi.PortMidiAccess ())
        player.play ()
        vt.proceedBy (200000)
        player.pause ()
        player.dispose ()
    }
    */

    @Test
    fun getTimePositionInMillisecondsForTick ()
    {
        var caughtError: Error? = null
        runBlocking {
            val job = GlobalScope.launch {
                try {
                    val vt = VirtualMidiPlayerTimer()
                    val player = TestHelper.getMidiPlayer(vt)
                    player.play()
                    vt.proceedBySeconds(0.1)

                    player.seek(5000)
                    assertEquals(5000, player.playDeltaTime, "1 PlayDeltaTime")
                    // compare rounded value
                    assertEquals(129, player.positionInMilliseconds.toInt() / 100, "1 PositionInMilliseconds")

                    vt.proceedBySeconds(0.1)

                    // does not proceed beyond the last event.
                    assertEquals(5000, player.playDeltaTime, "2 PlayDeltaTime")
                    // compare rounded value
                    assertEquals(129, player.positionInMilliseconds.toInt() / 100, "2 PositionInMilliseconds")

                    player.seek(2000)
                    // 1964 (note on) ... 2000 ... 2008 (note off)
                    assertEquals(2000, player.playDeltaTime, "3 PlayDeltaTime")
                    // FIXME: not working
                    //assertEquals(5000, player.positionInMilliseconds, "3 PositionInMilliseconds")

                    vt.proceedBySeconds(0.1)
                    // FIXME: not working
                    //assertEquals(2100, player.playDeltaTime, "4 PlayDeltaTime")
                    //assertEquals(5000, player.positionInMilliseconds, "4 PositionInMilliseconds")
                } catch (ex: Error) {
                    caughtError = ex
                }
            }

            job.join()
            if (caughtError != null)
                throw caughtError!!
        }
    }

    @Test
    fun playbackCompletedToEnd() {
        val vt = VirtualMidiPlayerTimer()
        val music = TestHelper.getMidiMusic()

        assertEquals(4988, music.getTotalTicks(), "music total ticks")
        val totalTime = music.getTotalPlayTimeMilliseconds()
        assertEquals(12989, totalTime, "music total milliseconds")
        val qmsec = Midi1Music.getPlayTimeMillisecondsAtTick(music.tracks[0].messages, 4998, 192)
        assertEquals(totalTime, qmsec, "milliseconds at end by ticks")
        runBlocking {
            val player = TestHelper.getMidiPlayer(vt, music)
            var completed = false
            var finished = false

            player.playbackCompletedToEnd = Runnable { completed = true }
            player.finished = Runnable { finished = true }
            assertTrue(!completed, "1 PlaybackCompletedToEnd already fired")
            assertTrue(!finished, "2 Finished already fired")
            try {
                player.play()
                assertEquals(0, player.playDeltaTime, "PlayDeltaTime 1")
                vt.proceedBySeconds(1.0)

                // Currently those Timer proceedBy() calls are "fire and forget" style and does not really care
                //  if the bound MidiPlayer actually went ahead or not. It totally depends on whether OS/platform
                //  context switches happened and the player loop thread had resumed.
                //
                // At this state we introduce delay() and assume (or "hope") that player loop thread resumes.
                delay(200)
                //assertEquals(376, player.playDeltaTime, "PlayDeltaTime 2")

                assertTrue(!completed, "3 PlaybackCompletedToEnd already fired")
                assertTrue(!finished, "4 Finished already fired")
                vt.proceedBySeconds(totalTime / 1000.0 - 1.0 + 0.01) // + 10msec. to ensure that it is not rounded within the total time...
                assertEquals(12999, (vt.totalProceededSeconds * 1000).toInt(), "total proceeded milliseconds")

                delay(100) // FIXME: should not be required

                assertTrue(completed, "5 PlaybackCompletedToEnd not fired")
                assertEquals(4988, player.playDeltaTime, "PlayDeltaTime 3")

                player.pause()
                player.close()
                assertTrue(finished, "6 Finished not fired")
            } catch (ex: Error) {
                player.stop()
                player.close()
                throw ex
            }
        }
    }

    // FIXME: fix and enable this test again
    //@Test
    fun playbackCompletedToEndAbort() {
        runBlocking {
            val vt = VirtualMidiPlayerTimer()
            val player = TestHelper.getMidiPlayer(vt)
            var completed = false
            var finished = false
            player.playbackCompletedToEnd = Runnable { completed = true }
            player.finished = Runnable { finished = true }
            try {
                player.play()
                vt.proceedBySeconds(1.0)

                delay(100) // FIXME: should not be required
                player.pause()
                delay(100) // FIXME: should not be required
                player.close() // abort in the middle
                delay(100) // FIXME: should not be required
                assertFalse(completed, "1 PlaybackCompletedToEnd unexpectedly fired")
                assertTrue(finished, "2 Finished not fired")
            } catch (ex: Error) {
                player.stop()
                player.close()
                throw ex
            }
        }
    }
}
