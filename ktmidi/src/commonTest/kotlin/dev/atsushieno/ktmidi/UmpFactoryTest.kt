package dev.atsushieno.ktmidi

import kotlin.test.Test
import kotlin.test.assertEquals

// These tests are imported from cmidi2 project and have flipped assertEquals() arguments...
private fun <T> assertEqualsFlipped(actual: T, expected: T) = assertEquals(expected, actual)

class UmpFactoryTest {

    @Test
    fun testType0Messages() {
        /* type 0 */
        assertEqualsFlipped(UmpFactory.noop(0), 0)
        assertEqualsFlipped(UmpFactory.noop(1) ,  0x01000000)
        assertEqualsFlipped(UmpFactory.jrClock(0, 0) ,  0x00100000)
        assertEqualsFlipped(UmpFactory.jrClock(0, 1.0) ,  0x00107A12)
        assertEqualsFlipped(UmpFactory.jrTimestamp(0, 0) ,  0x00200000)
        assertEqualsFlipped(UmpFactory.jrTimestamp(1, 1.0) ,  0x01207A12)
    }

    @Test
    fun testType1Messages() {
        assertEqualsFlipped(UmpFactory.systemMessage(1, 0xF1.toByte(), 99, 0) ,  0x11F16300)
        assertEqualsFlipped(UmpFactory.systemMessage(1, 0xF2.toByte(), 99, 89) ,  0x11F26359)
        assertEqualsFlipped(UmpFactory.systemMessage(1, 0xFF.toByte(), 0, 0) ,  0x11FF0000)
    }

    @Test
    fun testType2Messages() {
        assertEqualsFlipped(UmpFactory.midi1Message(1, 0x80.toByte(), 2, 65, 10), 0x2182410A)
        assertEqualsFlipped(UmpFactory.midi1NoteOff(1, 2, 65, 10), 0x2182410A)
        assertEqualsFlipped(UmpFactory.midi1NoteOn(1, 2, 65, 10), 0x2192410A)
        assertEqualsFlipped(UmpFactory.midi1PAf(1, 2, 65, 10), 0x21A2410A)
        assertEqualsFlipped(UmpFactory.midi1CC(1, 2, 65, 10), 0x21B2410A)
        assertEqualsFlipped(UmpFactory.midi1Program(1, 2, 29), 0x21C21D00)
        assertEqualsFlipped(UmpFactory.midi1CAf(1, 2, 10), 0x21D20A00)
        assertEqualsFlipped(UmpFactory.midi1PitchBendDirect(1, 2, 0), 0x21E20000)
        assertEqualsFlipped(UmpFactory.midi1PitchBendDirect(1, 2, 1), 0x21E20100)
        assertEqualsFlipped(UmpFactory.midi1PitchBendDirect(1, 2, 0x3FFF), 0x21E27F7F)
        assertEqualsFlipped(UmpFactory.midi1PitchBend(1, 2, 0), 0x21E20040)
        assertEqualsFlipped(UmpFactory.midi1PitchBend(1, 2, -8192), 0x21E20000)
        assertEqualsFlipped(UmpFactory.midi1PitchBend(1, 2, 8191), 0x21E27F7F)
    }

    @Test
    fun testType3Messages() {
        val gsReset = listOf(0xF0, 0x41, 0x10, 0x42, 0x12, 0x40, 0x00, 0x7F, 0x00, 0x41, 0xF7).map { i -> i.toByte() }
        val sysex12 =
            listOf(0xF0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 0xF7).map { i -> i.toByte() } // 12 bytes without 0xF0
        val sysex13 = listOf(0xF0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 0xF7).map { i -> i.toByte() } // 13 bytes without 0xF0

        assertEqualsFlipped(UmpFactory.sysex7GetSysexLength(gsReset + 1), 9)
        assertEqualsFlipped(UmpFactory.sysex7GetSysexLength(gsReset), 9) // skip 0xF0
        assertEqualsFlipped(UmpFactory.sysex7GetSysexLength(sysex12 + 1), 12)
        assertEqualsFlipped(UmpFactory.sysex7GetSysexLength(sysex12), 12) // skip 0xF0
        assertEqualsFlipped(UmpFactory.sysex7GetSysexLength(sysex13 + 1), 13)
        assertEqualsFlipped(UmpFactory.sysex7GetSysexLength(sysex13), 13) // skip 0xF0

        assertEqualsFlipped(UmpFactory.sysex7GetPacketCount(0), 1)
        assertEqualsFlipped(UmpFactory.sysex7GetPacketCount(1), 1)
        assertEqualsFlipped(UmpFactory.sysex7GetPacketCount(7), 2)
        assertEqualsFlipped(UmpFactory.sysex7GetPacketCount(12), 2)
        assertEqualsFlipped(UmpFactory.sysex7GetPacketCount(13), 3)

        var v = UmpFactory.sysex7Direct(1, 0, 6, 0x41, 0x10, 0x42, 0x40, 0x00, 0x7F)
        assertEqualsFlipped(v, 0x310641104240007F)

        var length = UmpFactory.sysex7GetSysexLength(gsReset)
        assertEqualsFlipped(length, 9)
        v = UmpFactory.sysex7GetPacketOf(1, length, gsReset, 0)
        assertEqualsFlipped(v, 0x3116411042124000) // skip F0 correctly.
        v = UmpFactory.sysex7GetPacketOf(1, length, gsReset, 1)
        assertEqualsFlipped(v, 0x31337F0041000000)

        length = UmpFactory.sysex7GetSysexLength(sysex13)
        v = UmpFactory.sysex7GetPacketOf(1, length, sysex13, 0)
        assertEqualsFlipped(v, 0x3116000102030405) // status 1
        v = UmpFactory.sysex7GetPacketOf(1, length, sysex13, 1)
        assertEqualsFlipped(v, 0x3126060708090A0B) // status 2
        v = UmpFactory.sysex7GetPacketOf(1, length, sysex13, 2)
        assertEqualsFlipped(v, 0x31310C0000000000) // status 3
    }

    @Test
    fun testType4Messages() {
        var pitch = UmpFactory.pitch7_9Split(0x20, 0.5)
        assertEqualsFlipped(pitch, 0x4100)
        pitch = UmpFactory.pitch7_9(32.5)
        assertEqualsFlipped(pitch, 0x4100)

        var v = UmpFactory.midi2ChannelMessage8_8_16_16(
            1,
            MidiChannelStatus.NOTE_OFF,
            2,
            0x20,
            MidiAttributeType.Pitch7_9,
            0xFEDC,
            pitch
        )
        assertEqualsFlipped(v, 0x41822003FEDC4100)
        v = UmpFactory.midi2ChannelMessage8_8_32(1, MidiChannelStatus.NOTE_OFF, 2, 0x20, MidiAttributeType.Pitch7_9, 0x12345678)
        assertEqualsFlipped(v, 0x4182200312345678)

        v = UmpFactory.midi2NoteOff(1, 2, 64, 0, 0x1234, 0)
        assertEqualsFlipped(v, 0x4182400012340000)
        v = UmpFactory.midi2NoteOff(1, 2, 64, 3, 0x1234, pitch)
        assertEqualsFlipped(v, 0x4182400312344100)

        v = UmpFactory.midi2NoteOn(1, 2, 64, 0, 0xFEDC, 0)
        assertEqualsFlipped(v, 0x41924000FEDC0000)
        v = UmpFactory.midi2NoteOn(1, 2, 64, 3, 0xFEDC, pitch)
        assertEqualsFlipped(v, 0x41924003FEDC4100)

        v = UmpFactory.midi2PAf(1, 2, 64, 0x87654321)
        assertEqualsFlipped(v, 0x41A2400087654321)

        v = UmpFactory.midi2CC(1, 2, 1, 0x87654321)
        assertEqualsFlipped(v, 0x41B2010087654321)

        v = UmpFactory.midi2Program(1, 2, 1, 29, 8, 1)
        assertEqualsFlipped(v, 0x41C200011D000801)

        v = UmpFactory.midi2CAf(1, 2, 0x87654321)
        assertEqualsFlipped(v, 0x41D2000087654321)

        v = UmpFactory.midi2PitchBendDirect(1, 2, 0x87654321)
        assertEqualsFlipped(v, 0x41E2000087654321)

        v = UmpFactory.midi2PitchBend(1, 2, 1)
        assertEqualsFlipped(v, 0x41E2000080000001)

        v = UmpFactory.midi2PerNoteRCC(1, 2, 56, 0x10, 0x33333333)
        assertEqualsFlipped(v, 0x4102381033333333)

        v = UmpFactory.midi2PerNoteACC(1, 2, 56, 0x10, 0x33333333)
        assertEqualsFlipped(v, 0x4112381033333333)

        v = UmpFactory.midi2RPN(1, 2, 0x10, 0x20, 0x12345678)
        assertEqualsFlipped(v, 0x4122102012345678)

        v = UmpFactory.midi2NRPN(1, 2, 0x10, 0x20, 0x12345678)
        assertEqualsFlipped(v, 0x4132102012345678)

        v = UmpFactory.midi2RelativeRPN(1, 2, 0x10, 0x20, 0x12345678)
        assertEqualsFlipped(v, 0x4142102012345678)

        v = UmpFactory.midi2RelativeNRPN(1, 2, 0x10, 0x20, 0x12345678)
        assertEqualsFlipped(v, 0x4152102012345678)

        v = UmpFactory.midi2PerNotePitchBendDirect(1, 2, 56, 0x87654321)
        assertEqualsFlipped(v, 0x4162380087654321)

        v = UmpFactory.midi2PerNotePitchBend(1, 2, 56, 1)
        assertEqualsFlipped(v, 0x4162380080000001)

        v = UmpFactory.midi2PerNoteManagement(1, 2, 56, MidiPerNoteManagementFlags.DETACH)
        assertEqualsFlipped(v, 0x41F2380200000000)
    }

    @Test
    fun testType5Messages() {
        val gsReset = listOf(0x41, 0x10, 0x42, 0x12, 0x40, 0x00, 0x7F, 0x00, 0x41).map { i -> i.toByte() }
        val sysex27 = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27).map { i -> i.toByte() }

        assertEqualsFlipped(UmpFactory.sysex8GetPacketCount(0), 1)
        assertEqualsFlipped(UmpFactory.sysex8GetPacketCount(1), 1)
        assertEqualsFlipped(UmpFactory.sysex8GetPacketCount(13), 1)
        assertEqualsFlipped(UmpFactory.sysex8GetPacketCount(14), 2)
        assertEqualsFlipped(UmpFactory.sysex8GetPacketCount(26), 2)
        assertEqualsFlipped(UmpFactory.sysex8GetPacketCount(27), 3)

        var length = 9
        var pair = UmpFactory.sysex8GetPacketOf(1, 7, length, gsReset, 0)
        assertEqualsFlipped(pair.first, 0x510A074110421240)
        assertEqualsFlipped(pair.second, 0x007F004100000000)

        length = 27
        pair = UmpFactory.sysex8GetPacketOf(1, 7, length, sysex27, 0)
        assertEqualsFlipped(pair.first, 0x511E070102030405)
        assertEqualsFlipped(pair.second, 0x060708090A0B0C0D)
        pair = UmpFactory.sysex8GetPacketOf(1, 7, length, sysex27, 1)
        assertEqualsFlipped(pair.first, 0x512E070E0F101112)
        assertEqualsFlipped(pair.second, 0x131415161718191A)
        pair = UmpFactory.sysex8GetPacketOf(1, 7, length, sysex27, 2)
        assertEqualsFlipped(pair.first, 0x5132071B00000000)
        assertEqualsFlipped(pair.second, 0x0000000000000000L)
    }
}
