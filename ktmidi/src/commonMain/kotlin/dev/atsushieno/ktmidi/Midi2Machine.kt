package dev.atsushieno.ktmidi

class Midi2Machine {
    fun interface Listener {
        fun onEvent(e: Ump)
    }

    var diagnosticsHandler: (String, Ump?) -> Unit =
        { message, ump -> throw UnsupportedOperationException(message + (if (ump != null) " : $ump" else null)) }

    private val listeners = arrayListOf<Listener>()

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private val channels = mutableMapOf<Int,Midi2MachineChannel>()
    val usedChannels : Iterable<Midi2MachineChannel>
        get() = channels.values
    fun channel(index: Int): Midi2MachineChannel {
        var ch = channels[index]
        if (ch == null) {
            ch = Midi2MachineChannel()
            channels[index] = ch
        }
        return ch
    }

    private fun withNoteRangeCheckV1(u: Ump, action: () -> Unit) = if (u.midi1Msb in 0..127) action() else diagnosticsHandler("Note is out of range", u)
    private fun withNoteRangeCheckV2(u: Ump, action: () -> Unit) = if (u.midi2Note in 0..127) action() else diagnosticsHandler("Note is out of range", u)

    @OptIn(ExperimentalUnsignedTypes::class)
    fun processEvent(evt: Ump) {
        when (evt.messageType) {
            MidiMessageType.MIDI1 -> {
                when (evt.statusCode) {
                    MidiChannelStatus.NOTE_ON ->
                        withNoteRangeCheckV1(evt) {
                            channel(evt.groupAndChannel).noteVelocity[evt.midi1Msb] = (evt.midi1Lsb shl 9).toUShort()
                        }
                    MidiChannelStatus.NOTE_OFF ->
                        withNoteRangeCheckV1(evt) {
                            channel(evt.groupAndChannel).noteVelocity[evt.midi1Msb] = 0u
                        }
                    MidiChannelStatus.PAF ->
                        withNoteRangeCheckV1(evt) {
                            channel(evt.groupAndChannel).pafVelocity[evt.midi1Msb] = (evt.midi1Lsb shl 25).toUInt()
                        }
                    MidiChannelStatus.CC -> {
                        // FIXME: handle RPNs and NRPNs by DTE
                        when (evt.midi1Msb) {
                            MidiCC.NRPN_MSB,
                            MidiCC.NRPN_LSB ->
                                channel(evt.groupAndChannel).dteTarget = DteTarget.NRPN
                            MidiCC.RPN_MSB,
                            MidiCC.RPN_LSB ->
                                channel(evt.groupAndChannel).dteTarget = DteTarget.RPN

                            MidiCC.DTE_MSB ->
                                channel(evt.groupAndChannel).processMidi1Dte(evt.midi1Lsb.toByte(), true)
                            MidiCC.DTE_LSB ->
                                channel(evt.groupAndChannel).processMidi1Dte(evt.midi1Lsb.toByte(), false)
                            MidiCC.DTE_INCREMENT ->
                                channel(evt.groupAndChannel).processMidi1DteIncrement()
                            MidiCC.DTE_DECREMENT ->
                                channel(evt.groupAndChannel).processMidi1DteDecrement()
                        }
                        channel(evt.groupAndChannel).controls[evt.midi1Msb] = (evt.midi1Lsb shl 25).toUInt()
                    }
                    MidiChannelStatus.PROGRAM ->
                        channel(evt.groupAndChannel).program = evt.midi1Msb.toByte()
                    MidiChannelStatus.CAF ->
                        channel(evt.groupAndChannel).caf = (evt.midi1Msb shl 25).toUInt()
                    MidiChannelStatus.PITCH_BEND ->
                        channel(evt.groupAndChannel).pitchbend = ((evt.midi1Msb.toUnsigned() shl 25) + (evt.midi1Lsb shl 18)).toUInt()
                }
            }
            MidiMessageType.MIDI2 -> {
                when (evt.statusCode) {
                    MidiChannelStatus.NOTE_ON ->
                        withNoteRangeCheckV2(evt) {
                            channel(evt.groupAndChannel).noteVelocity[evt.midi2Note] = evt.midi2Velocity16.toUShort()
                            channel(evt.groupAndChannel).noteAttribute[evt.midi2Note] =
                                evt.midi2NoteAttributeData.toUShort()
                            channel(evt.groupAndChannel).noteAttributeType[evt.midi2Note] =
                                evt.midi2NoteAttributeType.toUShort()
                        }
                    MidiChannelStatus.NOTE_OFF ->
                        withNoteRangeCheckV2(evt) {
                            channel(evt.groupAndChannel).noteVelocity[evt.midi2Note] = 0u
                        }
                    MidiChannelStatus.PAF ->
                        withNoteRangeCheckV2(evt) {
                            channel(evt.groupAndChannel).pafVelocity[evt.midi2Note] = evt.midi2PAfData
                        }
                    MidiChannelStatus.CC -> {
                        channel(evt.groupAndChannel).controls[evt.midi2CCIndex] = evt.midi2CCData
                    }
                    MidiChannelStatus.PROGRAM -> {
                        if (evt.midi2ProgramOptions and 1 != 0) {
                            channel(evt.groupAndChannel).controls[MidiCC.BANK_SELECT] =
                                evt.midi2ProgramBankMsb.toUInt()
                            channel(evt.groupAndChannel).controls[MidiCC.BANK_SELECT_LSB] =
                                evt.midi2ProgramBankMsb.toUInt()
                        }
                        channel(evt.groupAndChannel).program = evt.midi2ProgramProgram.toByte()
                    }
                    MidiChannelStatus.CAF ->
                        channel(evt.groupAndChannel).caf = evt.midi2CAfData
                    MidiChannelStatus.PITCH_BEND ->
                        channel(evt.groupAndChannel).pitchbend = evt.midi2PitchBendData
                    MidiChannelStatus.PER_NOTE_PITCH_BEND ->
                        channel(evt.groupAndChannel).perNotePitchbend[evt.midi2Note] = evt.midi2PitchBendData
                    MidiChannelStatus.PER_NOTE_RCC ->
                        withNoteRangeCheckV2(evt) {
                            channel(evt.groupAndChannel).perNoteRCC[evt.midi2PerNoteRCCIndex][evt.midi2Note] =
                                evt.midi2PerNoteRCCData
                        }
                    MidiChannelStatus.PER_NOTE_ACC ->
                        withNoteRangeCheckV2(evt) {
                            channel(evt.groupAndChannel).perNoteACC[evt.midi2PerNoteACCIndex][evt.midi2Note] =
                                evt.midi2PerNoteACCData
                        }
                    MidiChannelStatus.RPN ->
                        channel(evt.groupAndChannel).rpns[evt.midi2RpnMsb * 128 + evt.midi2RpnLsb] = evt.midi2RpnData
                    MidiChannelStatus.NRPN ->
                        channel(evt.groupAndChannel).nrpns[evt.midi2NrpnMsb * 128 + evt.midi2NrpnLsb] =
                            evt.midi2NrpnData
                    MidiChannelStatus.RELATIVE_RPN ->
                        channel(evt.groupAndChannel).rpns[evt.midi2RpnMsb * 128 + evt.midi2RpnLsb] =
                            (channel(evt.groupAndChannel).rpns[evt.midi2RpnMsb * 128 + evt.midi2RpnLsb].toLong() + evt.midi2RpnData.toInt()).toUInt()
                    MidiChannelStatus.RELATIVE_NRPN ->
                        channel(evt.groupAndChannel).nrpns[evt.midi2RpnMsb * 128 + evt.midi2RpnLsb] =
                            (channel(evt.groupAndChannel).nrpns[evt.midi2NrpnMsb * 128 + evt.midi2NrpnLsb].toLong() + evt.midi2NrpnData.toInt()).toUInt()
                }
            }
        }
        for (receiver in listeners)
            receiver.onEvent(evt)
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
class Midi2MachineChannel {
    val noteVelocity = Array<UShort>(128) { 0u }
    val noteAttribute = Array<UShort>(128) { 0u }
    val noteAttributeType = Array<UShort>(128) { 0u }
    val pafVelocity = Array<UInt>(128) { 0u }
    val controls = Array<UInt>(128) { 0u }
    val perNoteRCC = Array(128) { Array<UInt>(128) { 0u } }
    val perNoteACC = Array(128) { Array<UInt>(128) { 0u } }
    val rpns = Array<UInt>(128 * 128) { 0u } // only 5 should be used though...
    val nrpns = Array<UInt>(128 * 128) { 0u }
    var program: Byte = 0
    var caf: UInt = 0u
    var pitchbend: UInt = 0x80000000u
    val perNotePitchbend = Array<UInt>(128) { 0x80000000u }
    var dteTarget: DteTarget = DteTarget.RPN
    private var dte_target_value: Byte = 0

    fun processMidi1Dte(value: Byte, isMsb: Boolean) {
        var arr: Array<UInt>
        when (dteTarget) {
            DteTarget.RPN -> {
                dte_target_value = (controls[(if (isMsb) MidiCC.RPN_MSB else MidiCC.RPN_LSB)] shr 25).toByte()
                arr = rpns
            }
            DteTarget.NRPN -> {
                dte_target_value = (controls[(if (isMsb) MidiCC.NRPN_MSB else MidiCC.NRPN_LSB)] shr 25).toByte()
                arr = nrpns
            }
        }
        val cur = arr[dte_target_value.toUnsigned()]
        if (isMsb)
            arr[dte_target_value.toUnsigned()] = (value shl 25).toUInt() + (cur and 0x1FE0000.toUInt())
        else
            arr[dte_target_value.toUnsigned()] = (cur and 0xFE000000.toUInt()) + (value shl 18).toUInt()
    }

    // FIXME: should this be like this?? It feels so wrong. Shouldn't they be multiplied by 18 bits?
    fun processMidi1DteIncrement() {
        when (dteTarget) {
            DteTarget.RPN -> rpns[dte_target_value.toUnsigned()]++
            DteTarget.NRPN -> nrpns[dte_target_value.toUnsigned()]++
        }
    }

    // FIXME: should this be like this?? It feels so wrong. Shouldn't they be multiplied by 18 bits?
    fun processMidi1DteDecrement() {
        when (dteTarget) {
            DteTarget.RPN -> rpns[dte_target_value.toUnsigned()]--
            DteTarget.NRPN -> nrpns[dte_target_value.toUnsigned()]--
        }
    }
}
