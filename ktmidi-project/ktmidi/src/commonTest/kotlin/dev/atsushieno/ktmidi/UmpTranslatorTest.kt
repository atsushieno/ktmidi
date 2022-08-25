package dev.atsushieno.ktmidi

import kotlin.test.Test
import kotlin.test.assertEquals

class UmpTranslatorTest {
    @Test
    fun testConvertMidi1ToUmpNoteOn()
    {
        val bytes = listOf(0x91, 0x40, 0x78)
        val context = UmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

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
        val context = UmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

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
        val context = UmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

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
        val context = UmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

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
        val context = UmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

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
        var context = UmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

        // only RPN MSB -> error
        assertEquals(UmpTranslationResult.INVALID_DTE_SEQUENCE, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(3, context.midi1Pos)
        assertEquals(0, context.output.size)

        // only RPN MSB and LSB -> error
        bytes = listOf(0xB1, 101, 1, 0xB1, 100, 2)
        context = UmpTranslatorContext(bytes.map { it.toByte()}, group = 7)
        assertEquals(UmpTranslationResult.INVALID_DTE_SEQUENCE, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(6, context.midi1Pos)
        assertEquals(0, context.output.size)

        // only RPN MSB and LSB, and DTE MSB -> error
        bytes = listOf(0xB1, 101, 1, 0xB1, 100, 2, 0xB1, 6, 3)
        context = UmpTranslatorContext(bytes.map { it.toByte()}, group = 7)
        assertEquals(UmpTranslationResult.INVALID_DTE_SEQUENCE, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(9, context.midi1Pos)
        assertEquals(0, context.output.size)
    }


    @Test
    fun  testConvertMidi1ToUmpInvalidNRPN()
    {
        var bytes = listOf(0xB1, 99, 1)
        var context = UmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

        // only NRPN MSB -> error
        assertEquals(UmpTranslationResult.INVALID_DTE_SEQUENCE, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(3, context.midi1Pos)
        assertEquals(0, context.output.size)

        // only NRPN MSB and LSB -> error
        bytes = listOf(0xB1, 99, 1, 0xB1, 98, 2)
        context = UmpTranslatorContext(bytes.map { it.toByte()}, group = 7)
        assertEquals(UmpTranslationResult.INVALID_DTE_SEQUENCE, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(6, context.midi1Pos)
        assertEquals(0, context.output.size)

        // only NRPN MSB and LSB, and DTE MSB -> error
        bytes = listOf(0xB1, 99, 1, 0xB1, 98, 2, 0xB1, 6, 3)
        context = UmpTranslatorContext(bytes.map { it.toByte()}, group = 7)
        assertEquals(UmpTranslationResult.INVALID_DTE_SEQUENCE, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(9, context.midi1Pos)
        assertEquals(0, context.output.size)
    }

    @Test
    fun  testConvertMidi1ToUmpSimpleProgramChange()
    {
        var bytes = listOf(0xC1, 0x30)
        var context = UmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

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
        var context = UmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

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
        var context = UmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

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
        val context = UmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

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
        val context = UmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

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
        val context = UmpTranslatorContext(bytes.map { it.toByte()}, group = 7)

        // Pitchbend
        assertEquals(UmpTranslationResult.OK, UmpTranslator.translateMidi1BytesToUmp(context))
        assertEquals(3, context.midi1Pos)
        assertEquals(1, context.output.size)
        assertEquals(0x47E10000, context.output[0].int1)
        assertEquals(0x60800000u, context.output[0].int2.toUInt()) // note that source MIDI1 pitch bend is in littele endian.
    }
}
