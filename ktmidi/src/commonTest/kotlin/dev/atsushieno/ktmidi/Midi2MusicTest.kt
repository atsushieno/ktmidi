package dev.atsushieno.ktmidi

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

val musicFileIdentifier = List(8) {'A'.code.toByte()} + List(8) {'E'.code.toByte()}
val musicTrackIdentifier = "SMF2CLIP".map { it.code.toByte() }

class Midi2MusicTest {

    @Test
    fun readWriteMusic() {
        val music = Midi2Music()
        music.addTrack(Midi2Track())
        music.tracks[0].messages.addAll(listOf(
            Ump(UmpFactory.midi2NoteOn(0, 1, 0x36, 0, 100 shl 8, 0)),
            Ump(UmpFactory.deltaClockstamp(31250)),
            Ump(UmpFactory.midi2NoteOff(0, 1, 0x36, 0, 100 shl 8, 0))
        ))
        val store = mutableListOf<Byte>()
        music.write(store)
        val music2 = Midi2Music().apply { read(store, removeEmptyDeltaClockstamps = false) }
        assertEquals(music.tracks.size, music2.tracks.size, "tracks")
        val track = music2.tracks[0]
        assertEquals(track.messages.size, music2.tracks[0].messages.size, "messages")
        assertEquals(true, track.messages[1].isDCTPQ, "[1] is DCTPQ")
        assertEquals(true, track.messages[2].isDeltaClockstamp, "[2] is DCS")
        assertEquals(true, track.messages[3].isStartOfClip, "[3] is Start of Clip")
        assertEquals(true, track.messages[4].isDeltaClockstamp, "[4] is DCS")
        assertEquals(MidiMessageType.MIDI2, track.messages[5].messageType, "[5].messageType")
        assertEquals(true, track.messages[6].isDeltaClockstamp, "[6] is DC")
        assertEquals(MidiMessageType.MIDI2, track.messages[7].messageType, "[6].messageType")
    }

    @Test
    fun writeSysex8() {
        val music = Midi2Music()
        val track = Midi2Track()
        music.tracks.add(track)
        val ff = 0xFF.toByte()
        // It is actually to write meta events specified in docs/MIDI2_FORMATS.md.
        val sysex8 = listOf(0, 0, 0, 0, ff, ff, ff, 3, 0x07, 0xA1.toByte(), 0x20) // META EVENT 3: set tempo (500000 = 120bpm)
        UmpFactory.sysex8Process(0, sysex8) { v1, v2, _ -> track.messages.add(Ump(v1, v2)) }
        val bytes = mutableListOf<Byte>()
        music.write(bytes)
        // Note that "stream id" also counts in the packet size in sysex8 (hence 12, not 11).
        // Note that umpx is always serialized in BIG endian.
        val expected = musicFileIdentifier + listOf(0, 0, 1, 0xE0.toByte()) + listOf(0, 0, 0, 1) +musicTrackIdentifier +
            // DCS(0)
            listOf(0x00, 0x40, 0, 0) +
            // DCTPQ
            listOf(0x00, 0x30, 1, 0xE0.toByte()) +
            // DCS(0)
            listOf(0x00, 0x40, 0, 0) +
            // Start of Clip
            listOf(0xF0.toByte(), 0x20, 0, 0) + List(12) { 0.toByte() } +
            // DCS(0)
            listOf(0x00, 0x40, 0, 0) +
            // SysEx8
            listOf(0x50, 12, 0, 0, 0, 0, 0, ff, ff, ff, 3, 7, 0xA1.toByte(), 0x20, 0, 0) +
            // DCS(0)
            listOf(0x00, 0x40, 0, 0) +
            // End of Clip
            listOf(0xF0.toByte(), 0x21, 0, 0) + List(12) { 0.toByte() }
        assertContentEquals(expected, bytes, "umpx")
    }
}