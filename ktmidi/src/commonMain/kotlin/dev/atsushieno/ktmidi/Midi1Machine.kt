package dev.atsushieno.ktmidi

class Midi1Machine {
    fun interface OnMidiMessageListener {
        fun onMessage(e: Midi1Message)
    }

    val eventListeners by lazy { mutableListOf<OnMidiMessageListener>() }

    var channels = Array(16) { Midi1MachineChannel() }

    fun processMessage(evt: Midi1Message) {
        when (evt.eventType.toUnsigned()) {
            MidiChannelStatus.NOTE_ON ->
                channels[evt.channel.toUnsigned()].noteVelocity[evt.msb.toUnsigned()] = evt.lsb

            MidiChannelStatus.NOTE_OFF ->
                channels[evt.channel.toUnsigned()].noteVelocity[evt.msb.toUnsigned()] = 0
            MidiChannelStatus.PAF ->
                channels[evt.channel.toUnsigned()].pafVelocity[evt.msb.toUnsigned()] = evt.lsb
            MidiChannelStatus.CC -> {
                // FIXME: handle RPNs and NRPNs by DTE
                when (evt.msb.toInt()) {
                    MidiCC.NRPN_MSB,
                    MidiCC.NRPN_LSB ->
                        channels[evt.channel.toUnsigned()].dteTarget = DteTarget.NRPN
                    MidiCC.RPN_MSB,
                    MidiCC.RPN_LSB ->
                        channels[evt.channel.toUnsigned()].dteTarget = DteTarget.RPN

                    MidiCC.DTE_MSB ->
                        channels[evt.channel.toUnsigned()].processDte(evt.lsb, true)
                    MidiCC.DTE_LSB ->
                        channels[evt.channel.toUnsigned()].processDte(evt.lsb, false)
                    MidiCC.DTE_INCREMENT ->
                        channels[evt.channel.toUnsigned()].processDteIncrement()
                    MidiCC.DTE_DECREMENT ->
                        channels[evt.channel.toUnsigned()].processDteDecrement()
                }
                channels[evt.channel.toUnsigned()].controls[evt.msb.toUnsigned()] = evt.lsb
            }
            MidiChannelStatus.PROGRAM ->
                channels[evt.channel.toUnsigned()].program = evt.msb
            MidiChannelStatus.CAF ->
                channels[evt.channel.toUnsigned()].caf = evt.msb
            MidiChannelStatus.PITCH_BEND ->
                channels[evt.channel.toUnsigned()].pitchbend = ((evt.msb.toUnsigned() shl 7) + evt.lsb).toShort()
        }

        eventListeners.forEach { it.onMessage(evt) }
    }
}

class Midi1MachineChannel {
    val noteVelocity = ByteArray(128)
    val pafVelocity = ByteArray(128)
    val controls = ByteArray(128)
    val rpns = ShortArray(128 * 128) // only 5 should be used though
    val nrpns = ShortArray(128 * 128)
    var program: Byte = 0
    var caf: Byte = 0
    var pitchbend: Short = 8192
    var dteTarget: DteTarget = DteTarget.RPN
    private var dte_target_value: Byte = 0

    @Deprecated("Use currentRPN (of Int type)")
    val rpnTarget: Short
        get() = ((controls[MidiCC.RPN_MSB].toUnsigned() shl 7) + controls[MidiCC.RPN_LSB]).toShort()

    val currentRPN: Int
        get() = ((controls[MidiCC.RPN_MSB].toUnsigned() shl 7) + controls[MidiCC.RPN_LSB])
    val currentNRPN: Int
        get() = ((controls[MidiCC.NRPN_MSB].toUnsigned() shl 7) + controls[MidiCC.NRPN_LSB])

    fun processDte(value: Byte, isMsb: Boolean) {
        lateinit var arr: ShortArray
        var target = 0
        when (dteTarget) {
            DteTarget.RPN -> {
                target = currentRPN
                arr = rpns
            }
            DteTarget.NRPN -> {
                target = currentNRPN
                arr = nrpns
            }
        }
        val cur = arr[target].toInt()
        if (isMsb)
            arr[target] = (cur and 0x007F + ((value.toUnsigned() and 0x7F) shl 7)).toShort()
        else
            arr[target] = (cur and 0x3FF0 + (value.toUnsigned() and 0x7F)).toShort()
    }

    fun processDteIncrement() {
        when (dteTarget) {
            DteTarget.RPN -> rpns[controls[MidiCC.RPN_MSB] * 0x80 + controls[MidiCC.RPN_LSB]]++
            DteTarget.NRPN -> nrpns[controls[MidiCC.NRPN_MSB] * 0x80 + controls[MidiCC.NRPN_LSB]]++
        }
    }

    fun processDteDecrement() {
        when (dteTarget) {
            DteTarget.RPN -> rpns[controls[MidiCC.RPN_MSB] * 0x80 + controls[MidiCC.RPN_LSB]]--
            DteTarget.NRPN -> nrpns[controls[MidiCC.NRPN_MSB] * 0x80 + controls[MidiCC.NRPN_LSB]]--
        }
    }
}

enum class DteTarget {
    RPN,
    NRPN
}
