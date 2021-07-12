package dev.atsushieno.ktmidi

import kotlin.test.Test

class MidiReaderWriterTest {
    @Test
    fun readMusicFromResource() {
        val music = MidiMusic()
        val stream = javaClass.getResource("/mugene-fantasy-suite/1-das-stromreiters.mid")
        music.read(stream.readBytes().toList())
    }
}