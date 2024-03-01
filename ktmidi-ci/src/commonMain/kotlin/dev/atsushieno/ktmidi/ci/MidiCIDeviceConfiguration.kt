package dev.atsushieno.ktmidi.ci

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

    var capabilityInquirySupported: Byte = MidiCISupportedCategories.THREE_P
    var receivableMaxSysExSize: Int = MidiCIConstants.DEFAULT_RECEIVABLE_MAX_SYSEX_SIZE
    var maxSimultaneousPropertyRequests: Byte = MidiCIConstants.DEFAULT_MAX_SIMULTANEOUS_PROPERTY_REQUESTS
    var maxPropertyChunkSize: Int = MidiCIConstants.DEFAULT_MAX_PROPERTY_CHUNK_SIZE

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
    val propertyMetadataList: MutableList<PropertyMetadata> = mutableListOf()
}
