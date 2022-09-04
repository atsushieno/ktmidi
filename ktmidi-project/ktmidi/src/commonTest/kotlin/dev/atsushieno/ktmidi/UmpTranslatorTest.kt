package dev.atsushieno.ktmidi

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class UmpTranslatorTest {
    @Test
    fun testConvertSingleUmpToMidi1 ()
    {
        var ump = Ump(0L)
        val dst = MutableList<Byte>(16) { 0 }
        var size = 0

        // MIDI1 Channel Voice Messages

        ump = Ump(UmpFactory.midi1NoteOff(0, 1, 40, 0x70))
        size = UmpTranslator.translateSingleUmpToMidi1Bytes(dst, ump)
        assertEquals(3,  size)
        assertContentEquals(listOf(0x81, 40, 0x70).map { it.toByte() }, dst.take(size))

        ump = Ump(UmpFactory.midi1Program(0, 1, 40))
        size = UmpTranslator.translateSingleUmpToMidi1Bytes(dst, ump)
        assertEquals(2,  size)
        assertContentEquals(listOf(0xC1, 40).map { it.toByte() }, dst.take(size))

        // MIDI2 Channel Voice Messages

        // rpn
        ump = Ump(UmpFactory.midi2RPN(0, 1, 2, 3, 517 * 0x40000)) // MIDI1 DTE 517, expanded to 32bit
        size = UmpTranslator.translateSingleUmpToMidi1Bytes(dst, ump)
        assertEquals(12,  size)
        // 4 = 517 / 0x80, 5 = 517 % 0x80
        assertContentEquals(listOf(0xB1, 101, 0x2, 0xB1, 100, 0x3, 0xB1, 6, 4, 0xB1, 38, 5).map { it.toByte() }, dst.take(size))

        // nrpn
        ump = Ump(UmpFactory.midi2NRPN(0, 1, 2, 3, 0xFF000000))
        size = UmpTranslator.translateSingleUmpToMidi1Bytes(dst, ump)
        assertEquals(12,  size)
        assertContentEquals(listOf(0xB1, 99, 0x2, 0xB1, 98, 0x3, 0xB1, 6, 0x7F, 0xB1, 38, 0x40).map { it.toByte() }, dst.take(size))

        // note off
        ump = Ump(UmpFactory.midi2NoteOff(0, 1, 40, 0, 0xE800, 0))
        size = UmpTranslator.translateSingleUmpToMidi1Bytes(dst, ump)
        assertEquals(3,  size)
        assertContentEquals(listOf(0x81, 40, 0x74).map { it.toByte() }, dst.take(size))

        // note on
        ump = Ump(UmpFactory.midi2NoteOn(0, 1, 40, 0, 0xE800, 0))
        size = UmpTranslator.translateSingleUmpToMidi1Bytes(dst, ump)
        assertEquals(3,  size)
        assertContentEquals(listOf(0x91, 40, 0x74).map { it.toByte() }, dst.take(size))

        // PAf
        ump = Ump(UmpFactory.midi2PAf(0, 1, 40, 0xE8000000))
        size = UmpTranslator.translateSingleUmpToMidi1Bytes(dst, ump)
        assertEquals(3,  size)
        assertContentEquals(listOf(0xA1, 40, 0x74).map { it.toByte() }, dst.take(size))

        // CC
        ump = Ump(UmpFactory.midi2CC(0, 1, 10, 0xE8000000))
        size = UmpTranslator.translateSingleUmpToMidi1Bytes(dst, ump)
        assertEquals(3,  size)
        assertContentEquals(listOf(0xB1, 10, 0x74).map { it.toByte() }, dst.take(size))

        // program change, without bank options
        ump = Ump(UmpFactory.midi2Program(0, 1, 0, 8, 16, 24))
        size = UmpTranslator.translateSingleUmpToMidi1Bytes(dst, ump)
        assertEquals(2,  size)
        assertContentEquals(listOf(0xC1, 8).map { it.toByte() }, dst.take(size))

        // program change, with bank options
        ump = Ump(UmpFactory.midi2Program(0, 1, 1, 8, 16, 24))
        size = UmpTranslator.translateSingleUmpToMidi1Bytes(dst, ump)
        assertEquals(8,  size)
        assertContentEquals(listOf(0xB1, 0, 16, 0xB1, 32, 24, 0xC1, 8).map { it.toByte() }, dst.take(size))

        // CAf
        ump = Ump(UmpFactory.midi2CAf(0, 1, 0xE8000000))
        size = UmpTranslator.translateSingleUmpToMidi1Bytes(dst, ump)
        assertEquals(2,  size)
        assertContentEquals(listOf(0xD1, 0x74).map { it.toByte() }, dst.take(size))

        // PitchBend
        ump = Ump(UmpFactory.midi2PitchBendDirect(0, 1, 0xE8040000))
        size = UmpTranslator.translateSingleUmpToMidi1Bytes(dst, ump)
        assertEquals(3,  size)
        assertContentEquals(listOf(0xE1, 1, 0x74).map { it.toByte() }, dst.take(size))
    }

    @Test
    fun testConvertUmpToMidi1Bytes1() {
        val umps = listOf(
            Ump(0x40904000E0000000),
            Ump(0x00201000),
            Ump(0x4080400000000000),
        )
        val dst = MutableList<Byte>(9) { 0 }
        assertEquals(UmpTranslationResult.OK, UmpTranslator.translateUmpToMidi1Bytes(dst, umps.asSequence()))
        val expected = listOf(0, 0x90, 0x40, 0x70, 0x80, 0x20, 0x80, 0x40, 0).map { it.toByte() }
        assertContentEquals(expected, dst)
    }


    @Test
    fun testConvertMidi1ToUmpNoteOn()
    {
        val bytes = listOf(0x91, 0x40, 0x78)
        val context = Midi1ToUmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

        assertEquals(UmpTranslationResult.OK, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(3, context.midi1Pos)
        assertEquals(1, context.output.size)
        assertEquals(0x47914000, context.output[0].int1)
        assertEquals(0xF0000000u, context.output[0].int2.toUInt())
    }

    @Test
    fun  testConvertMidi1ToUmpPAf()
    {
        val bytes = listOf(0xA1, 0x40, 0x60)
        val context = Midi1ToUmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

        // PAf
        assertEquals(UmpTranslationResult.OK, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(3, context.midi1Pos)
        assertEquals(1, context.output.size)
        assertEquals(0x47A14000, context.output[0].int1)
        assertEquals(0xC0000000u, context.output[0].int2.toUInt())
    }

    @Test
    fun  testConvertMidi1ToUmpSimpleCC()
    {
        val bytes = listOf(0xB1, 0x07, 0x70)
        val context = Midi1ToUmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

        // PAf
        assertEquals(UmpTranslationResult.OK, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(3, context.midi1Pos)
        assertEquals(1, context.output.size)
        assertEquals(0x47B10700, context.output[0].int1)
        assertEquals(0xE0000000u, context.output[0].int2.toUInt())
    }

    @Test
    fun  testConvertMidi1ToUmpValidRPN()
    {
        val bytes = listOf(0xB1, 101, 1, 0xB1, 100, 2, 0xB1, 6, 0x10, 0xB1, 38, 0x20)
        val context = Midi1ToUmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

        // RPN
        assertEquals(UmpTranslationResult.OK, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(12, context.midi1Pos)
        assertEquals(1, context.output.size)
        assertEquals(0x47210102, context.output[0].int1)
        assertEquals(0x20800000u, context.output[0].int2.toUInt())
    }

    @Test
    fun  testConvertMidi1ToUmpValidNRPN()
    {
        val bytes = listOf(0xB1, 99, 1, 0xB1, 98, 2, 0xB1, 6, 0x10, 0xB1, 38, 0x20)
        val context = Midi1ToUmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

        // NRPN
        assertEquals(UmpTranslationResult.OK, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(12, context.midi1Pos)
        assertEquals(1, context.output.size)
        assertEquals(0x47310102, context.output[0].int1)
        assertEquals(0x20800000u, context.output[0].int2.toUInt())
    }

    @Test
    fun  testConvertMidi1ToUmpInvalidRPN()
    {
        var bytes = listOf(0xB1, 101, 1)
        var context = Midi1ToUmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

        // only RPN MSB -> error
        assertEquals(UmpTranslationResult.INVALID_DTE_SEQUENCE, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(3, context.midi1Pos)
        assertEquals(0, context.output.size)

        // only RPN MSB and LSB -> error
        bytes = listOf(0xB1, 101, 1, 0xB1, 100, 2)
        context = Midi1ToUmpTranslatorContext(bytes.map { it.toByte()}, group = 7)
        assertEquals(UmpTranslationResult.INVALID_DTE_SEQUENCE, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(6, context.midi1Pos)
        assertEquals(0, context.output.size)

        // only RPN MSB and LSB, and DTE MSB -> error
        bytes = listOf(0xB1, 101, 1, 0xB1, 100, 2, 0xB1, 6, 3)
        context = Midi1ToUmpTranslatorContext(bytes.map { it.toByte()}, group = 7)
        assertEquals(UmpTranslationResult.INVALID_DTE_SEQUENCE, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(9, context.midi1Pos)
        assertEquals(0, context.output.size)
    }


    @Test
    fun  testConvertMidi1ToUmpInvalidNRPN()
    {
        var bytes = listOf(0xB1, 99, 1)
        var context = Midi1ToUmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

        // only NRPN MSB -> error
        assertEquals(UmpTranslationResult.INVALID_DTE_SEQUENCE, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(3, context.midi1Pos)
        assertEquals(0, context.output.size)

        // only NRPN MSB and LSB -> error
        bytes = listOf(0xB1, 99, 1, 0xB1, 98, 2)
        context = Midi1ToUmpTranslatorContext(bytes.map { it.toByte()}, group = 7)
        assertEquals(UmpTranslationResult.INVALID_DTE_SEQUENCE, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(6, context.midi1Pos)
        assertEquals(0, context.output.size)

        // only NRPN MSB and LSB, and DTE MSB -> error
        bytes = listOf(0xB1, 99, 1, 0xB1, 98, 2, 0xB1, 6, 3)
        context = Midi1ToUmpTranslatorContext(bytes.map { it.toByte()}, group = 7)
        assertEquals(UmpTranslationResult.INVALID_DTE_SEQUENCE, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(9, context.midi1Pos)
        assertEquals(0, context.output.size)
    }

    @Test
    fun  testConvertMidi1ToUmpSimpleProgramChange()
    {
        var bytes = listOf(0xC1, 0x30)
        var context = Midi1ToUmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

        // simple program change
        assertEquals(UmpTranslationResult.OK, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(2, context.midi1Pos)
        assertEquals(1, context.output.size)
        assertEquals(0x47C10000, context.output[0].int1)
        assertEquals(0x30000000u, context.output[0].int2.toUInt())
    }

    @Test
    fun  testConvertMidi1ToUmpBankMsbLsbAndProgramChange()
    {
        var bytes = listOf(0xB1, 0x00, 0x12, 0xB1, 0x20, 0x22, 0xC1, 0x30)
        var context = Midi1ToUmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

        // bank select MSB, bank select LSB, program change
        assertEquals(UmpTranslationResult.OK, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(8, context.midi1Pos)
        assertEquals(1, context.output.size)
        assertEquals(0x47C10001, context.output[0].int1)
        assertEquals(0x30001222u, context.output[0].int2.toUInt())
    }

    // Not sure if this should be actually accepted or rejected; we accept it for now.
    @Test
    fun  testConvertMidi1ToUmpBankMsbAndProgramChange()
    {
        var bytes = listOf(0xB1, 0x00, 0x12, 0xC1, 0x30)
        var context = Midi1ToUmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

        // bank select MSB, then program change (LSB skipped)
        assertEquals(UmpTranslationResult.OK, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(5, context.midi1Pos)
        assertEquals(1, context.output.size)
        assertEquals(0x47C10001, context.output[0].int1)
        assertEquals(0x30001200u, context.output[0].int2.toUInt())
    }

    // Not sure if this should be actually accepted or rejected; we accept it for now.
    @Test
    fun  testConvertMidi1ToUmpBankLsbAndProgramChange()
    {
        val bytes = listOf(0xB1, 0x20, 0x12, 0xC1, 0x30)
        val context = Midi1ToUmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

        // bank select LSB, then program change (MSB skipped)
        assertEquals(UmpTranslationResult.OK, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(5, context.midi1Pos)
        assertEquals(1, context.output.size)
        assertEquals(0x47C10001, context.output[0].int1)
        assertEquals(0x30000012u, context.output[0].int2.toUInt())
    }

    @Test
    fun  testConvertMidi1ToUmpCAf()
    {
        val bytes = listOf(0xD1, 0x60)
        val context = Midi1ToUmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

        // CAf
        assertEquals(UmpTranslationResult.OK, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(2, context.midi1Pos)
        assertEquals(1, context.output.size)
        assertEquals(0x47D10000, context.output[0].int1)
        assertEquals(0xC0000000u, context.output[0].int2.toUInt())
    }

    @Test
    fun  testConvertMidi1ToUmpPitchBend()
    {
        val bytes = listOf(0xE1, 0x20, 0x30)
        val context = Midi1ToUmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

        // Pitchbend
        assertEquals(UmpTranslationResult.OK, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(3, context.midi1Pos)
        assertEquals(1, context.output.size)
        assertEquals(0x47E10000, context.output[0].int1)
        assertEquals(0x60800000u, context.output[0].int2.toUInt()) // note that source MIDI1 pitch bend is in littele endian.
    }
}
