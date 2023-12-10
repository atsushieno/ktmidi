package dev.atsushieno.ktmidi.ci

class TestCIMediator {
    val initiatorSender = { data: List<Byte> -> responder.processInput(data) }
    val responderSender = { data: List<Byte> -> initiator.processInput(data) }

    val initiator: MidiCIInitiator =
        MidiCIInitiator(initiatorSender, 0, 19474 and 0x7F7F7F7F)
    val responder: MidiCIResponder =
        MidiCIResponder(responderSender, 37564 and 0x7F7F7F7F)
}