package dev.atsushieno.ktmidi

import dev.atsushieno.ktmidi.umpfactory.*
import kotlin.test.Test
import kotlin.test.assertEquals

// These tests are imported from cmidi2 project and have flipped assertEquals() arguments...
private fun <T> assertEqualsFlipped(actual: T, expected: T) = assertEquals(expected, actual)

class UmpFactoryTest {

    @Test
    fun testType0Messages() {
        /* type 0 */
        assertEqualsFlipped(umpNOOP(0), 0)
        assertEqualsFlipped(umpNOOP(1) ,  0x01000000)
        assertEqualsFlipped(umpJRClock(0, 0) ,  0x00100000)
        assertEqualsFlipped(umpJRClock(0, 1.0) ,  0x00107A12)
        assertEqualsFlipped(umpJRTimestamp(0, 0) ,  0x00200000)
        assertEqualsFlipped(umpJRTimestamp(1, 1.0) ,  0x01207A12)
    }

    @Test
    fun testType1Messages() {
        assertEqualsFlipped(umpSystemMessage(1, 0xF1.toByte(), 99, 0) ,  0x11F16300)
        assertEqualsFlipped(umpSystemMessage(1, 0xF2.toByte(), 99, 89) ,  0x11F26359)
        assertEqualsFlipped(umpSystemMessage(1, 0xFF.toByte(), 0, 0) ,  0x11FF0000)
    }

    @Test
    fun testType2Messages() {
        assertEqualsFlipped(umpMidi1Message(1, 0x80.toByte(), 2, 65, 10), 0x2182410A)
        assertEqualsFlipped(umpMidi1NoteOff(1, 2, 65, 10), 0x2182410A)
        assertEqualsFlipped(umpMidi1NoteOn(1, 2, 65, 10), 0x2192410A)
        assertEqualsFlipped(umpMidi1PAf(1, 2, 65, 10), 0x21A2410A)
        assertEqualsFlipped(umpMidi1CC(1, 2, 65, 10), 0x21B2410A)
        assertEqualsFlipped(umpMidi1Program(1, 2, 29), 0x21C21D00)
        assertEqualsFlipped(umpMidi1CAf(1, 2, 10), 0x21D20A00)
        assertEqualsFlipped(umpMidi1PitchBendDirect(1, 2, 0), 0x21E20000)
        assertEqualsFlipped(umpMidi1PitchBendDirect(1, 2, 1), 0x21E20100)
        assertEqualsFlipped(umpMidi1PitchBendDirect(1, 2, 0x3FFF), 0x21E27F7F)
        assertEqualsFlipped(umpMidi1PitchBend(1, 2, 0), 0x21E20040)
        assertEqualsFlipped(umpMidi1PitchBend(1, 2, -8192), 0x21E20000)
        assertEqualsFlipped(umpMidi1PitchBend(1, 2, 8191), 0x21E27F7F)
    }

    @Test
    fun testType3Messages() {
        val gsReset = listOf(0xF0, 0x41, 0x10, 0x42, 0x12, 0x40, 0x00, 0x7F, 0x00, 0x41, 0xF7).map { i -> i.toByte() }
        val sysex12 =
            listOf(0xF0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 0xF7).map { i -> i.toByte() } // 12 bytes without 0xF0
        val sysex13 = listOf(0xF0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 0xF7).map { i -> i.toByte() } // 13 bytes without 0xF0

        assertEqualsFlipped(umpSysex7GetSysexLength(gsReset + 1), 9)
        assertEqualsFlipped(umpSysex7GetSysexLength(gsReset), 9) // skip 0xF0
        assertEqualsFlipped(umpSysex7GetSysexLength(sysex12 + 1), 12)
        assertEqualsFlipped(umpSysex7GetSysexLength(sysex12), 12) // skip 0xF0
        assertEqualsFlipped(umpSysex7GetSysexLength(sysex13 + 1), 13)
        assertEqualsFlipped(umpSysex7GetSysexLength(sysex13), 13) // skip 0xF0

        assertEqualsFlipped(umpSysex7GetPacketCount(0), 1)
        assertEqualsFlipped(umpSysex7GetPacketCount(1), 1)
        assertEqualsFlipped(umpSysex7GetPacketCount(7), 2)
        assertEqualsFlipped(umpSysex7GetPacketCount(12), 2)
        assertEqualsFlipped(umpSysex7GetPacketCount(13), 3)

        var v = umpSysex7Direct(1, 0, 6, 0x41, 0x10, 0x42, 0x40, 0x00, 0x7F)
        assertEqualsFlipped(v, 0x310641104240007F)

        var length = umpSysex7GetSysexLength(gsReset)
        assertEqualsFlipped(length, 9)
        v = umpSysex7GetPacketOf(1, length, gsReset, 0)
        assertEqualsFlipped(v, 0x3116411042124000) // skip F0 correctly.
        v = umpSysex7GetPacketOf(1, length, gsReset, 1)
        assertEqualsFlipped(v, 0x31337F0041000000)

        length = umpSysex7GetSysexLength(sysex13)
        v = umpSysex7GetPacketOf(1, length, sysex13, 0)
        assertEqualsFlipped(v, 0x3116000102030405) // status 1
        v = umpSysex7GetPacketOf(1, length, sysex13, 1)
        assertEqualsFlipped(v, 0x3126060708090A0B) // status 2
        v = umpSysex7GetPacketOf(1, length, sysex13, 2)
        assertEqualsFlipped(v, 0x31310C0000000000) // status 3
    }

    @Test
    fun testType4Messages() {
        var pitch = umpPitch7_9Split(0x20, 0.5)
        assertEqualsFlipped(pitch, 0x4100)
        pitch = umpPitch7_9(32.5)
        assertEqualsFlipped(pitch, 0x4100)

        var v = umpMidi2ChannelMessage8_8_16_16(
            1,
            MidiEventType.NOTE_OFF,
            2,
            0x20,
            MidiAttributeType.Pitch7_9,
            0xFEDC,
            pitch
        )
        assertEqualsFlipped(v, 0x41822003FEDC4100)
        v = umpMidi2ChannelMessage8_8_32(1, MidiEventType.NOTE_OFF, 2, 0x20, MidiAttributeType.Pitch7_9, 0x12345678)
        assertEqualsFlipped(v, 0x4182200312345678)

        v = umpMidi2NoteOff(1, 2, 64, 0, 0x1234, 0)
        assertEqualsFlipped(v, 0x4182400012340000)
        v = umpMidi2NoteOff(1, 2, 64, 3, 0x1234, pitch)
        assertEqualsFlipped(v, 0x4182400312344100)

        v = umpMidi2NoteOn(1, 2, 64, 0, 0xFEDC, 0)
        assertEqualsFlipped(v, 0x41924000FEDC0000)
        v = umpMidi2NoteOn(1, 2, 64, 3, 0xFEDC, pitch)
        assertEqualsFlipped(v, 0x41924003FEDC4100)

        v = umpMidi2PAf(1, 2, 64, 0x87654321)
        assertEqualsFlipped(v, 0x41A2400087654321)

        v = umpMidi2CC(1, 2, 1, 0x87654321)
        assertEqualsFlipped(v, 0x41B2010087654321)

        v = umpMidi2Program(1, 2, 1, 29, 8, 1)
        assertEqualsFlipped(v, 0x41C200011D000801)

        v = umpMidi2CAf(1, 2, 0x87654321)
        assertEqualsFlipped(v, 0x41D2000087654321)

        v = umpMidi2PitchBendDirect(1, 2, 0x87654321)
        assertEqualsFlipped(v, 0x41E2000087654321)

        v = umpMidi2PitchBend(1, 2, 1)
        assertEqualsFlipped(v, 0x41E2000080000001)

        v = umpMidi2PerNoteRCC(1, 2, 56, 0x10, 0x33333333)
        assertEqualsFlipped(v, 0x4102381033333333)

        v = umpMidi2PerNoteACC(1, 2, 56, 0x10, 0x33333333)
        assertEqualsFlipped(v, 0x4112381033333333)

        v = umpMidi2RPN(1, 2, 0x10, 0x20, 0x12345678)
        assertEqualsFlipped(v, 0x4122102012345678)

        v = umpMidi2NRPN(1, 2, 0x10, 0x20, 0x12345678)
        assertEqualsFlipped(v, 0x4132102012345678)

        v = umpMidi2RelativeRPN(1, 2, 0x10, 0x20, 0x12345678)
        assertEqualsFlipped(v, 0x4142102012345678)

        v = umpMidi2RelativeNRPN(1, 2, 0x10, 0x20, 0x12345678)
        assertEqualsFlipped(v, 0x4152102012345678)

        v = umpMidi2PerNotePitchBendDirect(1, 2, 56, 0x87654321)
        assertEqualsFlipped(v, 0x4162380087654321)

        v = umpMidi2PerNotePitchBend(1, 2, 56, 1)
        assertEqualsFlipped(v, 0x4162380080000001)

        v = umpMidi2PerNoteManagement(1, 2, 56, MidiPerNoteManagementFlags.DETACH)
        assertEqualsFlipped(v, 0x41F2380200000000)
    }

    @Test
    fun testType5Messages() {
        val gsReset = listOf(0x41, 0x10, 0x42, 0x12, 0x40, 0x00, 0x7F, 0x00, 0x41).map { i -> i.toByte() }
        val sysex27 = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27).map { i -> i.toByte() }

        assertEqualsFlipped(umpSysex8GetPacketCount(0), 1)
        assertEqualsFlipped(umpSysex8GetPacketCount(1), 1)
        assertEqualsFlipped(umpSysex8GetPacketCount(13), 1)
        assertEqualsFlipped(umpSysex8GetPacketCount(14), 2)
        assertEqualsFlipped(umpSysex8GetPacketCount(26), 2)
        assertEqualsFlipped(umpSysex8GetPacketCount(27), 3)

        var length = 9
        var pair = umpSysex8GetPacketOf(1, 7, length, gsReset, 0)
        assertEqualsFlipped(pair.first, 0x510A074110421240)
        assertEqualsFlipped(pair.second, 0x007F004100000000)

        length = 27
        pair = umpSysex8GetPacketOf(1, 7, length, sysex27, 0)
        assertEqualsFlipped(pair.first, 0x511E070102030405)
        assertEqualsFlipped(pair.second, 0x060708090A0B0C0D)
        pair = umpSysex8GetPacketOf(1, 7, length, sysex27, 1)
        assertEqualsFlipped(pair.first, 0x512E070E0F101112)
        assertEqualsFlipped(pair.second, 0x131415161718191A)
        pair = umpSysex8GetPacketOf(1, 7, length, sysex27, 2)
        assertEqualsFlipped(pair.first, 0x5132071B00000000)
        assertEqualsFlipped(pair.second, 0x0000000000000000L)
    }
}
