package dev.atsushieno.ktmidi

import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue


@kotlin.ExperimentalUnsignedTypes
class Midi1MusicUnitTest {
    @Test
    fun getSmfBpm() {
        assertEquals(120.0, round(Midi1Music.getSmfBpm(byteArrayOf(7, 0xA1.toByte(), 0x20), 0)), "120")
        assertEquals(140.0, round(Midi1Music.getSmfBpm(byteArrayOf(6, 0x8A.toByte(), 0xB1.toByte()), 0)), "140")
    }

    @Test
    fun getSmpteTicksPerSeconds() {
        assertEquals(2400, Midi1Music.getSmpteTicksPerSeconds(0xE764), "24 x 100")
        assertEquals(2500, Midi1Music.getSmpteTicksPerSeconds(0xE664), "25 x 100")
        assertEquals(3000, Midi1Music.getSmpteTicksPerSeconds(0xE264), "29 x 100")
        assertEquals(3000, Midi1Music.getSmpteTicksPerSeconds(0xE164), "30 x 100")
        assertEquals(1200, Midi1Music.getSmpteTicksPerSeconds(0xE732), "24 x 50")
        assertEquals(1250, Midi1Music.getSmpteTicksPerSeconds(0xE632), "25 x 50")
        assertEquals(1500, Midi1Music.getSmpteTicksPerSeconds(0xE232), "29 x 50")
        assertEquals(1500, Midi1Music.getSmpteTicksPerSeconds(0xE132), "30 x 50")
    }

    @Test
    fun getSmpteDurationInSeconds() {
        // For 0E764 in 1.0 seconds, there should be 100 ticks * 24 frames for 2400 ticks.
        // For tempo 500000 (msec. for a quarter note), 2400 means two quarter notes.
        assertEquals(1.0, Midi1Music.getSmpteDurationInSeconds(0xE764, 1200, 500000, 1.0), "1200 in 24 x 100")
        assertEquals(0.5, Midi1Music.getSmpteDurationInSeconds(0xE764, 600, 500000, 1.0), "600 in 24 x 100")
        assertEquals(1.0, Midi1Music.getSmpteDurationInSeconds(0xE732, 600, 500000, 1.0), "600 in 24 x 50")
        assertEquals(2.0, Midi1Music.getSmpteDurationInSeconds(0xE732, 1200, 500000, 1.0), "1200 in 24 x 50")
    }

    @Test
    fun getSmpteTicksForSeconds() {
        // For 0E764 in 1.0 seconds, there should be 100 ticks * 24 frames for 2400 ticks.
        // For tempo 500000 (msec. for a quarter note), 2400 means two quarter notes.
        assertEquals(1200, Midi1Music.getSmpteTicksForSeconds(0xE764, 1.0, 500000, 1.0), "1.0 in 24 x 100")
        assertEquals(600, Midi1Music.getSmpteTicksForSeconds(0xE764, 0.5, 500000, 1.0), "0.5 in 24 x 100")
        assertEquals(600, Midi1Music.getSmpteTicksForSeconds(0xE732, 1.0, 500000, 1.0), "1.0 in 24 x 50")
        assertEquals(1200, Midi1Music.getSmpteTicksForSeconds(0xE732, 2.0, 500000, 1.0), "2.0 in 24 x 50")
    }

    @Test
    fun getFixedSize() {
        assertEquals(2, Midi1Message.fixedDataSize(0x90.toByte()).toInt(), "NoteOn")
        assertEquals(1, Midi1Message.fixedDataSize(0xC0.toByte()).toInt(), "ProgramChange")
        assertEquals(1, Midi1Message.fixedDataSize(0xD0.toByte()).toInt(), "CAf")
        assertEquals(2, Midi1Message.fixedDataSize(0xA0.toByte()).toInt(), "PAf")
        assertEquals(0, Midi1Message.fixedDataSize(0xF0.toByte()).toInt(), "SysEx")
        assertEquals(2, Midi1Message.fixedDataSize(0xF2.toByte()).toInt(), "SongPositionPointer")
        assertEquals(1, Midi1Message.fixedDataSize(0xF3.toByte()).toInt(), "SongSelect")
        assertEquals(0, Midi1Message.fixedDataSize(0xF8.toByte()).toInt(), "MidiClock")
        assertEquals(0, Midi1Message.fixedDataSize(0xFF.toByte()).toInt(), "META")
    }

    @Test
    fun midiEventConvert() {
        var bytes1 = byteArrayOf(0xF8.toByte())
        var events1 = Midi1Message.convert(bytes1, 0, bytes1.size)
        assertEquals(1, events1.count(), "bytes1 count")

        var bytes2 = byteArrayOf(0xFE.toByte())
        var events2 = Midi1Message.convert(bytes2, 0, bytes2.size)
        assertEquals(1, events2.count(), "bytes2 count")
    }

    @Test
    fun unsignedOperations() {
        val evt = Midi1CompoundMessage(0xFF, 3, 4, charArrayOf('t', 'e', 's', 't').map { c -> c.code.toByte() }.toByteArray(), 0)
        assertEquals(0xFF.toByte(), evt.statusCode, "eventType")
        assertEquals(3, evt.msb, "msb")
        assertEquals(4, evt.lsb, "lsb")
        assertEquals(4, evt.extraDataLength, "extraDataLength")
    }

    @Test
    fun midi1MusicGetPlayTimeMillisecondsAtTick() {
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
        val music = Midi1Music().apply { read(expected.toList()) }
        assertEquals(144, music.getTotalTicks(), "total ticks")
    }

    @Test
    fun smfWriterWrite() {
        val music = Midi1Music()
        val track = Midi1Track()
        music.tracks.add(track)
        val sysexData = arrayOf(0x7D, 0x0B, 0x2D, 0x31, 0x34, 0x37, 0x32, 0x35, 0x34, 0x39, 0x39, 0x37, 0x38, 0xF7)
                .map { v -> v.toByte() }.toByteArray()
        val sysexDataPart1 = sysexData.slice(0..4).toByteArray()
        val sysexDataPart2 = sysexData.slice(5..8).toByteArray()
        val sysexDataPart3 = sysexData.slice(9..13).toByteArray()
        track.events.add(Midi1Event(0, Midi1CompoundMessage(0xF0, 0, 0, sysexData)))
        track.events.add(Midi1Event(0, Midi1CompoundMessage(0xF0, 0, 0, sysexDataPart1)))
        track.events.add(Midi1Event(100, Midi1CompoundMessage(0xF7, 0, 0, sysexDataPart2)))
        track.events.add(Midi1Event(200, Midi1CompoundMessage(0xF7, 0, 0, sysexDataPart3)))
        val bytes = mutableListOf<Byte>()
        music.write(bytes)
        val trackHead = arrayOf('M'.code, 'T'.code, 'r'.code, 'k'.code, 0, 0, 0, 45, 0, 0xF0, 14).map { v -> v.toByte() }.toByteArray()
        val multiPacketSysexData = byteArrayOf(0, 0xF0.toByte(), 5) + sysexDataPart1 +
                byteArrayOf(0x64, 0xF7.toByte(), 4) + sysexDataPart2 +
                byteArrayOf(0x81.toByte(), 0x48, 0xF7.toByte(), 5) + sysexDataPart3
        val headerChunkSize = 14 // MThd + sizeInfo(0 0 0 6) + actualSize(6 bytes)
        val actual = bytes.drop(headerChunkSize).toByteArray()
        assertContentEquals(trackHead + sysexData + multiPacketSysexData + byteArrayOf(0, 0xFF.toByte(), 0x2F, 0), actual, "SMF track")

        bytes.clear()
        music.write(bytes)
        music.tracks.clear()
        assertTrue(bytes.size > 0, "bytes size")
        music.read(bytes)
        val evt = music.tracks.first().events.first().message as Midi1CompoundMessage
        assertContentEquals(sysexData, evt.extraData!!.drop(evt.extraDataOffset).take(evt.extraDataLength).toByteArray(), "read")
        assertEquals(headerChunkSize + trackHead.size + sysexData.size + multiPacketSysexData.size + 4, bytes.size, "music bytes size")
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
        val msgs = Midi1Message.convert (bytes, 0, bytes.size).asIterable().toList()
        assertEquals(1, msgs.size, "message length")
        assertEquals(bytes.size, (msgs.first() as Midi1CompoundMessage).extraDataLength)
    }

    @Test
    fun encode7BitLength() {
        assertEquals(listOf<Byte>(0), Midi1Event.encode7BitLength(0).toList(), "test1")
        assertEquals(listOf<Byte>(0x7F), Midi1Event.encode7BitLength(0x7F).toList(), "test2")
        assertEquals(listOf(0x80.toByte(), 1), Midi1Event.encode7BitLength(0x80).toList(), "test3")
        assertEquals(listOf(0xFF.toByte(), 1), Midi1Event.encode7BitLength(0xFF).toList(), "test4")
        assertEquals(listOf(0x80.toByte(), 2), Midi1Event.encode7BitLength(0x100).toList(), "test5")
        assertEquals(listOf(0xFF.toByte(), 0x7F.toByte()), Midi1Event.encode7BitLength(0x3FFF).toList(), "test6")
        assertEquals(listOf(0x80.toByte(), 0x80.toByte(), 1), Midi1Event.encode7BitLength(0x4000).toList(), "test7")
    }
}
