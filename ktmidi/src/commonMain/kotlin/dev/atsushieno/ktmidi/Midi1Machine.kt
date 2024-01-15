package dev.atsushieno.ktmidi

class Midi1Machine {
    fun interface OnMidi1MessageListener {
        fun onMessage(e: Midi1Message)
    }

    val messageListeners by lazy { mutableListOf<OnMidi1MessageListener>() }
    @Deprecated("Use messageListeners property instead", ReplaceWith("messageListeners"))
    val eventListeners : MutableList<OnMidi1MessageListener>
        get() = messageListeners

    val controllerCatalog = Midi1ControllerCatalog()

    var systemCommon = Midi1SystemCommon()

    var channels = Array(16) { Midi1MachineChannel() }

    fun processMessage(evt: Midi1Message) {
        val ch = evt.channel.toUnsigned()
        when (evt.statusCode.toUnsigned()) {
            MidiChannelStatus.NOTE_ON -> {
                with (channels[ch]) {
                    noteVelocity[evt.msb.toUnsigned()] = evt.lsb
                    noteOnStatus[evt.msb.toUnsigned()] = true
                }
            }
            MidiChannelStatus.NOTE_OFF -> {
                with (channels[ch]) {
                    noteVelocity[evt.msb.toUnsigned()] = evt.lsb
                    noteOnStatus[evt.msb.toUnsigned()] = false
                }
            }
            MidiChannelStatus.PAF ->
                channels[ch].pafVelocity[evt.msb.toUnsigned()] = evt.lsb
            MidiChannelStatus.CC -> {
                // FIXME: handle RPNs and NRPNs by DTE
                when (evt.msb.toInt()) {
                    MidiCC.NRPN_MSB,
                    MidiCC.NRPN_LSB ->
                        channels[ch].dteTarget = DteTarget.NRPN
                    MidiCC.RPN_MSB,
                    MidiCC.RPN_LSB ->
                        channels[ch].dteTarget = DteTarget.RPN

                    MidiCC.DTE_MSB ->
                        channels[ch].processDte(evt.lsb, true)
                    MidiCC.DTE_LSB ->
                        channels[ch].processDte(evt.lsb, false)
                    MidiCC.DTE_INCREMENT ->
                        channels[ch].processDteIncrement()
                    MidiCC.DTE_DECREMENT ->
                        channels[ch].processDteDecrement()
                }
                channels[ch].controls[evt.msb.toUnsigned()] = evt.lsb
                when (evt.msb.toUnsigned()) {
                    MidiCC.OMNI_MODE_OFF -> channels[ch].omniMode = false
                    MidiCC.OMNI_MODE_ON -> channels[ch].omniMode = true
                    MidiCC.MONO_MODE_ON -> channels[ch].monoPolyMode = false
                    MidiCC.POLY_MODE_ON -> channels[ch].monoPolyMode = true
                }
            }
            MidiChannelStatus.PROGRAM ->
                channels[ch].program = evt.msb
            MidiChannelStatus.CAF ->
                channels[ch].caf = evt.msb
            MidiChannelStatus.PITCH_BEND ->
                channels[ch].pitchbend = ((evt.msb.toUnsigned() shl 7) + evt.lsb).toShort()
        }

        messageListeners.forEach { it.onMessage(evt) }
    }
}

private val midi1StandardRpnEnabled = BooleanArray(0x80 * 0x80) { false }.apply {
    this[MidiRpn.PITCH_BEND_SENSITIVITY] = true
    this[MidiRpn.FINE_TUNING] = true
    this[MidiRpn.COARSE_TUNING] = true
    this[MidiRpn.TUNING_PROGRAM] = true
    this[MidiRpn.TUNING_BANK_SELECT] = true
    this[MidiRpn.MODULATION_DEPTH] = true
}

class Midi1ControllerCatalog(
    val enabledRpns: BooleanArray = midi1StandardRpnEnabled.copyOf(),
    val enabledNrpns: BooleanArray = BooleanArray(0x80 * 0x80) { false }
) {
    fun enableAllNrpnMsbs() {
        (0 until 0x80).forEach { enabledNrpns[it * 0x80] = true }
    }
}


class Midi1SystemCommon {
    var mtcQuarterFrame: Byte = 0
    var songPositionPointer: Short = 0
    var songSelect: Byte = 0
}

class Midi1MachineChannel {
    val noteOnStatus = BooleanArray(128)
    val noteVelocity = ByteArray(128)
    val pafVelocity = ByteArray(128)
    val controls = ByteArray(128)
    // They need independent flag to indicate which was set currently.
    var omniMode: Boolean? = null
    var monoPolyMode: Boolean? = null
    // They store values sent by DTE (MSB+LSB), per index (MSB+LSB)
    val rpns = ShortArray(128 * 128) // only 5 should be used though
    val nrpns = ShortArray(128 * 128)
    var program: Byte = 0
    var caf: Byte = 0
    var pitchbend: Short = 8192
    var dteTarget: DteTarget = DteTarget.RPN
    private var dte_target_value: Byte = 0

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
            arr[target] = ((cur and 0x007F) + ((value.toUnsigned() and 0x7F) shl 7)).toShort()
        else
            arr[target] = ((cur and 0x3F80) + (value.toUnsigned() and 0x7F)).toShort()
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
