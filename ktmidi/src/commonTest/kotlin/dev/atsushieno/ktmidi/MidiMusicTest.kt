package dev.atsushieno.ktmidi

import kotlin.experimental.or
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue


@kotlin.ExperimentalUnsignedTypes
class MidiMusicUnitTest {
    @Test
    fun getSmfBpm() {
        assertEquals(120.0, round(MidiMusic.getSmfBpm(byteArrayOf(7, 0xA1.toByte(), 0x20), 0)), "120")
        assertEquals(140.0, round(MidiMusic.getSmfBpm(byteArrayOf(6, 0x8A.toByte(), 0xB1.toByte()), 0)), "140")
    }

    @Test
    fun getSmpteTicksPerSeconds() {
        assertEquals(2400, MidiMusic.getSmpteTicksPerSeconds(0xE764), "24 x 100")
        assertEquals(2500, MidiMusic.getSmpteTicksPerSeconds(0xE664), "25 x 100")
        assertEquals(3000, MidiMusic.getSmpteTicksPerSeconds(0xE264), "29 x 100")
        assertEquals(3000, MidiMusic.getSmpteTicksPerSeconds(0xE164), "30 x 100")
        assertEquals(1200, MidiMusic.getSmpteTicksPerSeconds(0xE732), "24 x 50")
        assertEquals(1250, MidiMusic.getSmpteTicksPerSeconds(0xE632), "25 x 50")
        assertEquals(1500, MidiMusic.getSmpteTicksPerSeconds(0xE232), "29 x 50")
        assertEquals(1500, MidiMusic.getSmpteTicksPerSeconds(0xE132), "30 x 50")
    }

    @Test
    fun getSmpteDurationInSeconds() {
        // For 0E764 in 1.0 seconds, there should be 100 ticks * 24 frames for 2400 ticks.
        // For tempo 500000 (msec. for a quarter note), 2400 means two quarter notes.
        assertEquals(1.0, MidiMusic.getSmpteDurationInSeconds(0xE764, 1200, 500000, 1.0), "1200 in 24 x 100")
        assertEquals(0.5, MidiMusic.getSmpteDurationInSeconds(0xE764, 600, 500000, 1.0), "600 in 24 x 100")
        assertEquals(1.0, MidiMusic.getSmpteDurationInSeconds(0xE732, 600, 500000, 1.0), "600 in 24 x 50")
        assertEquals(2.0, MidiMusic.getSmpteDurationInSeconds(0xE732, 1200, 500000, 1.0), "1200 in 24 x 50")
    }

    @Test
    fun getSmpteTicksForSeconds() {
        // For 0E764 in 1.0 seconds, there should be 100 ticks * 24 frames for 2400 ticks.
        // For tempo 500000 (msec. for a quarter note), 2400 means two quarter notes.
        assertEquals(1200, MidiMusic.getSmpteTicksForSeconds(0xE764, 1.0, 500000, 1.0), "1.0 in 24 x 100")
        assertEquals(600, MidiMusic.getSmpteTicksForSeconds(0xE764, 0.5, 500000, 1.0), "0.5 in 24 x 100")
        assertEquals(600, MidiMusic.getSmpteTicksForSeconds(0xE732, 1.0, 500000, 1.0), "1.0 in 24 x 50")
        assertEquals(1200, MidiMusic.getSmpteTicksForSeconds(0xE732, 2.0, 500000, 1.0), "2.0 in 24 x 50")
    }

    @Test
    fun getFixedSize() {
        assertEquals(2, MidiEvent.fixedDataSize(0x90.toByte()).toInt(), "NoteOn")
        assertEquals(1, MidiEvent.fixedDataSize(0xC0.toByte()).toInt(), "ProgramChange")
        assertEquals(1, MidiEvent.fixedDataSize(0xD0.toByte()).toInt(), "CAf")
        assertEquals(2, MidiEvent.fixedDataSize(0xA0.toByte()).toInt(), "PAf")
        assertEquals(0, MidiEvent.fixedDataSize(0xF0.toByte()).toInt(), "SysEx")
        assertEquals(2, MidiEvent.fixedDataSize(0xF2.toByte()).toInt(), "SongPositionPointer")
        assertEquals(1, MidiEvent.fixedDataSize(0xF3.toByte()).toInt(), "SongSelect")
        assertEquals(0, MidiEvent.fixedDataSize(0xF8.toByte()).toInt(), "MidiClock")
        assertEquals(0, MidiEvent.fixedDataSize(0xFF.toByte()).toInt(), "META")
    }

    @Test
    fun midiEventConvert() {
        var bytes1 = byteArrayOf(0xF8.toByte())
        var events1 = MidiEvent.convert(bytes1, 0, bytes1.size)
        assertEquals(1, events1.count(), "bytes1 count")

        var bytes2 = byteArrayOf(0xFE.toByte())
        var events2 = MidiEvent.convert(bytes2, 0, bytes2.size)
        assertEquals(1, events2.count(), "bytes2 count")
    }

    @Test
    fun unsignedOperations() {
        val evt = MidiEvent(0xFF, 3, 4, charArrayOf('t', 'e', 's', 't').map { c -> c.code.toByte() }.toByteArray(), 0)
        assertEquals(0xFF.toByte(), evt.eventType, "eventType")
        assertEquals(3, evt.msb, "msb")
        assertEquals(4, evt.lsb, "lsb")
        assertEquals(4, evt.extraDataLength, "extraDataLength")
    }

    @Test
    fun midiMusicGetPlayTimeMillisecondsAtTick() {
        val music= TestHelper.getMidiMusic()
        assertEquals(0, music.getTimePositionInMillisecondsForTick(0), "tick 0")
        assertEquals(125, music.getTimePositionInMillisecondsForTick(48), "tick 48")
        assertEquals(500, music.getTimePositionInMillisecondsForTick(192), "tick 192")
    }

    @Test
    fun smfReaderRead () {
        val expected = intArrayOf(
            U.M, U.T, L.h, L.d, 0, 0, 0, 6, 0, 1, 0, 1, 0, 0x30, // at 14
            U.M, U.T, L.r, L.k, 0, 0, 0, 0x1C, // at 22
            0, 0x90, 0x3C, 100,
            0x30, 0x80, 0x3C, 0,
            0, 0x90, 0x3E, 100,
            0x30, 0x80, 0x3E, 0,
            0, 0x90, 0x40, 100,
            0x30, 0x80, 0x40, 0,
            0, 0xFF, 0x2F, 0).map { i -> i.toByte() }.toByteArray()
        val music = MidiMusic().apply { read(expected.toList()) }
        assertEquals(144, music.getTotalTicks(), "total ticks")
    }

    @Test
    fun smfWriterWrite() {
        val music = MidiMusic()
        val track = MidiTrack()
        music.tracks.add(track)
        val sysexData = arrayOf(0x7D, 0x0B, 0x2D, 0x31, 0x34, 0x37, 0x32, 0x35, 0x34, 0x39, 0x39, 0x37, 0x38)
                .map { v -> v.toByte() }.toByteArray()
        track.messages.add(MidiMessage(0, MidiEvent(0xF0, 0, 0, sysexData)))
        val bytes = mutableListOf<Byte>()
        SmfWriter(bytes).writeMusic(music)
        val trackHead = arrayOf('M'.code, 'T'.code, 'r'.code, 'k'.code, 0, 0, 0, 16, 0, 0xF0).map { v -> v.toByte() }.toByteArray()
        val headerChunkSize = 14 // MThd + sizeInfo(0 0 0 6) + actualSize(6 bytes)
        val actual = bytes.drop(headerChunkSize).toByteArray()
        assertContentEquals(trackHead + sysexData + byteArrayOf(0xF7.toByte()), actual, "SMF track")

        bytes.clear()
        SmfWriter(bytes).writeMusic(music)
        music.tracks.clear()
        assertTrue(bytes.size > 0, "bytes size")
        music.read(bytes)
        val evt = music.tracks.first().messages.first().event
        assertContentEquals(sysexData, evt.extraData!!.drop(evt.extraDataOffset).take(evt.extraDataLength).toByteArray(), "read")
        assertEquals(headerChunkSize + trackHead.size + sysexData.size + 1, bytes.size, "music bytes size")
    }

    // U and L cannot share case-insensitively identical fields for JNI signature...
    class U {
        companion object {
            val M = 'M'.code
            val T = 'T'.code
        }
    }

    class L {
        companion object {
            val h = 'h'.code
            val d = 'd'.code
            val r = 'r'.code
            val k = 'k'.code
            val e = 'e'.code
            val s = 's'.code
            val t = 't'.code
        }
    }

    @Test
    fun convert() {
        val bytes = intArrayOf(0xF0, 0x0A, 0x41, 0x10, 0x42, 0x12, 0x40, 0, 0x7F, 0, 0x41, 0xF7).map { it.toByte() }.toByteArray() // am too lazy to add cast to byte...
        val msgs = MidiEvent.convert (bytes, 0, bytes.size).asIterable().toList()
        assertEquals(1, msgs.size, "message length")
        assertEquals(bytes.size, msgs.first().extraDataLength)
    }

    @Test
    fun encode7BitLength() {
        assertEquals(listOf<Byte>(0), MidiMessage.encode7BitLength(0).toList(), "test1")
        assertEquals(listOf<Byte>(0x7F), MidiMessage.encode7BitLength(0x7F).toList(), "test2")
        assertEquals(listOf(0x80.toByte(), 1), MidiMessage.encode7BitLength(0x80).toList(), "test3")
        assertEquals(listOf(0xFF.toByte(), 1), MidiMessage.encode7BitLength(0xFF).toList(), "test4")
        assertEquals(listOf(0x80.toByte(), 2), MidiMessage.encode7BitLength(0x100).toList(), "test5")
        assertEquals(listOf(0xFF.toByte(), 0x7F.toByte()), MidiMessage.encode7BitLength(0x3FFF).toList(), "test6")
        assertEquals(listOf(0x80.toByte(), 0x80.toByte(), 1), MidiMessage.encode7BitLength(0x4000).toList(), "test7")
    }
}
