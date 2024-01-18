package dev.atsushieno.ktmidi.ci

class TestCIMediator {
    private val ciSender = { data: List<Byte> -> device.processInput(data) }
    private val midiMessageReportSender = { _: MidiMessageReportProtocol, _: List<Byte> -> }

    private val deviceInfo = MidiCIDeviceInfo(0, 0, 0, 0,
        "TestDevice", "TestInitiatorFamily", "TestInitiatorModel", "0.0")
    private val deviceConfig = MidiCIDeviceConfiguration(deviceInfo)
    private val device = MidiCIDevice(19474 and 0x7F7F7F7F, deviceConfig, ciSender, midiMessageReportSender)
    val initiator: MidiCIInitiator = device.initiator
    val responder: MidiCIResponder = device.responder
}
