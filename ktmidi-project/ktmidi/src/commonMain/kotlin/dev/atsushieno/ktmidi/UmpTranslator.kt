package dev.atsushieno.ktmidi

import kotlin.experimental.and

class UmpTranslatorContext(val midi1: List<Byte>,
                           // MIDI 2.0 Defalult Translation (UMP specification Appendix D.3) accepts only DTE LSB as
                           // the conversion terminator, but we allow DTE LSB to come first, if this flag is enabled.
                           val allowReorderedDTE: Boolean = false,
                           // Destination protocol: can be MIDI1 UMP or MIDI2 UMP.
                           val midiProtocol: Int = MidiCIProtocolType.MIDI2,
                           // Group can be specified
                           val group: Int,
                           // Sysex conversion can be done to sysex8
                           val useSysex8: Boolean = false,
                           // When it is true, it means the input MIDI1 stream contains delta time. TODO: implement
                           val isMidi1Smf: Boolean = false) {
    var midi1Pos: Int = 0
    val output: MutableList<Ump> = mutableListOf()

    // DTE conversion target.
    // Ump.convertMidi1BytesToUmp() will return *_INVALID_RPN or *_INVALID_NRPN
    // for such invalid sequences, and to report that correctly we need to preserve CC status.
    // They are initialized to 0x8080 that implies both bank (MSB) and index (LSB) are invalid (> 0x7F).
    // When they are assigned valid values, then (context_[n]rpn & 0x8080) will become 0.
    // When cmidi2_convert_midi1_messages_to_ump() encountered DTE LSB, they are consumed
    // and reset to the initial value (0x8080).
    var rpnState: Int = 0x8080
    var nrpnState: Int = 0x8080
    var dteState: Int = 0x8080

    // Bank Select CC is preserved for the next program change.
    // The initial value is 0x8080, same as RPN/NRPN/DTE.
    // After program change is set, it is reset to the initial value.
    var bankState: Int = 0x8080
    var tempo: Int = 500000
}

object UmpTranslationResult {
    const val OK = 0
    const val OUT_OF_SPACE = 1
    const val INVALID_SYSEX = 0x10
    const val INVALID_DTE_SEQUENCE = 0x11
    const val INVALID_STATUS = 0x13
}

object UmpTranslator {

    private fun convertMidi1DteToUmp(context: UmpTranslatorContext, channel: Int): Long {
        val isRpn = (context.rpnState and 0x8080) == 0
        val msb = (if (isRpn) context.rpnState else context.nrpnState) shr 8
        val lsb = (if (isRpn) context.rpnState else context.nrpnState) and 0xFF
        val data = (context.dteState shr 8 shl 25) + ((context.dteState and 0x7F) shl 18).toLong()
        // reset RPN/NRPN/DTE status to the initial values.
        context.rpnState = 0x8080
        context.nrpnState = 0x8080
        context.dteState = 0x8080
        return if (isRpn) UmpFactory.midi2RPN(context.group, channel, msb, lsb, data)
        else UmpFactory.midi2NRPN(context.group, channel, msb, lsb, data)
    }

    // Returns one of those UmpTranslationResult constants: 0 for success, others for failure
    fun translateMidi1BytesToUmp(context: UmpTranslatorContext): Int {

        while (context.midi1Pos < context.midi1.size) {
            // FIXME: implement deltaTime to JR Timestamp conversion.

            if (context.midi1[context.midi1Pos] == 0xF0.toByte()) {
                // sysex
                val f7Pos = context.midi1.drop(context.midi1Pos).indexOf(0xF7.toByte())
                if (f7Pos < 0)
                    return UmpTranslationResult.INVALID_SYSEX
                val sysexSize = f7Pos - context.midi1Pos - 1 // excluding 0xF7
                if (context.useSysex8)
                    context.output.addAll(
                        UmpFactory.sysex8(
                            context.group,
                            context.midi1.drop(context.midi1Pos).take(sysexSize)
                        )
                    )
                else
                    context.output.addAll(
                        UmpFactory.sysex7(
                            context.group,
                            context.midi1.drop(context.midi1Pos).take(sysexSize)
                        )
                    )
                context.midi1Pos += sysexSize + 1 // +1 for 0xF7
            } else {
                // fixed sized message
                val len = MidiEvent.fixedDataSize(context.midi1[context.midi1Pos]) + 1
                val byte2 = context.midi1[context.midi1Pos + 1].toInt()
                val byte3 = if (len > 2) context.midi1[context.midi1Pos + 2].toInt() else 0
                val channel = context.midi1[context.midi1Pos].toInt() and 0xF
                if (context.midiProtocol == MidiCIProtocolType.MIDI1) {
                    // generate MIDI1 UMPs
                    context.output.add(
                        Ump(
                            UmpFactory.midi1Message(
                                context.group,
                                context.midi1[context.midi1Pos] and 0xF0.toByte(),
                                channel,
                                byte2.toByte(),
                                byte3.toByte()
                            )
                        )
                    )
                    context.midi1Pos += len
                } else {
                    // generate MIDI2 UMPs
                    var m2: Long = 0
                    val NO_ATTRIBUTE_TYPE: Byte = 0
                    val NO_ATTRIBUTE_DATA = 0
                    var bankValid = false
                    var bankMsbValid = false
                    var bankLsbValid = false
                    var skipEmitUmp = false
                    when (context.midi1[context.midi1Pos].toInt() and 0xF0) {
                        MidiChannelStatus.NOTE_OFF -> m2 = UmpFactory.midi2NoteOff(
                            context.group,
                            channel,
                            byte2,
                            NO_ATTRIBUTE_TYPE,
                            byte3 shl 9,
                            NO_ATTRIBUTE_DATA
                        )

                        MidiChannelStatus.NOTE_ON -> m2 = UmpFactory.midi2NoteOn(
                            context.group,
                            channel,
                            byte2,
                            NO_ATTRIBUTE_TYPE,
                            byte3 shl 9,
                            NO_ATTRIBUTE_DATA
                        )

                        MidiChannelStatus.PAF -> m2 =
                            UmpFactory.midi2PAf(context.group, channel, byte2, byte3.toUnsigned() shl 25)

                        MidiChannelStatus.CC -> {
                            when (byte2) {
                                MidiCC.RPN_MSB -> {
                                    context.rpnState = (context.rpnState and 0xFF) or (byte3 shl 8)
                                    skipEmitUmp = true
                                }

                                MidiCC.RPN_LSB -> {
                                    context.rpnState = (context.rpnState and 0xFF00) or byte3
                                    skipEmitUmp = true
                                }

                                MidiCC.NRPN_MSB -> {
                                    context.nrpnState = (context.nrpnState and 0xFF) or (byte3 shl 8)
                                    skipEmitUmp = true
                                }

                                MidiCC.NRPN_LSB -> {
                                    context.nrpnState = (context.nrpnState and 0xFF00) or byte3
                                    skipEmitUmp = true
                                }

                                MidiCC.DTE_MSB -> {
                                    context.dteState = (context.dteState and 0xFF) or (byte3 shl 8)

                                    if (context.allowReorderedDTE && (context.dteState and 0x8080) == 0)
                                        m2 = convertMidi1DteToUmp(context, channel)
                                    else
                                        skipEmitUmp = true

                                }

                                MidiCC.DTE_LSB -> {
                                    context.dteState = (context.dteState and 0xFF00) or byte3

                                    if ((context.dteState and 0x8000) != 0 && !context.allowReorderedDTE)
                                        return UmpTranslationResult.INVALID_DTE_SEQUENCE
                                    if ((context.rpnState and 0x8080) != 0 && (context.nrpnState and 0x8080) != 0)
                                        return UmpTranslationResult.INVALID_DTE_SEQUENCE
                                    m2 = convertMidi1DteToUmp(context, channel)

                                }

                                MidiCC.BANK_SELECT -> {
                                    context.bankState = (context.bankState and 0xFF) or (byte3 shl 8)
                                    skipEmitUmp = true
                                }

                                MidiCC.BANK_SELECT_LSB -> {
                                    context.bankState = (context.bankState and 0xFF00) or byte3
                                    skipEmitUmp = true
                                }

                                else -> m2 = UmpFactory.midi2CC(context.group, channel, byte2, byte3.toUnsigned() shl 25)
                            }
                        }

                        MidiChannelStatus.PROGRAM -> {
                            bankMsbValid = (context.bankState and 0x8000) == 0
                            bankLsbValid = (context.bankState and 0x80) == 0
                            bankValid = bankMsbValid || bankLsbValid
                            m2 = UmpFactory.midi2Program(
                                context.group, channel,
                                if (bankValid) MidiProgramChangeOptions.BANK_VALID else MidiProgramChangeOptions.NONE,
                                byte2,
                                if (bankMsbValid) context.bankState shr 8 else 0,
                                if (bankLsbValid) context.bankState and 0x7F else 0
                            )
                            context.bankState = 0x8080
                        }

                        MidiChannelStatus.CAF -> m2 = UmpFactory.midi2CAf(context.group, channel, byte2.toLong() shl 25)
                        MidiChannelStatus.PITCH_BEND ->
                            // Note: Pitch Bend values in the MIDI 1.0 Protocol are presented as Little Endian.
                            m2 = UmpFactory.midi2PitchBendDirect(
                                context.group,
                                channel,
                                (((byte3 shl 7) + byte2) shl 18).toLong()
                            )

                        else ->
                            return UmpTranslationResult.INVALID_STATUS
                    }
                    if (!skipEmitUmp)
                        context.output.add(Ump(m2))
                    context.midi1Pos += len
                }
            }
        }
        if (context.rpnState != 0x8080 || context.nrpnState != 0x8080 || context.dteState != 0x8080)
            return UmpTranslationResult.INVALID_DTE_SEQUENCE

        return UmpTranslationResult.OK
    }
}