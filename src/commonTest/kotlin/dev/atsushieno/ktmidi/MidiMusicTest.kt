package dev.atsushieno.ktmidi

import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals


@kotlin.ExperimentalUnsignedTypes
class MidiMusicUnitTest {
    @Test
    fun getBpm() {
        assertEquals(120.0, round(MidiMetaType.getBpm(byteArrayOf(7, 0xA1.toByte(), 0x20), 0)), "120")
        assertEquals(140.0, round(MidiMetaType.getBpm(byteArrayOf(6, 0x8A.toByte(), 0xB1.toByte()), 0)), "140")
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
        val evt = MidiEvent(0xFF, 3, 4, charArrayOf('t', 'e', 's', 't').map { c -> c.toByte() }.toByteArray(), 0)
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
        val music = SmfReader.read(expected.toList())
        assertEquals(144, music.getTotalTicks(), "total ticks")
    }

    // U and L cannot share case-insensitively identical fields for JNI signature...
    class U {
        companion object {
            val M = 'M'.toInt()
            val T = 'T'.toInt()
        }
    }

    class L {
        companion object {
            val h = 'h'.toInt()
            val d = 'd'.toInt()
            val r = 'r'.toInt()
            val k = 'k'.toInt()
            val e = 'e'.toInt()
            val s = 's'.toInt()
            val t = 't'.toInt()
        }
    }

    @Test
    fun convert() {
        val bytes = intArrayOf(0xF0, 0x0A, 0x41, 0x10, 0x42, 0x12, 0x40, 0, 0x7F, 0, 0x41, 0xF7).map { it.toByte() }.toByteArray() // am too lazy to add cast to byte...
        val msgs = MidiEvent.convert (bytes, 0, bytes.size).asIterable().toList()
        assertEquals(1, msgs.size, "message length")
        assertEquals(bytes.size, msgs.first().extraDataLength)
    }
}
