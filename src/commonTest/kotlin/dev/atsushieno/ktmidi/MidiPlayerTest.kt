package dev.atsushieno.ktmidi

import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MidiPlayerTest {

     class AlmostVirtualMidiPlayerTimer : VirtualMidiPlayerTimer() {
         override suspend fun waitBy(addedMilliseconds: Int) {
             delay (50)
            super.waitBy (addedMilliseconds)
        }
    }

    @Test
    fun playSimple ()
    {
        val vt = VirtualMidiPlayerTimer ()
        val player = TestHelper.getMidiPlayer (vt)
        player.play ()
        vt.proceedBy (200000)
        player.pause ()
        player.dispose ()
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
    fun playbackCompletedToEnd () {
        val vt = VirtualMidiPlayerTimer()
        val music = TestHelper.getMidiMusic()
        val qmsec = MidiMusic.getPlayTimeMillisecondsAtTick(music.tracks[0].messages, 4998, 192)
        val player = TestHelper.getMidiPlayer(vt, music)
        var completed = false
        var finished = false

        player.playbackCompletedToEnd = Runnable { completed = true }
        player.finished = Runnable { finished = true }
        assertTrue(!completed, "1 PlaybackCompletedToEnd already fired")
        assertTrue(!finished, "2 Finished already fired")
        player.play()
        vt.proceedBy(100)
        assertTrue(!completed, "3 PlaybackCompletedToEnd already fired")
        assertTrue(!finished, "4 Finished already fired")
        vt.proceedBy(qmsec.toLong())
        assertEquals(12989, qmsec, "qmsec")

        // FIXME: this is an ugly spin-wait
        while (player.playDeltaTime < 4988)
            print("")

        assertEquals(4988, player.playDeltaTime, "PlayDeltaTime")
        player.pause()
        player.dispose()
        assertTrue(completed, "5 PlaybackCompletedToEnd not fired")
        assertTrue(finished, "6 Finished not fired")
    }

    @Test
    fun playbackCompletedToEndAbort () {
        val vt = VirtualMidiPlayerTimer ()
        val player = TestHelper.getMidiPlayer (vt)
        var completed = false
        var finished = false
        player.playbackCompletedToEnd = Runnable { completed = true}
        player.finished = Runnable { finished = true }
        player.play ()
        vt.proceedBy (1000)

        // FIXME: this is an ugly spin-wait
        while (player.playDeltaTime < 4988)
            print("")

        player.pause ()
        player.dispose () // abort in the middle
        assertFalse( completed, "1 PlaybackCompletedToEnd unexpectedly fired")
        assertTrue (finished, "2 Finished not fired")
    }
}
