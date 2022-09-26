package dev.atsushieno.ktmidi

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MidiReaderWriterTest {
    @Test
    fun readMusicFromResource() {
        val music = MidiMusic()
        val stream = javaClass.getResource("/mugene-fantasy-suite/1-das-stromreiters.mid")
        music.read(stream.readBytes().toList())
    }

    @Test
    fun writeMetaText() {
        val music = MidiMusic()
        music.deltaTimeSpec = 0x30
        music.tracks.add(MidiTrack())
        music.tracks[0].messages.addAll(listOf(
            MidiMessage(0, MidiEvent(0xFF, 3, 0, byteArrayOf(0x41, 0x41, 0x41, 0x41), 0, 4)),
            MidiMessage(0, MidiEvent(0xFF, 0x2F, 0, byteArrayOf()))))
        val result = mutableListOf<Byte>()
        SmfWriter(result).writeMusic(music)
        val expected = intArrayOf(
            'M'.code, 'T'.code, 'h'.code, 'd'.code, 0, 0, 0, 6, 0, 1, 0, 1, 0, 0x30,
            'M'.code, 'T'.code, 'r'.code, 'k'.code, 0, 0, 0, 0x0C,
            0, 0xFF, 3, 4, 0x41, 0x41, 0x41, 0x41,
            0, 0xFF, 0x2F, 0).map { i -> i.toByte() }.toByteArray()
        val actual = result.toByteArray()
        assertContentEquals(expected, actual, "test1")
    }
}