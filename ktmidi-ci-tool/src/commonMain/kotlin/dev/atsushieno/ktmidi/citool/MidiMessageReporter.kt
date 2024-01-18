package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.Midi1Machine
import dev.atsushieno.ktmidi.ci.MidiMessageReportProtocol
import dev.atsushieno.ktmidi.ci.MidiMessageReporter
import dev.atsushieno.ktmidi.reportMidiMessages

class Midi1MessageReporter(
    private val ciOutputHandler: (midi1Messages: List<Byte>) -> Unit
) : MidiMessageReporter {
    override val midiTransportProtocol = MidiMessageReportProtocol.Midi1Stream

    val machine = Midi1Machine()

    override fun reportMidiMessages(
        groupAddress: Byte,
        channelAddress: Byte,
        messageDataControl: Byte,
        midiMessageReportSystemMessages: Byte,
        midiMessageReportChannelControllerMessages: Byte,
        midiMessageReportNoteDataMessages: Byte
    ): Sequence<List<Byte>> =
        machine.reportMidiMessages(channelAddress,
            messageDataControl,
            midiMessageReportSystemMessages,
            midiMessageReportChannelControllerMessages,
            midiMessageReportNoteDataMessages)
}
