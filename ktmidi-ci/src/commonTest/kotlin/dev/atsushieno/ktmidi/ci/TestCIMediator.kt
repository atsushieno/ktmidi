package dev.atsushieno.ktmidi.ci

class TestCIMediator {
    private val initiatorSender = { data: List<Byte> -> responder.processInput(data) }
    private val responderSender = { data: List<Byte> -> initiator.processInput(data) }
    private val responderMidiMessageReportSender = { _: MidiMessageReportProtocol, _: List<Byte> -> }

    private val device = MidiCIDeviceInfo(0, 0, 0, 0,
        "TestDevice", "TestInitiatorFamily", "TestInitiatorModel", "0.0")
    private val common = MidiCIDeviceConfiguration(device)
    private val configI = MidiCIInitiatorConfiguration(common)
    private val configR = MidiCIResponderConfiguration(common)
    val initiator: MidiCIInitiator = MidiCIInitiator(19474 and 0x7F7F7F7F, configI, initiatorSender)
    val responder: MidiCIResponder = MidiCIResponder(19474 and 0x7F7F7F7F, configR, responderSender, responderMidiMessageReportSender)
}
