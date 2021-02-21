package dev.atsushieno.ktmidi

class MidiMachine
{
    private val event_received_handlers = arrayListOf<OnMidiEventListener>()

    fun addOnEventReceivedListener(listener: OnMidiEventListener)
    {
        event_received_handlers.add(listener)
    }

    fun removeOnEventReceivedListener(listener: OnMidiEventListener)
    {
        event_received_handlers.remove(listener)
    }

    var channels = Array<MidiMachineChannel> (16, { i -> MidiMachineChannel() })

    fun processEvent (evt: MidiEvent)
    {
        when (evt.eventType) {
            MidiEventType.NOTE_ON->
            channels [evt.channel.toUnsigned()].noteVelocity [evt.msb.toUnsigned()] = evt.lsb

            MidiEventType.NOTE_OFF->
            channels [evt.channel.toUnsigned()].noteVelocity [evt.msb.toUnsigned()] = 0
            MidiEventType.PAF->
            channels [evt.channel.toUnsigned()].pafVelocity [evt.msb.toUnsigned()] = evt.lsb
            MidiEventType.CC-> {
                // FIXME: handle RPNs and NRPNs by DTE
                when (evt.msb) {
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
            MidiEventType.PROGRAM->
            channels [evt.channel.toUnsigned()].program = evt.msb
            MidiEventType.CAF->
            channels [evt.channel.toUnsigned()].caf = evt.msb
            MidiEventType.PITCH ->
            channels [evt.channel.toUnsigned()].pitchbend = ((evt.msb.toUnsigned() shl 7) + evt.lsb).toShort()
        }
        for (receiver in event_received_handlers)
            receiver.onEvent (evt)
    }
}

class MidiMachineChannel
{
    val noteVelocity = ByteArray(128)
    val pafVelocity = ByteArray(128)
    val controls = ByteArray(128)
    val rpns = ShortArray(128) // only 5 should be used though
    val nrpns = ShortArray(128)
    var program : Byte = 0
    var caf : Byte = 0
    var pitchbend : Short = 8192
    var dteTarget : DteTarget = DteTarget.RPN
    private var dte_target_value : Byte = 0

    val rpnTarget : Short
        get () = ((controls [MidiCC.RPN_MSB.toUnsigned()].toUnsigned() shl 7) + controls [MidiCC.RPN_LSB.toUnsigned()]).toShort()


    fun processDte (value: Byte, isMsb: Boolean)
    {
        var arr : ShortArray
        when (dteTarget) {
            DteTarget.RPN-> {
                dte_target_value = controls[(if (isMsb) MidiCC.RPN_MSB else MidiCC.RPN_LSB).toUnsigned()]
                arr = rpns
            }
            DteTarget.NRPN-> {
                dte_target_value = controls[(if (isMsb) MidiCC.NRPN_MSB else MidiCC.NRPN_LSB).toUnsigned()]
                arr = nrpns
            }
        }
        var cur = arr [dte_target_value.toUnsigned()].toInt()
        if (isMsb)
            arr [dte_target_value.toUnsigned()] = (cur and 0x007F + ((value.toUnsigned() and 0x7F) shl 7)).toShort()
        else
        arr [dte_target_value.toUnsigned()] = (cur and 0x3FF0 + (value.toUnsigned() and 0x7F)).toShort()
    }

    fun processDteIncrement ()
    {
        when (dteTarget) {
            DteTarget.RPN -> rpns [dte_target_value.toUnsigned()]++
            DteTarget.NRPN -> nrpns [dte_target_value.toUnsigned()]++
        }
    }

    fun processDteDecrement ()
    {
        when (dteTarget) {
            DteTarget.RPN -> rpns [dte_target_value.toUnsigned()]--
            DteTarget.NRPN -> nrpns [dte_target_value.toUnsigned()]--
        }
    }
}

enum class DteTarget
{
    RPN,
    NRPN
}
