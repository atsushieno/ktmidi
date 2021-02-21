package dev.atsushieno.ktmidi

import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test

@kotlin.ExperimentalUnsignedTypes
class MidiMusicUnitTest {
    @Test
    fun getBpm() {
        Assert.assertEquals("120", 120, Math.round(MidiMetaType.getBpm(byteArrayOf(7, 0xA1.toByte(), 0x20), 0)))
        Assert.assertEquals("140", 140, Math.round(MidiMetaType.getBpm(byteArrayOf(6, 0x8A.toByte(), 0xB1.toByte()), 0)))
    }


    @Test
    fun getFixedSize() {
        Assert.assertEquals("NoteOn",2, MidiEvent.fixedDataSize (0x90.toByte()).toInt())
        Assert.assertEquals("ProgramChange", 1, MidiEvent.fixedDataSize (0xC0.toByte()).toInt())
        Assert.assertEquals("CAf", 1, MidiEvent.fixedDataSize (0xD0.toByte()).toInt())
        Assert.assertEquals("PAf", 2, MidiEvent.fixedDataSize (0xA0.toByte()).toInt())
        Assert.assertEquals("SysEx", 0, MidiEvent.fixedDataSize (0xF0.toByte()).toInt())
        Assert.assertEquals("SongPositionPointer", 2, MidiEvent.fixedDataSize (0xF2.toByte()).toInt())
        Assert.assertEquals("SongSelect", 1, MidiEvent.fixedDataSize (0xF3.toByte()).toInt())
        Assert.assertEquals("MidiClock", 0, MidiEvent.fixedDataSize (0xF8.toByte()).toInt())
        Assert.assertEquals("META", 0, MidiEvent.fixedDataSize (0xFF.toByte()).toInt())
    }

    @Test
    fun midiEventConvert ()
    {
        var bytes1 = byteArrayOf (0xF8.toByte())
        var events1 = MidiEvent.convert (bytes1, 0, bytes1.size)
        Assert.assertEquals("bytes1 count", 1, events1.count())

        var bytes2 = byteArrayOf (0xFE.toByte())
        var events2 = MidiEvent.convert (bytes2, 0, bytes2.size)
        Assert.assertEquals("bytes2 count", 1, events2.count())
    }
}