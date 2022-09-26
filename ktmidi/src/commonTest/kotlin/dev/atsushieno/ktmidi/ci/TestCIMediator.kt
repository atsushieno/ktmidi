package dev.atsushieno.ktmidi.ci

class TestCIMediator {
    val initiatorSender = { data: List<Byte> -> responder.processInput(data) }
    val responderSender = { data: List<Byte> -> initiator.processInput(data) }

    val initiator: MidiCIInitiator =
        MidiCIInitiator(initiatorSender, muid = 19474)
    val responder: MidiCIResponder =
        MidiCIResponder(responderSender, muid = 37564)
}