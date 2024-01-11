package dev.atsushieno.ktmidi.ci

import kotlin.random.Random

class MidiCIDeviceConfiguration(
    var device: MidiCIDeviceInfo,
    var muid: Int = Random.nextInt() and 0x7F7F7F7F
    ) {
    var capabilityInquirySupported: Byte = MidiCISupportedCategories.THREE_P
    var receivableMaxSysExSize: Int = MidiCIConstants.DEFAULT_RECEIVABLE_MAX_SYSEX_SIZE
    var maxSimultaneousPropertyRequests: Byte = MidiCIConstants.DEFAULT_MAX_SIMULTANEOUS_PROPERTY_REQUESTS
    var maxPropertyChunkSize: Int = MidiCIConstants.DEFAULT_MAX_PROPERTY_CHUNK_SIZE
}


class MidiCIInitiatorConfiguration(
    val common: MidiCIDeviceConfiguration,

    var outputPathId: Byte = 0,
    var midiCIBufferSize: Int = 4096,
    var productInstanceId: String? = null,

    var autoSendEndpointInquiry: Boolean = true,
    var autoSendProfileInquiry: Boolean = true,
    var autoSendPropertyExchangeCapabilitiesInquiry: Boolean = true,
    var autoSendGetResourceList: Boolean = true,
    val profiles: MutableList<MidiCIProfile> = mutableListOf()
)


class MidiCIResponderConfiguration(
    val common: MidiCIDeviceConfiguration,

    var functionBlock: Byte = MidiCIConstants.NO_FUNCTION_BLOCK,
    var productInstanceId: String = "ktmidi-ci" + (Random.nextInt() % 65536),

    // Profile Configuration
    val profiles: MutableList<MidiCIProfile> = mutableListOf(),

    // Property Exchange

    // Process Inquiry
    var processInquirySupportedFeatures: Byte = 0,
    var midiMessageReportSystemMessages: Byte = 0,
    var midiMessageReportChannelControllerMessages: Byte = 0,
    var midiMessageReportNoteDataMessages: Byte = 0
)
