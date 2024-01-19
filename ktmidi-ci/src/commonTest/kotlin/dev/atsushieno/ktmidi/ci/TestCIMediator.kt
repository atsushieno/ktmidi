package dev.atsushieno.ktmidi.ci

class TestCIMediator {
    private val device1Sender: (List<Byte>)->Unit = { data: List<Byte> -> device2.processInput(data) }
    private val device2Sender: (List<Byte>)->Unit = { data: List<Byte> -> device1.processInput(data) }
    private val midiMessageReportSender = { _: MidiMessageReportProtocol, _: List<Byte> -> }

    private val deviceInfo = MidiCIDeviceInfo(0, 0, 0, 0,
        "TestDevice", "TestInitiatorFamily", "TestInitiatorModel", "0.0")
    private val deviceConfig = MidiCIDeviceConfiguration(deviceInfo)
    val device1 = MidiCIDevice(19474 and 0x7F7F7F7F, deviceConfig, device1Sender, midiMessageReportSender)
    val device2 = MidiCIDevice(37564 and 0x7F7F7F7F, deviceConfig, device2Sender, midiMessageReportSender)
    val initiator: MidiCIInitiator = device1.initiator
    val responder: MidiCIResponder = device2.responder
}
