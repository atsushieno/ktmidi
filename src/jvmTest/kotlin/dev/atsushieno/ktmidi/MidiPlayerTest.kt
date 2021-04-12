package dev.atsushieno.ktmidi

import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MidiPlayerTest {

    class AlmostVirtualMidiPlayerTimer : VirtualMidiPlayerTimer() {
        override suspend fun waitBy(addedMilliseconds: Int) {
            delay(50)
            super.waitBy(addedMilliseconds)
        }
    }

    @Test
    fun playSimple() {
        runBlocking {
            delay(100)
            val vt = VirtualMidiPlayerTimer()
            val player = TestHelper.getMidiPlayer(vt)
            player.play()
            vt.proceedBy(200000)
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
                    vt.proceedBy(100)

                    player.seek(5000)
                    assertEquals(5000, player.playDeltaTime, "1 PlayDeltaTime")
                    // compare rounded value
                    assertEquals(129, player.positionInMilliseconds.toInt() / 100, "1 PositionInMilliseconds")

                    vt.proceedBy(100)

                    // does not proceed beyond the last event.
                    assertEquals(5000, player.playDeltaTime, "2 PlayDeltaTime")
                    // compare rounded value
                    assertEquals(129, player.positionInMilliseconds.toInt() / 100, "2 PositionInMilliseconds")

                    player.seek(2000)
                    // 1964 (note on) ... 2000 ... 2008 (note off)
                    assertEquals(2000, player.playDeltaTime, "3 PlayDeltaTime")
                    // FIXME: not working
                    //assertEquals(5000, player.positionInMilliseconds, "3 PositionInMilliseconds")

                    vt.proceedBy(100)
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
        val qmsec = MidiMusic.getPlayTimeMillisecondsAtTick(music.tracks[0].messages, 4998, 192)
        assertEquals(totalTime, qmsec, "milliseconds at end by ticks")
        val player = TestHelper.getMidiPlayer(vt, music)
        var completed = false
        var finished = false

        player.playbackCompletedToEnd = Runnable { completed = true }
        player.finished = Runnable { finished = true }
        assertTrue(!completed, "1 PlaybackCompletedToEnd already fired")
        assertTrue(!finished, "2 Finished already fired")
        runBlocking {
            try {
                player.play()
                assertEquals(0, player.playDeltaTime, "PlayDeltaTime 1")
                vt.proceedBy(1000)

                // Currently those Timer proceedBy() calls are "fire and forget" style and does not really care
                //  if the bound MidiPlayer actually went ahead or not. It totally depends on whether OS/platform
                //  context switches happened and the player loop thread had resumed.
                //
                // At this state we introduce delay() and assume (or "hope") that player loop thread resumes.
                delay(200)
                //assertEquals(376, player.playDeltaTime, "PlayDeltaTime 2")

                assertTrue(!completed, "3 PlaybackCompletedToEnd already fired")
                assertTrue(!finished, "4 Finished already fired")
                vt.proceedBy(totalTime - 1000.toLong())
                assertEquals(12989, vt.totalProceededMilliseconds, "total proceeded milliseconds")

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

    @Test
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
                vt.proceedBy(1000)

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
