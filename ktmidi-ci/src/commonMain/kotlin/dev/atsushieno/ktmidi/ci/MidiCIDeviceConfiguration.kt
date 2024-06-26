package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.propertycommonrules.CommonRulesPropertyMetadata
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * This class works as a top-level object for serializing the state of a MIDI-CI device.
 *
 * The actual serialization work is done by kotlinx.serialization (or anything that can handle `Serializable`).
 *
 * For ktmidi-ci-tool, the actual top-level serialization target is `SavedSettings` in that module
 * which may contain other information bits in the later versions.
 */
@Serializable
class MidiCIDeviceConfiguration {
    var deviceInfo = MidiCIDeviceInfo(0x123456,0x1234,0x5678,0x00000001,
            "atsushieno", "KtMidi", "KtMidi-CI-Tool", "0.1")

    var channelList = MidiCIChannelList()

    var jsonSchemaString: String = ""

    var capabilityInquirySupported: Byte = MidiCISupportedCategories.THREE_P
    var receivableMaxSysExSize: Int = MidiCIConstants.DEFAULT_RECEIVABLE_MAX_SYSEX_SIZE
    var maxSimultaneousPropertyRequests: Byte = MidiCIConstants.DEFAULT_MAX_SIMULTANEOUS_PROPERTY_REQUESTS
    var maxPropertyChunkSize: Int = MidiCIConstants.DEFAULT_MAX_PROPERTY_CHUNK_SIZE

    // The group number of this device itself.
    // It will be used when its initiator messages are sent over UMP transport.
    var group: Byte = 0

    // discovery initiator
    var outputPathId: Byte = 0
    var autoSendEndpointInquiry: Boolean = true
    var autoSendProfileInquiry: Boolean = true
    var autoSendPropertyExchangeCapabilitiesInquiry: Boolean = true
    var autoSendProcessInquiry: Boolean = true
    var autoSendGetResourceList: Boolean = true
    var autoSendGetDeviceInfo: Boolean = true

    // discovery responder
    var functionBlock: Byte = MidiCIConstants.NO_FUNCTION_BLOCK
    var productInstanceId: String = "ktmidi-ci" + (Random.nextInt() % 65536)

    // Profile Configuration
    val localProfiles: MutableList<MidiCIProfile> = mutableListOf()

    // Process Inquiry responder
    var processInquirySupportedFeatures: Byte = MidiCIProcessInquiryFeatures.MIDI_MESSAGE_REPORT
    var midiMessageReportMessageDataControl: Byte = MidiMessageReportDataControl.Full
    var midiMessageReportSystemMessages: Byte = MidiMessageReportSystemMessagesFlags.All
    var midiMessageReportChannelControllerMessages: Byte = MidiMessageReportChannelControllerFlags.All
    var midiMessageReportNoteDataMessages: Byte = MidiMessageReportNoteDataFlags.All

    // Property Exchange responder
    val propertyValues: MutableList<PropertyValue> = mutableListOf()
    // FIXME: Common Rules which does not look good for decoupling spec, but we cannot serialize
    //  MutableList<PropertyMetadata> (list of interface entries) using kotlinx.serialization...
    val propertyMetadataList: MutableList<CommonRulesPropertyMetadata> = mutableListOf()
}
