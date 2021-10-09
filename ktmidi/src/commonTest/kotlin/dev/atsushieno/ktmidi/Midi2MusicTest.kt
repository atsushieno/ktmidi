package dev.atsushieno.ktmidi

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

val musicFileIdentifier = List(16) {0xAA.toByte()}
val musicTrackIdentifier = List(16) {0xEE.toByte()}

class Midi2MusicTest {

    @Test
    fun readWriteMusic() {
        val music = Midi2Music()
        music.addTrack(Midi2Track())
        music.tracks[0].messages.addAll(listOf(
            Ump(UmpFactory.midi2NoteOn(0, 1, 0x36, 0, 100 shl 8, 0)),
            Ump(UmpFactory.jrTimestamp(0, 31250)),
            Ump(UmpFactory.midi2NoteOff(0, 1, 0x36, 0, 100 shl 8, 0))
        ))
        val store = mutableListOf<Byte>()
        music.write(store)
        val music2 = Midi2Music().apply { read(store) }
        assertEquals(music.tracks.size, music2.tracks.size, "tracks")
        assertEquals(music.tracks[0].messages.size, music2.tracks[0].messages.size, "messages")
    }

    @Test
    fun writeSysex8() {
        val music = Midi2Music()
        val track = Midi2Track()
        music.tracks.add(track)
        val ff = 0xFF.toByte()
        // It is actually to write meta events specified in docs/MIDI2_FORMATS.md.
        val sysex8 = listOf(0, 0, 0, ff, ff, ff, ff, 3, 0x07, 0xA1.toByte(), 0x20) // META EVENT 3: set tempo (500000 = 120bpm)
        UmpFactory.sysex8Process(0, sysex8) { v1, v2, _ -> track.messages.add(Ump(v1, v2)) }
        val bytes = mutableListOf<Byte>()
        music.write(bytes)
        // Note that "stream id" also counts in the packet size in sysex8 (hence 12, not 11).
        // FIXME: consider correct endianness
        assertContentEquals(musicFileIdentifier + listOf(0, 0, 0, 0) + listOf(1, 0, 0, 0) +
            musicTrackIdentifier + listOf(1, 0, 0, 0) + listOf(0, 0, 12, 0x50) + listOf(ff, ff, 0, 0) + listOf(7, 3, ff, ff) + listOf(0, 0, 0x20, 0xA1.toByte()), bytes, "umpx")
    }
}