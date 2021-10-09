package dev.atsushieno.ktmidi

import io.ktor.utils.io.core.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
        val sysex27 = (1..27).map { i -> i.toByte() }

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

    @Test
    fun sysex7Process() {
        val sysex6 = (1..6).map { i -> i.toByte() }
        val pl = mutableListOf<Ump>()
        UmpFactory.sysex7Process(0, sysex6) {l, _ -> pl.add(Ump(l)) }
        assertEquals(1, pl.size, "pl size")
        assertEquals(0x30060102, pl[0].int1, "v1.1")
        assertEquals(0x03040506, pl[0].int2, "v1.2")
        val bytes1 = pl[0].toPlatformBytes(ByteOrder.LITTLE_ENDIAN)
        assertContentEquals(arrayOf(2, 1, 6, 0x30, 6, 5, 4, 3), bytes1.toTypedArray(), "bytes1.1")
        assertContentEquals(sysex6, UmpRetriever.getSysex7Data(pl.iterator()), "getSysex7:6")

        val sysex7 = (1..7).map { i -> i.toByte() }
        pl.clear()
        UmpFactory.sysex7Process(0, sysex7) {l, _ -> pl.add(Ump(l)) }
        assertEquals(2, pl.size, "pl size2")
        assertEquals(0x30160102, pl[0].int1, "v2.1")
        assertEquals(0x03040506, pl[0].int2, "v2.2")
        assertEquals(0x30310700, pl[1].int1, "v2.3")
        assertEquals(0, pl[1].int2, "v2.4")
        val bytes2_1 = pl[0].toPlatformBytes(ByteOrder.LITTLE_ENDIAN)
        val bytes2_2 = pl[1].toPlatformBytes(ByteOrder.LITTLE_ENDIAN)
        assertContentEquals(arrayOf(2, 1, 0x16, 0x30, 6, 5, 4, 3), bytes2_1.toTypedArray(), "bytes2.1")
        assertContentEquals(arrayOf(0, 7, 0x31, 0x30, 0, 0, 0, 0), bytes2_2.toTypedArray(), "bytes2.2")
        assertContentEquals(sysex7, UmpRetriever.getSysex7Data(pl.iterator()), "getSysex7:7")

        val sysex12 = (1..12).map { i -> i.toByte() }
        pl.clear()
        UmpFactory.sysex7Process(0, sysex12) {l, _ -> pl.add(Ump(l)) }
        assertEquals(2, pl.size, "pl size3")
        assertEquals(0x30160102, pl[0].int1, "v3.1")
        assertEquals(0x03040506, pl[0].int2, "v3.2")
        assertEquals(0x30360708, pl[1].int1, "v3.3")
        assertEquals(0x090A0B0C, pl[1].int2, "v3.4")
        val bytes3_1 = pl[0].toPlatformBytes(ByteOrder.LITTLE_ENDIAN)
        val bytes3_2 = pl[1].toPlatformBytes(ByteOrder.LITTLE_ENDIAN)
        assertContentEquals(arrayOf(2, 1, 0x16, 0x30, 6, 5, 4, 3), bytes3_1.toTypedArray(), "bytes3.1")
        assertContentEquals(arrayOf(8, 7, 0x36, 0x30, 12, 11, 10, 9), bytes3_2.toTypedArray(), "bytes3.2")
        assertContentEquals(sysex12, UmpRetriever.getSysex7Data(pl.iterator()), "getSysex7:12")

        val sysex13 = (1..13).map { i -> i.toByte() }
        pl.clear()
        UmpFactory.sysex7Process(0, sysex13) {l, _ -> pl.add(Ump(l)) }
        assertEquals(3, pl.size, "pl size4")
        assertEquals(0x30160102, pl[0].int1, "v4.1")
        assertEquals(0x03040506, pl[0].int2, "v4.2")
        assertEquals(0x30260708, pl[1].int1, "v4.3")
        assertEquals(0x090A0B0C, pl[1].int2, "v4.4")
        assertEquals(0x30310D00, pl[2].int1, "v4.3")
        assertEquals(0, pl[2].int2, "v4.4")
        val bytes4_1 = pl[0].toPlatformBytes(ByteOrder.LITTLE_ENDIAN)
        val bytes4_2 = pl[1].toPlatformBytes(ByteOrder.LITTLE_ENDIAN)
        val bytes4_3 = pl[2].toPlatformBytes(ByteOrder.LITTLE_ENDIAN)
        assertContentEquals(arrayOf(2, 1, 0x16, 0x30, 6, 5, 4, 3), bytes4_1.toTypedArray(), "bytes4.1")
        assertContentEquals(arrayOf(8, 7, 0x26, 0x30, 12, 11, 10, 9), bytes4_2.toTypedArray(), "bytes4.2")
        assertContentEquals(arrayOf(0, 13, 0x31, 0x30, 0, 0, 0, 0), bytes4_3.toTypedArray(), "bytes4.3")
        assertContentEquals(sysex13, UmpRetriever.getSysex7Data(pl.iterator()), "getSysex7:13")
    }

    @Test
    fun sysex8Process() {
        val sysex1 = listOf(1).map { i -> i.toByte() }
        val pl = mutableListOf<Ump>()
        UmpFactory.sysex8Process(0, sysex1) {l1, l2, _ -> pl.add(Ump(l1, l2)) }
        assertEquals(1, pl.size, "pl size")
        assertEquals(0x50020001, pl[0].int1, "v1.1")
        assertEquals(0, pl[0].int2, "v1.2")
        val bytes1 = pl[0].toPlatformBytes(ByteOrder.LITTLE_ENDIAN)
        assertContentEquals(arrayOf(1, 0, 2, 0x50, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), bytes1.toTypedArray(), "bytes1.1")

        val sysex13 = (1..13).map { i -> i.toByte() }
        pl.clear()
        UmpFactory.sysex8Process(0, sysex13) { l1, l2, _ -> pl.add(Ump(l1, l2)) }
        assertEquals(1, pl.size, "pl size13")
        assertEquals(0x500E0001, pl[0].int1, "v13.1")
        assertEquals(0x02030405, pl[0].int2, "v13.2")
        assertEquals(0x06070809, pl[0].int3, "v13.3")
        assertEquals(0x0A0B0C0D, pl[0].int4, "v13.4")
        val bytes13 = pl[0].toPlatformBytes(ByteOrder.LITTLE_ENDIAN)
        assertContentEquals(arrayOf(1, 0, 14, 0x50, 5, 4, 3, 2, 9, 8, 7, 6, 13, 12, 11, 10), bytes13.toTypedArray(), "bytes1.1")

        val sysex14 = (1..14).map { i -> i.toByte() }
        pl.clear()
        UmpFactory.sysex8Process(0, sysex14) { l1, l2, _ -> pl.add(Ump(l1, l2)) }
        assertEquals(2, pl.size, "pl size14")
        assertEquals(0x501E0001, pl[0].int1, "v14.1")
        assertEquals(0x02030405, pl[0].int2, "v14.2")
        assertEquals(0x06070809, pl[0].int3, "v14.3")
        assertEquals(0x0A0B0C0D, pl[0].int4, "v14.4")
        assertEquals(0x5032000E, pl[1].int1, "v14.5")
        assertEquals(0, pl[1].int2, "v14.6")
        assertEquals(0, pl[1].int3, "v14.7")
        assertEquals(0, pl[1].int4, "v14.8")
        val bytes14_1 = pl[0].toPlatformBytes(ByteOrder.LITTLE_ENDIAN)
        val bytes14_2 = pl[1].toPlatformBytes(ByteOrder.LITTLE_ENDIAN)
        assertContentEquals(arrayOf(1, 0, 0x1E, 0x50, 5, 4, 3, 2, 9, 8, 7, 6, 13, 12, 11, 10), bytes14_1.toTypedArray(), "bytes14.1")
        assertContentEquals(arrayOf(14, 0, 0x32, 0x50, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), bytes14_2.toTypedArray(), "bytes14.2")

        val sysex26 = (1..26).map { i -> i.toByte() }
        pl.clear()
        UmpFactory.sysex8Process(0, sysex26) { l1, l2, _ -> pl.add(Ump(l1, l2)) }
        assertEquals(2, pl.size, "pl size26")
        assertEquals(0x501E0001, pl[0].int1, "v26.1")
        assertEquals(0x503E000E, pl[1].int1, "v26.2")
        val bytes26_1 = pl[0].toPlatformBytes(ByteOrder.LITTLE_ENDIAN)
        val bytes26_2 = pl[1].toPlatformBytes(ByteOrder.LITTLE_ENDIAN)
        assertContentEquals(arrayOf(1, 0, 0x1E, 0x50, 5, 4, 3, 2, 9, 8, 7, 6, 13, 12, 11, 10), bytes26_1.toTypedArray(), "bytes26.1")
        assertContentEquals(arrayOf(14, 0, 0x3E, 0x50, 18, 17, 16, 15, 22, 21, 20, 19, 26, 25, 24, 23), bytes26_2.toTypedArray(), "bytes26.2")


        val sysex27 = (1..27).map { i -> i.toByte() }
        pl.clear()
        UmpFactory.sysex8Process(0, sysex27) { l1, l2, _ -> pl.add(Ump(l1, l2)) }
        assertEquals(3, pl.size, "pl size27")
        assertEquals(0x501E0001, pl[0].int1, "v27.1")
        assertEquals(0x502E000E, pl[1].int1, "v27.2")
        assertEquals(0x5032001B, pl[2].int1, "v27.3")
        val bytes27_1 = pl[0].toPlatformBytes(ByteOrder.LITTLE_ENDIAN)
        val bytes27_2 = pl[1].toPlatformBytes(ByteOrder.LITTLE_ENDIAN)
        val bytes27_3 = pl[2].toPlatformBytes(ByteOrder.LITTLE_ENDIAN)
        assertContentEquals(arrayOf(1, 0, 0x1E, 0x50, 5, 4, 3, 2, 9, 8, 7, 6, 13, 12, 11, 10), bytes27_1.toTypedArray(), "bytes26.1")
        assertContentEquals(arrayOf(14, 0, 0x2E, 0x50, 18, 17, 16, 15, 22, 21, 20, 19, 26, 25, 24, 23), bytes27_2.toTypedArray(), "bytes26.2")
        assertContentEquals(arrayOf(27, 0, 0x32, 0x50, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), bytes27_3.toTypedArray(), "bytes26.3")
    }
}
