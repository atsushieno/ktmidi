package dev.atsushieno.ktmidi

import kotlin.experimental.and

private object MessageDataControl {
    const val None:Byte = 0
    const val OnlyNonDefaults:Byte = 1
    const val Full:Byte = 0x7F
}

private object SystemMessagesFlags {
    const val MtcQuarterFrame = 0
    const val SongPosition = 1 shl 1
    const val SongSelect = 1 shl 2
}

private object ChannelControllerFlags {
    const val Pitchbend = 0
    const val CC = 1 shl 1
    const val Rpn = 1 shl 2
    const val Nrpn = 1 shl 3
    const val Program = 1 shl 4
    const val CAf = 1 shl 5
}

private object NoteDataFlags {
    const val Notes = 0
    const val PAf = 1 shl 1
    const val Pitchbend = 1 shl 2
    const val RegisteredController = 1 shl 3
    const val AssignableController = 1 shl 4
}

// FIXME: to fully support MessageDataControl.OnlyNoDefaults we need to track
//  which CCs, RPNs, NRPNs etc. have non-default values, which is not doable
//  with the current MidiMachine API.
fun Midi1Machine.reportMidiMessages(
    address: Byte,
    messageDataControl: Byte,
    midiMessageReportSystemMessages: Byte,
    midiMessageReportChannelControllerMessages: Byte,
    midiMessageReportNoteDataMessages: Byte
): Sequence<List<Byte>> = sequence {
    if (messageDataControl == MessageDataControl.None)
        return@sequence

    yieldAll(getSystemMessages(messageDataControl, midiMessageReportSystemMessages))
    when (address.toInt()) {
        0x7E, 0x7F -> (0..15)
        else -> (address..address)
    }.forEach {
        yieldAll(getChannelControllerMessages(messageDataControl, midiMessageReportChannelControllerMessages, it))
        yieldAll(getNoteDataMessages(messageDataControl, midiMessageReportNoteDataMessages, it))
    }
}

private fun Midi1Machine.getSystemMessages(
    messageDataControl: Byte,
    flags: Byte
): Sequence<List<Byte>> = sequence {
    val f = flags.toInt()
    if ((f and SystemMessagesFlags.MtcQuarterFrame) != 0)
        yield(listOf(MidiSystemStatus.MIDI_TIME_CODE.toByte(), systemCommon.mtcQuarterFrame))
    if ((f and SystemMessagesFlags.SongPosition) != 0 &&
        (messageDataControl == MessageDataControl.Full || systemCommon.songPositionPointer.toInt() != 0))
        yield(listOf(MidiSystemStatus.SONG_POSITION.toByte(),
            (systemCommon.songPositionPointer shl 7).toByte(),
            (systemCommon.songPositionPointer and 0x7F).toByte()))
    if ((f and SystemMessagesFlags.SongSelect) != 0 &&
        (messageDataControl == MessageDataControl.Full || systemCommon.songSelect.toInt() != 0))
        yield(listOf(MidiSystemStatus.SONG_SELECT.toByte(), systemCommon.songSelect))
}

private fun midi1Rpn(channel: Int, index: Int, value: Short) = listOf(
    MidiChannelStatus.CC or channel, MidiCC.RPN_MSB, (index / 0x80),
    MidiChannelStatus.CC or channel, MidiCC.RPN_LSB, (index % 0x80),
    MidiChannelStatus.CC or channel, MidiCC.DTE_MSB, (value % 0x80),
    MidiChannelStatus.CC or channel, MidiCC.DTE_LSB, (value % 0x80)
).map { i -> i.toByte() }
private fun midi1Nrpn(channel: Int, index: Int, value: Short) = listOf(
    MidiChannelStatus.CC or channel, MidiCC.NRPN_MSB, (index / 0x80),
    MidiChannelStatus.CC or channel, MidiCC.NRPN_LSB, (index % 0x80),
    MidiChannelStatus.CC or channel, MidiCC.DTE_MSB, (value % 0x80),
    MidiChannelStatus.CC or channel, MidiCC.DTE_LSB, (value % 0x80)
).map { i -> i.toByte() }

private fun Midi1Machine.getChannelControllerMessages(
    messageDataControl: Byte,
    flags: Byte,
    channel: Int
): Sequence<List<Byte>> = sequence {
    val f = flags.toInt()
    // Pitchbend
    if ((f and ChannelControllerFlags.Pitchbend) != 0) {
        with(channels[channel]) {
            yield(listOf(MidiChannelStatus.PITCH_BEND.toByte(), (pitchbend and 0x7F).toByte()))
            yield(listOf(MidiChannelStatus.PITCH_BEND.toByte(), (pitchbend shr 7).toByte()))
        }
    }
    // CC
    if ((f and ChannelControllerFlags.CC) != 0) {
        with(channels[channel]) {
            ((0..63) + (64..122)).forEach {
                when (it) {
                    0, 6, 32, 38, 98, 99, 100, 101, 120, 121 -> return@forEach
                }
                yield(listOf(MidiChannelStatus.CC.toByte(), it.toByte(), controls[it]))
            }
            when (omniMode) {
                false -> yield(listOf(MidiChannelStatus.CC, MidiCC.OMNI_MODE_OFF, 0).map { it.toByte() })
                true -> yield(listOf(MidiChannelStatus.CC, MidiCC.OMNI_MODE_ON, 0).map { it.toByte() })
                null -> {} // unspecified yet
            }
            when (monoPolyMode) {
                false -> yield(listOf(MidiChannelStatus.CC, MidiCC.MONO_MODE_ON, controls[MidiCC.MONO_MODE_ON].toInt()).map {it.toByte()})
                true -> yield(listOf(MidiChannelStatus.CC, MidiCC.POLY_MODE_ON, 0).map {it.toByte()})
                null -> {} // unspecified yet
            }
        }
    }
    // RPN
    if ((f and ChannelControllerFlags.Rpn) != 0) {
        with(channels[channel]) {
            (0 until 0x80 * 0x80).forEach {
                if (controllerCatalog.enabledRpns[it])
                    yield(midi1Rpn(channel, it, rpns[it]))
            }
            // "MIDI 1 Protocol only: After all RPN's have been sent, the Responder shall send the Null Function Value RPN"
            yield(midi1Rpn(channel, 0x7F shl 7, 0))
        }
    }
    // NRPN
    if ((f and ChannelControllerFlags.Nrpn) != 0) {
        with(channels[channel]) {
            (0 until 0x80 * 0x80).forEach {
                if (controllerCatalog.enabledNrpns[it])
                    yield(midi1Nrpn(channel, it, nrpns[it]))
            }
            // "MIDI 1 Protocol only: After all NRPN's have been sent, the Responder shall send the Null Function Value NRPN"
            yield(midi1Nrpn(channel, 0x7F shl 7, 0))
        }
    }
    // Program Change
    // LAMESPEC: Section 9.7.2 states:
    //   "If Bank Select is supported, the Responder shall send Bank Select MSB and LSB followed by a Program Change message."
    //  however it is unclear if these control changes should not be sent at the preceding CC block,
    //  also unclear if they should be placed here.
    //
    //  Possible outputs outlined:
    //
    //  - Bank select MSB and LSB appears on the CC list IF they are regarded as supported
    //    (regardless of whether they are supported or not, when messageDataControl is FULL)
    //  - Bank select MSB and LSB do not appear on the CC list IF they are not supported
    //    (this means, only Bank Select MSB and LSB are missing on the CC list when messageDataControl is FULL)
    //  - CC with Bank select MSB or LSB, apart from the CC list, appears immediately in front of the Program change;
    //    This happens regardless of whether they appear on the CC list or not.
    //
    // At this state, I'm lazy and find that Bank Select showing at the CC list and NOT here is the easiest.
    if ((f and ChannelControllerFlags.Program) != 0) {
        yield(listOf(MidiChannelStatus.PROGRAM or channel, channels[channel].program).map { it.toByte() })
    }
    // CAf
    if ((f and ChannelControllerFlags.CAf) != 0) {
        yield(listOf(MidiChannelStatus.CAF or channel, channels[channel].caf).map { it.toByte() })
    }
}

private fun Midi1Machine.getNoteDataMessages(
    messageDataControl: Byte,
    flags: Byte,
    channel: Int
): Sequence<List<Byte>> = sequence {
    val f = flags.toInt()
    with(channels[channel]) {
        // LAMESPEC: MIDI-CI v1.2, section 9.5.1:
        // "If a full report of note data is requested, a Responder should report Note On / Off messages for all active and inactive notes."
        // whereas MIDI-CI v1.2, section 9.7.2:
        // "In MIDI 1.0 Protocol: The Responder shall send a Note On message for each currently active note with the original velocity value."
        // Here we follow 9.5.1, otherwise `messageDataControl == FULL` does not make sense.
        val targetNotes = (0..127).filter { messageDataControl == MessageDataControl.Full || noteOnStatus[it] }

        // Notes
        if ((f and NoteDataFlags.Notes) != 0) {
            targetNotes.forEach {
                yield(listOf(MidiChannelStatus.NOTE_ON or channel, it, noteVelocity[it]).map { it.toByte() })
                // also:
                // "For every Note On message which has been sent, the Responder shall send a matching Note Off message prior to sending the End of MIDI Message Report."
                yield(listOf(MidiChannelStatus.NOTE_OFF or channel, it, noteVelocity[it]).map { it.toByte() })
            }
        }
        // PAf
        if ((f and NoteDataFlags.PAf) != 0) {
            targetNotes.forEach {
                yield(listOf(MidiChannelStatus.PAF or channel, it, pafVelocity[it]).map { it.toByte() })
            }
        }
    }
}
