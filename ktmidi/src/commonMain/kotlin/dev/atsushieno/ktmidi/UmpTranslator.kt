package dev.atsushieno.ktmidi

import kotlin.experimental.and

/**
 - `allowReorderedDTE`: MIDI 2.0 Defalult Translation (UMP specification Appendix D.3) accepts only DTE LSB as
   the conversion terminator, but we allow DTE LSB to come first, if this flag is enabled.
 - midiProtocol: Destination protocol: can be MIDI1 UMP or MIDI2 UMP.
 - group: the group in UMP can be specified
 - useSysex8: Sysex conversion can be done to sysex8
 - isMidi1Smf: When it is true, it means the input MIDI1 stream contains delta time. TODO: implement
 */
class Midi1ToUmpTranslatorContext(val midi1: List<Byte>,
                                  val allowReorderedDTE: Boolean = false,
                                  val midiProtocol: Int = MidiTransportProtocol.UMP,
                                  val group: Int,
                                  val useSysex8: Boolean = false,
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

class UmpToMidi1BytesTranslatorContext(val deltaTimeMasterClock: Int = 192,
                                       val treatJRTimestampAsSmfDeltaTime: Boolean = false,
                                       val skipDeltaTime: Boolean = false) {
}

object UmpTranslationResult {
    const val OK = 0
    const val INVALID_SYSEX = 0x10
    const val INVALID_DTE_SEQUENCE = 0x11
    const val INVALID_STATUS = 0x13
    const val INCOMPLETE_SYSEX7 = 0x20
}

object UmpTranslator {

    /**
     Converts UMP stream (of either MIDI 1.0 or 2.0) to MIDI 1.0 bytestream, as per Default Translation,
     with SMF delta time extension (otional).
     JR Timestamps are expressed as SMF delta time field in a MIDI event.
     As our MidiMusic2 can hold JR Timestamp fields to actually represent SMF-compatible ticks
     (i.e. they need to be *converted* to UMP compatible JR Timestamp to conform to UMP specification),
     the input UMP stream could contain such JR Timestamps (it is indeed intended to convert Midi2Music
     track contents to SMF compatible events).

     `treatJRTimestampAsSmfDeltaTime` argument determines whether this function converts JR Timestamp
     values into SMPTE offset OR NOT.
     In SMP, the deltaTime values can be either SMPTE offsets or tempo based ticks.
     In argument UMP stream, the deltaTime values can be either JR Timestamp of tempo based ticks (note that
     the ticks are NOT conforming to UMP spec. as explained above).
     If the JR Timestamp value is NOT of the tempo based ticks, then we have to convert JR Timestamps
     (of 1/31250 seconds) to SMPTE offsets.

     When do you NOT need delta time information when translating MIDI2 UMP stream that contain JR Timestamps?
     I cannot think of any usage scenario. IMO you will always need delta times.
     But if you REALLY do not need them, then you can skip delta times by `skipDeltaTime = true`.
     */
    fun translateUmpToMidi1Bytes(dst: MutableList<Byte>, src: Sequence<Ump>, context: UmpToMidi1BytesTranslatorContext = UmpToMidi1BytesTranslatorContext()) : Int {
        val dtc = Midi2Music.UmpDeltaTimeComputer()
        var offset = 0
        val sysex7 = mutableListOf<Byte>()
        var deltaTime: Int = 0
        src.forEach {
            if (it.isDeltaClockstamp) {
                deltaTime += it.deltaClockstamp
                return@forEach
            }
            if (it.isJRTimestamp) {
                if (!context.skipDeltaTime)
                    // FIXME: this should take deltaTimeMasterClock (SMPTE MIDI Time Code) into account.
                    deltaTime += if (context.treatJRTimestampAsSmfDeltaTime) dtc.messageToDeltaTime(it) else it.jrTimestamp
                return@forEach
            }
            offset += translateSingleUmpToMidi1Bytes(dst, it, offset, if (context.skipDeltaTime) null else deltaTime, sysex7)
            if (it.messageType == MidiMessageType.SYSEX7) {
                if (it.statusCode == Midi2BinaryChunkStatus.END || it.statusCode ==
                    Midi2BinaryChunkStatus.COMPLETE_PACKET) {
                    dst.addAll(Midi1Event.encode7BitLength(deltaTime))
                    dst.add(0xF0.toByte())
                    dst.addAll(Midi1Event.encode7BitLength(sysex7.size))
                    dst.addAll(sysex7)
                    sysex7.clear()
                    dst.add(0xF7.toByte())
                }
            }
            deltaTime = 0
        }
        return if (sysex7.any()) UmpTranslationResult.INCOMPLETE_SYSEX7 else UmpTranslationResult.OK
    }

    /// Convert one single UMP (without JR Timestamp) to MIDI 1.0 Message (without delta time)
    fun translateSingleUmpToMidi1Bytes(dst: MutableList<Byte>, ump: Ump, dstOffset: Int = 0, deltaTime: Int? = null, sysex: MutableList<Byte>? = null) : Int {
        var midiEventSize = 0
        val statusCode = ump.statusByte and 0xF0

        var offset = dstOffset

        val addDeltaTimeAndStatus = {
            if (deltaTime != null) {
                val list = Midi1Event.encode7BitLength(deltaTime).toList()
                list.forEachIndexed { i, v -> dst[offset + i] = v }
                offset += list.size
            }

            dst[offset] = ump.statusByte.toByte()
        }

        when (ump.messageType) {
            MidiMessageType.SYSTEM -> {
                addDeltaTimeAndStatus()
                midiEventSize = 1
                when (statusCode) {
                    0xF1, 0xF3, 0xF9 -> {
                        dst[offset + 1] = ump.midi1Msb.toByte()
                        midiEventSize = 2
                    }
                }
            }

            MidiMessageType.MIDI1 -> {
                addDeltaTimeAndStatus()
                midiEventSize = 3
                dst[offset + 1] = ump.midi1Msb.toByte()
                when (statusCode) {
                    0xC0, 0xD0 -> {
                        midiEventSize = 2
                    }

                    else -> {
                        dst[offset + 2] = ump.midi1Lsb.toByte()
                    }
                }
            }

            MidiMessageType.MIDI2 -> {
                when (statusCode) {
                    MidiChannelStatus.RPN -> {
                        addDeltaTimeAndStatus()
                        midiEventSize = 12
                        dst[offset + 0] = (ump.channelInGroup + MidiChannelStatus.CC).toByte()
                        dst[offset + 1] = MidiCC.RPN_MSB.toByte()
                        dst[offset + 2] = ump.midi2RpnMsb.toByte()
                        dst[offset + 3] = dst[offset] // CC + channel
                        dst[offset + 4] = MidiCC.RPN_LSB.toByte()
                        dst[offset + 5] = ump.midi2RpnLsb.toByte()
                        dst[offset + 6] = dst[offset] // CC + channel
                        dst[offset + 7] = MidiCC.DTE_MSB.toByte()
                        dst[offset + 8] = ((ump.midi2RpnData shr 25) and 0x7Fu).toByte()
                        dst[offset + 9] = dst[offset] // CC + channel
                        dst[offset + 10] = MidiCC.DTE_LSB.toByte()
                        dst[offset + 11] = ((ump.midi2RpnData shr 18) and 0x7Fu).toByte()
                    }

                    MidiChannelStatus.NRPN -> {
                        addDeltaTimeAndStatus()
                        midiEventSize = 12
                        dst[offset + 0] = (ump.channelInGroup + MidiChannelStatus.CC).toByte()
                        dst[offset + 1] = MidiCC.NRPN_MSB.toByte()
                        dst[offset + 2] = ump.midi2NrpnMsb.toByte()
                        dst[offset + 3] = dst[offset] // CC + channel
                        dst[offset + 4] = MidiCC.NRPN_LSB.toByte()
                        dst[offset + 5] = ump.midi2NrpnLsb.toByte()
                        dst[offset + 6] = dst[offset] // CC + channel
                        dst[offset + 7] = MidiCC.DTE_MSB.toByte()
                        dst[offset + 8] = ((ump.midi2RpnData shr 25) and 0x7Fu).toByte()
                        dst[offset + 9] = dst[offset] // CC + channel
                        dst[offset + 10] = MidiCC.DTE_LSB.toByte()
                        dst[offset + 11] = ((ump.midi2RpnData shr 18) and 0x7Fu).toByte()
                    }

                    MidiChannelStatus.NOTE_OFF,
                    MidiChannelStatus.NOTE_ON -> {
                        addDeltaTimeAndStatus()
                        midiEventSize = 3
                        dst[offset + 1] = ump.midi2Note.toByte()
                        dst[offset + 2] = (ump.midi2Velocity16 / 0x200).toByte()
                    }

                    MidiChannelStatus.PAF -> {
                        addDeltaTimeAndStatus()
                        midiEventSize = 3
                        dst[offset + 1] = ump.midi2Note.toByte()
                        dst[offset + 2] = (ump.midi2PAfData / 0x2000000u).toByte()
                    }

                    MidiChannelStatus.CC -> {
                        addDeltaTimeAndStatus()
                        midiEventSize = 3
                        dst[1] = ump.midi2CCIndex.toByte()
                        dst[2] = (ump.midi2CCData / 0x2000000u).toByte()
                    }

                    MidiChannelStatus.PROGRAM -> {
                        addDeltaTimeAndStatus()
                        if (0 != ump.midi2ProgramOptions and MidiProgramChangeOptions.BANK_VALID) {
                            midiEventSize = 8
                            dst[offset + 0] = (ump.channelInGroup + MidiChannelStatus.CC).toByte()
                            dst[offset + 1] = 0 // Bank MSB
                            dst[offset + 2] = ump.midi2ProgramBankMsb.toByte()
                            dst[offset + 3] = ((dst[offset] and 0xF) + MidiChannelStatus.CC).toByte()
                            dst[offset + 4] = 32 // Bank LSB
                            dst[offset + 5] = ump.midi2ProgramBankLsb.toByte()
                            dst[offset + 6] = ((dst[offset] and 0xF) + MidiChannelStatus.PROGRAM).toByte()
                            dst[offset + 7] = ump.midi2ProgramProgram.toByte()
                        } else {
                            midiEventSize = 2
                            dst[offset + 1] = ump.midi2ProgramProgram.toByte()
                        }
                    }

                    MidiChannelStatus.CAF -> {
                        addDeltaTimeAndStatus()
                        midiEventSize = 2
                        dst[offset + 1] = (ump.midi2CAfData / 0x2000000u).toByte()
                    }

                    MidiChannelStatus.PITCH_BEND -> {
                        addDeltaTimeAndStatus()
                        midiEventSize = 3
                        val pitchBendV1 = ump.midi2PitchBendData.toULong() / 0x40000u
                        // Note that it has to be translated to little endian pair
                        dst[offset + 1] = (pitchBendV1 % 0x80u).toByte()
                        dst[offset + 2] = (pitchBendV1 / 0x80u).toByte()
                    }
                    // skip for other status bytes; we cannot support them.
                }
            }

            MidiMessageType.SYSEX7 -> {
                midiEventSize = 0
                val bytes = ump.toPlatformNativeBytes()
                sysex?.addAll(bytes.drop(2).take(ump.sysex7Size))
            }

            MidiMessageType.SYSEX8_MDS -> {
                // By the UMP specification they cannot be translated in Default Translation
                midiEventSize = 0
            }

            else -> {
                // The sepcification does not state anything else, including Flex Data and UMP Stream messages -> ignore
                midiEventSize = 0
            }
        }
        midiEventSize += offset - dstOffset
        return midiEventSize
    }


    private fun convertMidi1DteToUmp(context: Midi1ToUmpTranslatorContext, channel: Int): Long {
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
    fun translateMidi1BytesToUmp(context: Midi1ToUmpTranslatorContext): Int {

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
                val len = Midi1Message.fixedDataSize(context.midi1[context.midi1Pos]) + 1
                val byte2 = context.midi1[context.midi1Pos + 1].toInt()
                val byte3 = if (len > 2) context.midi1[context.midi1Pos + 2].toInt() else 0
                val channel = context.midi1[context.midi1Pos].toInt() and 0xF
                if (context.midiProtocol == MidiTransportProtocol.MIDI1) {
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