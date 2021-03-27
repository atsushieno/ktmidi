package dev.atsushieno.ktmidi

import dev.atsushieno.ktmidi.MidiEvent
import dev.atsushieno.ktmidi.MidiMetaType
import kotlin.math.round
import kotlin.test.assertEquals
import kotlin.test.Test

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
}
