package dev.atsushieno.ktmidi.ci

// This MIDI Message Reporter does nothing
internal class StubMidiMessageReporter : MidiMessageReporter {
    override val midiTransportProtocol = MidiMessageReportProtocol.Midi1Stream
    override fun reportMidiMessages(
        groupAddress: Byte,
        channelAddress: Byte,
        messageDataControl: Byte,
        midiMessageReportSystemMessages: Byte,
        midiMessageReportChannelControllerMessages: Byte,
        midiMessageReportNoteDataMessages: Byte
    ): Sequence<List<Byte>> = sequenceOf()
}