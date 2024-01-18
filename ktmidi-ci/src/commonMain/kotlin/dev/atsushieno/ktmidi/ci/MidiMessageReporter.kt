package dev.atsushieno.ktmidi.ci

/**
 * Implements Profile Inquiry MIDI Message Report feature.
 * `reportMidiMessages()` returns the specified MIDI messages.
 *
 * It is abstracted away in this module. A MIDI-CI device could make use of
 * ktmidi core module to facilitate implementation of this interface.
 */
interface MidiMessageReporter {
    val midiTransportProtocol: MidiMessageReportProtocol

    /**
     * Return a sequence of MIDI messages (can be split in any number of lists)
     */
    fun reportMidiMessages(
        groupAddress: Byte,
        channelAddress: Byte,
        messageDataControl: Byte,
        midiMessageReportSystemMessages: Byte,
        midiMessageReportChannelControllerMessages: Byte,
        midiMessageReportNoteDataMessages: Byte
    ): Sequence<List<Byte>>
}