package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.Midi1Machine
import dev.atsushieno.ktmidi.Midi2Machine
import dev.atsushieno.ktmidi.ci.MidiMessageReportProtocol
import dev.atsushieno.ktmidi.ci.MidiMessageReporter
import dev.atsushieno.ktmidi.reportMidiMessages

class MidiMachineMessageReporter : MidiMessageReporter {
    override val midiTransportProtocol
        get() = configuredMidiTransportProtocol

    var configuredMidiTransportProtocol = MidiMessageReportProtocol.Midi1Stream

    val midi1Machine = Midi1Machine()
    val midi2Machine = Midi2Machine()

    override fun reportMidiMessages(
        groupAddress: Byte,
        channelAddress: Byte,
        messageDataControl: Byte,
        midiMessageReportSystemMessages: Byte,
        midiMessageReportChannelControllerMessages: Byte,
        midiMessageReportNoteDataMessages: Byte
    ): Sequence<List<Byte>> =
        if (configuredMidiTransportProtocol == MidiMessageReportProtocol.Ump)
            TODO("FIXME: implement")
        else
            midi1Machine.reportMidiMessages(channelAddress,
                messageDataControl,
                midiMessageReportSystemMessages,
                midiMessageReportChannelControllerMessages,
                midiMessageReportNoteDataMessages)
}
