package dev.atsushieno.ktmidi.ci

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
class MidiCIDeviceConfiguration(var device: MidiCIDeviceInfo) {
    var capabilityInquirySupported: Byte = MidiCISupportedCategories.THREE_P
    var receivableMaxSysExSize: Int = MidiCIConstants.DEFAULT_RECEIVABLE_MAX_SYSEX_SIZE
    var maxSimultaneousPropertyRequests: Byte = MidiCIConstants.DEFAULT_MAX_SIMULTANEOUS_PROPERTY_REQUESTS
    var maxPropertyChunkSize: Int = MidiCIConstants.DEFAULT_MAX_PROPERTY_CHUNK_SIZE

    val initiator = MidiCIInitiatorConfiguration()
    val responder = MidiCIResponderConfiguration()

    // discovery initiator
    var outputPathId: Byte = 0
    var autoSendEndpointInquiry: Boolean = true
    var autoSendProfileInquiry: Boolean = true
    var autoSendPropertyExchangeCapabilitiesInquiry: Boolean = true

    // discovery responder
    var functionBlock: Byte = MidiCIConstants.NO_FUNCTION_BLOCK
    var productInstanceId: String = "ktmidi-ci" + (Random.nextInt() % 65536)

    // Profile Configuration
    val localProfiles: MutableList<MidiCIProfile> = mutableListOf()
}

@Serializable
class MidiCIInitiatorConfiguration(
    var autoSendGetResourceList: Boolean = true,
)

@Serializable
class MidiCIResponderConfiguration {

    // Property Exchange
    val propertyValues: MutableList<PropertyValue> = mutableListOf()
    val propertyMetadataList: MutableList<PropertyMetadata> = mutableListOf()

    // Process Inquiry
    var processInquirySupportedFeatures: Byte = MidiCIProcessInquiryFeatures.MIDI_MESSAGE_REPORT
    var midiMessageReportMessageDataControl: Byte = MidiMessageReportDataControl.Full
    var midiMessageReportSystemMessages: Byte = MidiMessageReportSystemMessagesFlags.All
    var midiMessageReportChannelControllerMessages: Byte = MidiMessageReportChannelControllerFlags.All
    var midiMessageReportNoteDataMessages: Byte = MidiMessageReportNoteDataFlags.All
}