package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.MidiCIProtocolType
import dev.atsushieno.ktmidi.MidiCIProtocolValue
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

data class DeviceDetails(val manufacturer: Int = 0, val family: Short = 0, val modelNumber: Short = 0, val softwareRevisionLevel: Int = 0) {
    companion object {
        val empty = DeviceDetails()
    }
}

enum class MidiCIInitiatorState {
    Initial,
    DISCOVERY_SENT,
    DISCOVERED,
    NEW_PROTOCOL_SENT, // almost no chance to stay at the state
    TEST_SENT,
    ESTABLISHED
}

object MidiCIDiscoveryCategoryFlags {
    const val None: Byte = 0
    const val ProtocolNegotiation: Byte = 1 // Deprecated in MIDI-CI 1.2
    const val ProfileConfiguration: Byte = 2
    const val PropertyExchange: Byte = 4
    const val ProcessInquiry: Byte = 8
    // I'm inclined to say "All", but that may change in the future and it indeed did.
    // Even worse, the definition of those Three Ps had changed...
    const val ThreePs: Byte = (ProfileConfiguration + PropertyExchange + ProcessInquiry).toByte()
}

// It is used to determine default authority level
// Note that they are deprecated in MIDI-CI 1.2.
object MidiCIAuthorityLevelBasis {
    const val NodeServer: Byte = 0x60 // to 0x6F
    const val Gateway: Byte = 0x50 // to 0x5F
    const val Translator: Byte = 0x40 // to 0x4F
    const val Endpoint: Byte = 0x30 // to 0x3F
    const val EventProcessor: Byte = 0x20 // to 0x2F
    const val Transport: Byte = 0x10 // to 0x1F
}

object MidiCISystem {
    @OptIn(ExperimentalTime::class)
    var timeSource: TimeSource = TimeSource.Monotonic
}

object MidiCIConstants {
    const val UNIVERSAL_SYSEX: Byte = 0x7E
    const val UNIVERSAL_SYSEX_SUB_ID_MIDI_CI: Byte = 0x0D

    const val CI_VERSION_AND_FORMAT: Byte = 0x2
    const val PROPERTY_EXCHANGE_MAJOR_VERSION: Byte = 0
    const val PROPERTY_EXCHANGE_MINOR_VERSION: Byte = 0

    const val ENDPOINT_STATUS_PRODUCT_INSTANCE_ID: Byte = 0

    const val DEFAULT_RECEIVABLE_MAX_SYSEX_SIZE = 4096

    const val DEVICE_ID_MIDI_PORT: Byte = 0x7F

    const val NO_FUNCTION_BLOCK: Byte = 0x7F
    const val WHOLE_FUNCTION_BLOCK: Byte = 0x7F

    val Midi1ProtocolTypeInfo = MidiCIProtocolTypeInfo(MidiCIProtocolType
        .MIDI1.toByte(), MidiCIProtocolValue.MIDI1.toByte(), 0, 0, 0)
    val Midi2ProtocolTypeInfo = MidiCIProtocolTypeInfo(MidiCIProtocolType
        .MIDI2.toByte(), MidiCIProtocolValue.MIDI2_V1.toByte(), 0, 0, 0)
    // The list is ordered from most preferred protocol type info, as per MIDI-CI spec. section 6.5 describes.
    val Midi2ThenMidi1Protocols = listOf(Midi2ProtocolTypeInfo, Midi1ProtocolTypeInfo)
    val Midi1ThenMidi2Protocols = listOf(Midi1ProtocolTypeInfo, Midi2ProtocolTypeInfo)
}

object CINakStatus {
    const val Nak: Byte = 0
    const val MessageNotSupported: Byte = 1
    const val CIVersionNotSupported: Byte = 2
    const val TargetNotInUse: Byte = 3 // Target = Channel/Group/FunctionBlock
    const val ProfileNotSupportedOnTarget: Byte = 4
    const val TerminateInquiry: Byte = 0x20
    const val PropertyExchangeChunksAreOutOfSequence: Byte = 0x21
    const val ErrorRetrySuggested: Byte = 0x40
    const val MalformedMessage: Byte = 0x41
    const val Timeout: Byte = 0x42
    const val TimeoutRetrySuggested = 0x43
}

/*
    Typical MIDI-CI processing flow

    - MidiCIInitiator.sendDiscovery()
    - MidiCIResponder.processDiscovery()
    - MidiCIInitiator.requestProfiles()
    - MidiCIResponder.processProfileInquiry()
    - MidiCIInitiator.setNewProtocol() - no matching reply from responder
    - MidiCIInitiator.testNewProtocol()
    - MidiCIResponder.processTestNewProtocol()
    - MidiCIInitiator.confirmProtocol()

 These classes are responsible only for one input/output connection pair.
 The argument `sendOutput` takes a sysex bytestream which is NOT specific to MIDI 1.0 bytestream (i.e. it should
 support sysex7 UMPs) and thus does NOT contain F0 and F7.
 Same goes for `processInput()` function.

*/

class MidiCIInitiator(private val sendOutput: (data: List<Byte>) -> Unit,
                      val outputPathId: Byte = 0,
                      val muid: Int = Random.nextInt() and 0x7F7F7F7F) {

    var device: DeviceDetails = DeviceDetails.empty
    var midiCIBufferSize = 1024
    var receivableMaxSysExSize = MidiCIConstants.DEFAULT_RECEIVABLE_MAX_SYSEX_SIZE
    var productInstanceId: String? = null

    var state: MidiCIInitiatorState = MidiCIInitiatorState.Initial

    private var discoveredDevice: DeviceDetails? = null

    val profiles = mutableListOf<MidiCIProfileId>()

    var establishedMaxSimultaneousPropertyRequests: Byte? = null

    // Input handlers

    private val defaultProcessDiscoveryResponse = { device: DeviceDetails, sourceMUID: Int, destinationMUID: Int ->
        if (destinationMUID == muid) {
            state = MidiCIInitiatorState.DISCOVERED

            // If successfully discovered, continue to protocol promotion to MIDI 2.0
            discoveredDevice = device
            requestProfiles(0x7F, sourceMUID)
        }
    }
    var processDiscoveryResponse = defaultProcessDiscoveryResponse

    private val defaultProcessInvalidateMUID = { sourceMUID: Int, destinationMUID: Int, muidToInvalidate: Int ->
        if (muidToInvalidate == muid) {
            state = MidiCIInitiatorState.Initial
        }
    }
    var processInvalidateMUID = defaultProcessInvalidateMUID

    var onAck: (sourceMUID: Int, destinationMUID: Int, originalTransactionSubId: Byte, nakStatusCode: Byte, nakStatusData: Byte, nakDetailsForEachSubIdClassification: List<Byte>, messageLength: UShort, messageText: List<Byte>) -> Unit = { _,_,_,_,_,_,_,_ -> }
    var onNak: (sourceMUID: Int, destinationMUID: Int, originalTransactionSubId: Byte, nakStatusCode: Byte, nakStatusData: Byte, nakDetailsForEachSubIdClassification: List<Byte>, messageLength: UShort, messageText: List<Byte>) -> Unit = { _,_,_,_,_,_,_,_ -> }
    private fun defaultProcessAckNak(isNak: Boolean, sourceMUID: Int, destinationMUID: Int, originalTransactionSubId: Byte, statusCode: Byte, statusData: Byte, detailsForEachSubIdClassification: List<Byte>, messageLength: UShort, messageText: List<Byte>) {
        if (isNak)
            onNak(sourceMUID, destinationMUID, originalTransactionSubId, statusCode, statusData, detailsForEachSubIdClassification, messageLength, messageText)
        else
            onAck(sourceMUID, destinationMUID, originalTransactionSubId, statusCode, statusData, detailsForEachSubIdClassification, messageLength, messageText)
    }
    private val defaultProcessAck = { sourceMUID: Int, destinationMUID: Int, originalTransactionSubId: Byte, statusCode: Byte, statusData: Byte, detailsForEachSubIdClassification: List<Byte>, messageLength: UShort, messageText: List<Byte> ->
        defaultProcessAckNak(false, sourceMUID, destinationMUID, originalTransactionSubId, statusCode, statusData, detailsForEachSubIdClassification, messageLength, messageText)
    }
    var processAck = defaultProcessAck
    private val defaultProcessNak = { sourceMUID: Int, destinationMUID: Int, originalTransactionSubId: Byte, statusCode: Byte, statusData: Byte, detailsForEachSubIdClassification: List<Byte>, messageLength: UShort, messageText: List<Byte> ->
        defaultProcessAckNak(true, sourceMUID, destinationMUID, originalTransactionSubId, statusCode, statusData, detailsForEachSubIdClassification, messageLength, messageText)
    }
    var processNak = defaultProcessNak

    private val defaultProcessEndpointMessageResponse = { sourceMUID: Int, destinationMUID: Int, status: Byte, data: List<Byte> ->
        if (status == MidiCIConstants.ENDPOINT_STATUS_PRODUCT_INSTANCE_ID)
            productInstanceId = data.toByteArray().decodeToString() // FIXME: verify that it is only ASCII chars?
    }
    var processEndpointMessageResponse = defaultProcessEndpointMessageResponse

    /*

    Protocol Negotiation is deprecated. We do not send any of those anymore.

    private val defaultProcessReplyToInitiateProtocolNegotiation = { supportedProtocols: List<MidiCIProtocolTypeInfo>, sourceMUID: Int ->
        val protocol = preferredProtocols.firstOrNull { i -> supportedProtocols.any { i.type == it.type && i.version == it.version }}
        if (protocol != null) {
            // we set state before sending the MIDI data as it may process the rest of the events synchronously through the end...
            state = MidiCIInitiatorState.NEW_PROTOCOL_SENT
            setNewProtocol(sourceMUID, authorityLevel, protocol)
            currentMidiProtocol = protocol
            protocolTestData = (0 until 48).map { it.toByte()}
            state = MidiCIInitiatorState.TEST_SENT
            testNewProtocol(sourceMUID, authorityLevel, protocolTestData!!)
        }
    }
    var processReplyToInitiateProtocolNegotiation = defaultProcessReplyToInitiateProtocolNegotiation

    private val defaultProcessTestProtocolReply = { sourceMUID: Int, testData: List<Byte> ->
        if (testData.toByteArray() contentEquals protocolTestData?.toByteArray()) {
            protocolTested = true
            state = MidiCIInitiatorState.ESTABLISHED
            confirmProtocol(sourceMUID, authorityLevel)
        }
    }
    var processTestProtocolReply = defaultProcessTestProtocolReply
    */

    var processProfileInquiryResponse: (destinationChannel: Byte, sourceMUID: Int, profileSet: List<Pair<MidiCIProfileId, Boolean>>) -> Unit = { _, _, _ ->
        // do nothing
    }

    private val defaultProcessProfileAddedReport: (profileId: MidiCIProfileId) -> Unit = { profileId -> profiles.add(profileId) }
    var processProfileAddedReport = defaultProcessProfileAddedReport

    private val defaultProcessProfileRemovedReport: (profileId: MidiCIProfileId) -> Unit = { profileId -> profiles.remove(profileId) }
    var processProfileRemovedReport = defaultProcessProfileRemovedReport

    private val defaultProcessGetMaxSimultaneousPropertyReply: (destinationChannel: Byte, sourceMUID: Int, destinationMUID: Int, max: Byte) -> Unit = { _, _, _, max ->
        establishedMaxSimultaneousPropertyRequests = max
    }
    var processGetMaxSimultaneousPropertyReply = defaultProcessGetMaxSimultaneousPropertyReply

    // Discovery

    fun sendDiscovery(ciCategorySupported: Byte = MidiCIDiscoveryCategoryFlags.ThreePs) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIDiscovery(buf, MidiCIConstants.CI_VERSION_AND_FORMAT, muid, device.manufacturer, device.family, device.modelNumber,
            device.softwareRevisionLevel, ciCategorySupported, receivableMaxSysExSize, outputPathId)
        // we set state before sending the MIDI data as it may process the rest of the events synchronously through the end...
        state = MidiCIInitiatorState.DISCOVERY_SENT
        sendOutput(buf)
    }

    fun sendEndpointMessage(targetMuid: Int, status: Byte) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIEndpointMessage(buf, MidiCIConstants.CI_VERSION_AND_FORMAT, muid, targetMuid, status)
        sendOutput(buf)
    }

    /*
    // Protocol Negotiation - we do not send them anymore.

    fun initiateProtocolNegotiation(destinationMUID: Int, authorityLevel: Byte, protocolTypes: List<MidiCIProtocolTypeInfo>) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIProtocolNegotiation(buf, false, muid, destinationMUID, authorityLevel, protocolTypes)
        sendOutput(buf)
    }

    fun setNewProtocol(destinationMUID: Int, authorityLevel: Byte, newProtocolType: MidiCIProtocolTypeInfo) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIProtocolSet(buf, muid, destinationMUID, authorityLevel, newProtocolType)
        sendOutput(buf)
    }

    fun testNewProtocol(destinationMUID: Int, authorityLevel: Byte, testData48Bytes: List<Byte>) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIProtocolTest(buf, true, muid, destinationMUID, authorityLevel, testData48Bytes)
        sendOutput(buf)
    }

    fun confirmProtocol(destinationMUID: Int, authorityLevel: Byte) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIProtocolConfirmEstablished(buf, muid, destinationMUID, authorityLevel)
        sendOutput(buf)
    }
    */

    // Profile Configuration

    fun requestProfiles(destinationChannelOr7F: Byte, destinationMUID: Int) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIProfileInquiry(buf, destinationChannelOr7F, muid, destinationMUID)
        sendOutput(buf)
    }

    // Property Exchange
    // TODO: implement the rest (it's going to take long time, read the entire Common Rules for PE, support split chunks in reader and writer, and JSON serializers)

    fun requestPropertyExchangeCapabilities(destinationChannelOr7F: Byte, destinationMUID: Int, maxSimultaneousPropertyRequests: Byte) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIPropertyGetCapabilities(buf, destinationChannelOr7F, false, muid, destinationMUID, maxSimultaneousPropertyRequests)
        sendOutput(buf)
    }

    fun requestHasPropertyData(destinationChannelOr7F: Byte, destinationMUID: Int, header: List<Byte>, body: List<Byte>) {
        TODO("FIXME: implement")
    }

    // Reply handler

    fun processInput(data: List<Byte>) {
        if (data[0] != 0x7E.toByte() || data[2] != 0xD.toByte())
            return // not MIDI-CI sysex
        when (data[3]) {
            /*
            // Protocol Negotiation - we ignore them
            FIXME: we should send back NAK
            CIFactory.SUB_ID_2_PROTOCOL_NEGOTIATION_REPLY -> {
                // Reply to Initiate Protocol Negotiation
                processReplyToInitiateProtocolNegotiation(
                    CIRetrieval.midiCIGetSupportedProtocols(data),
                    CIRetrieval.midiCIGetSourceMUID(data))
            }
            CIFactory.SUB_ID_2_TEST_NEW_PROTOCOL_R2I -> {
                // Test New Protocol Responder to Initiator
                processTestProtocolReply(
                    CIRetrieval.midiCIGetSourceMUID(data),
                    CIRetrieval.midiCIGetTestData(data))
            }
            */
            // Discovery
            CIFactory.SUB_ID_2_DISCOVERY_REPLY -> {
                // Reply to Discovery
                processDiscoveryResponse(
                    CIRetrieval.midiCIGetDeviceDetails(data),
                    CIRetrieval.midiCIGetSourceMUID(data),
                    CIRetrieval.midiCIGetDestinationMUID(data))
            }
            CIFactory.SUB_ID_2_ENDPOINT_MESSAGE_REPLY -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val destinationMUID = CIRetrieval.midiCIGetDestinationMUID(data)
                val status = data[13]
                val dataLength = data[14] + (data[15].toInt() shl 7)
                val dataValue = data.drop(16).take(dataLength)
                processEndpointMessageResponse(sourceMUID, destinationMUID, status, dataValue)
            }
            CIFactory.SUB_ID_2_INVALIDATE_MUID -> {
                // Invalid MUID
                processInvalidateMUID(CIRetrieval.midiCIGetSourceMUID(data),
                    0x7F7F7F7F,
                    CIRetrieval.midiCIGetMUIDToInvalidate(data)
                    )
            }
            CIFactory.SUB_ID_2_ACK -> {
                // ACK MIDI-CI
                processAck(
                    CIRetrieval.midiCIGetSourceMUID(data),
                    CIRetrieval.midiCIGetDestinationMUID(data),
                    data[13],
                    data[14],
                    data[15],
                    data.drop(16).take(5),
                    (data[21] + (data[22].toInt() shl 7)).toUShort(),
                    data.drop(23)
                )
            }
            CIFactory.SUB_ID_2_NAK -> {
                // NAK MIDI-CI
                processNak(
                    CIRetrieval.midiCIGetSourceMUID(data),
                    CIRetrieval.midiCIGetDestinationMUID(data),
                    data[13],
                    data[14],
                    data[15],
                    data.drop(16).take(5),
                    (data[21] + (data[22].toInt() shl 7)).toUShort(),
                    data.drop(23)
                )
            }
            // Profile Configuration
            CIFactory.SUB_ID_2_PROFILE_INQUIRY_REPLY -> {
                processProfileInquiryResponse(CIRetrieval.midiCIGetDestination(data),
                    CIRetrieval.midiCIGetSourceMUID(data),
                    CIRetrieval.midiCIGetProfileSet(data))
            }
            CIFactory.SUB_ID_2_PROFILE_ADDED_REPORT -> {
                processProfileAddedReport(CIRetrieval.midiCIGetProfileId(data))
            }
            CIFactory.SUB_ID_2_PROFILE_REMOVED_REPORT -> {
                processProfileRemovedReport(CIRetrieval.midiCIGetProfileId(data))
            }
            // FIXME: support set profile on/off messages

            // Property Exchange
            CIFactory.SUB_ID_2_PROPERTY_CAPABILITIES_REPLY -> {
                processGetMaxSimultaneousPropertyReply(
                    CIRetrieval.midiCIGetDestination(data),
                    CIRetrieval.midiCIGetSourceMUID(data),
                    CIRetrieval.midiCIGetSourceMUID(data),
                    CIRetrieval.midiCIGetMaxPropertyRequests(data))
            }
            CIFactory.SUB_ID_2_PROPERTY_HAS_DATA_REPLY -> {
                // Reply to Property Exchange Capabilities
                TODO("Implement")
            }
            CIFactory.SUB_ID_2_PROPERTY_GET_DATA_REPLY -> {
                // Reply to Property Exchange Capabilities
                TODO("Implement")
            }
            CIFactory.SUB_ID_2_PROPERTY_SET_DATA_REPLY -> {
                // Reply to Property Exchange Capabilities
                TODO("Implement")
            }
            CIFactory.SUB_ID_2_PROPERTY_SUBSCRIBE_REPLY -> {
                // Reply to Property Exchange Capabilities
                TODO("Implement")
            }
        }
    }
}

/**
 * This class is responsible only for one input/output connection pair
 *
 * The argument `sendOutput` takes a sysex bytestream which is NOT specific to MIDI 1.0 bytestream (i.e. it should
 * support sysex7 UMPs) and thus does NOT contain F0 and F7.
 * Same goes for `processInput()` function.
 *
 */
class MidiCIResponder(val device: MidiCIDeviceInfo,
                      private val sendOutput: (data: List<Byte>) -> Unit,
                      private val muid: Int = Random.nextInt() and 0x7F7F7F7F) {

    var capabilityInquirySupported = MidiCIDiscoveryCategoryFlags.ThreePs
    var receivableMaxSysExSize = MidiCIConstants.DEFAULT_RECEIVABLE_MAX_SYSEX_SIZE
    var supportedProtocols: List<MidiCIProtocolTypeInfo> = MidiCIConstants.Midi2ThenMidi1Protocols
    val profileSet: MutableList<Pair<MidiCIProfileId,Boolean>> = mutableListOf()
    var functionBlock: Byte = MidiCIConstants.NO_FUNCTION_BLOCK
    var productInstanceId: String? = null
    var maxSimultaneousPropertyRequests: Byte = 1

    var midiCIBufferSize = 4096

    // smaller value of initiator's maxSimulutaneousPropertyRequests vs. this.maxSimulutaneousPropertyRequests upon PEx inquiry request
    // FIXME: enable this when we start supporting Property Exchange.
    //var establishedMaxSimulutaneousPropertyRequests: Byte? = null

    val sendDiscoveryReplyForInquiry: (initiatorMUID: Int, initiatorOutputPath: Byte) -> Unit = { initiatorMUID, initiatorOutputPath ->
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIDiscoveryReply(
        dst, MidiCIConstants.CI_VERSION_AND_FORMAT, muid, initiatorMUID,
        device.manufacturerId, device.familyId, device.modelId, device.versionId,
        capabilityInquirySupported, receivableMaxSysExSize,
        initiatorOutputPath, functionBlock
        ))
    }
    var processDiscovery = sendDiscoveryReplyForInquiry

    val sendEndpointReplyForInquiry: (initiatorMUID: Int, destinationMUID: Int, status: Byte) -> Unit = { initiatorMUID, destinationMUID, status ->
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        val prodId = productInstanceId
        sendOutput(CIFactory.midiCIEndpointMessageReply(
            dst, MidiCIConstants.CI_VERSION_AND_FORMAT, muid, initiatorMUID, status,
            if (status == MidiCIConstants.ENDPOINT_STATUS_PRODUCT_INSTANCE_ID && prodId != null) prodId.toByteArray(Charsets.ISO_8859_1).toList() else listOf() // FIXME: verify that it is only ASCII chars?
            ))
    }
    var processEndpointMessage = sendEndpointReplyForInquiry

    val sendAck: (sourceMUID: Int) -> Unit = { sourceMUID ->
        // FIXME: we need to implement some sort of state management for each initiator
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIAckNak(dst, false, MidiCIConstants.DEVICE_ID_MIDI_PORT,
            MidiCIConstants.CI_VERSION_AND_FORMAT, muid, sourceMUID, CIFactory.SUB_ID_2_INVALIDATE_MUID, 0, 0, listOf(), listOf()))
    }
    var processInvalidateMUID = { sourceMUID: Int, targetMUID: Int -> sendAck(sourceMUID) }

    // Profile Configuration

    val sendProfileReplyForInquiry: (source: Byte, initiatorMUID: Int, destinationMUID: Int) -> Unit = { source, sourceMUID, destinationMUID ->
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIProfileInquiryReply(dst, source, muid, sourceMUID,
            profileSet.filter { it.second }.map { it.first },
            profileSet.filter { !it.second }.map { it.first }))
    }
    var processProfileInquiry = sendProfileReplyForInquiry

    var onProfileSet: (profile: MidiCIProfileId, enabled: Boolean) -> Unit = { _, _ -> }

    private val defaultProcessSetProfile = { destinationChannel: Byte, initiatorMUID: Int, destinationMUID: Int, profile: MidiCIProfileId, enabled: Boolean ->
        val newEntry = Pair(profile, enabled)
        val existing = profileSet.indexOfFirst { it.first.mid1_7e == profile.mid1_7e &&
                it.first.mid2_bank == profile.mid2_bank && it.first.mid3_number == profile.mid3_number &&
                it.first.msi1_version == profile.msi1_version && it.first.msi2_level == profile.msi2_level }
        if (existing >= 0) {
            profileSet[existing] = newEntry
        }
        else
            profileSet.add(newEntry)
        onProfileSet(profile, enabled)
        enabled
    }
    /**
     * Arguments
     * - destinationChannel: Byte - target channel
     * - initiatorMUID: Int - points to the initiator MUID
     * - destinationMUID: Int - should point to MUID of this responder
     * - profile: MidiCIProfileId - the target profile
     * - enabled: Boolean - true to enable, false to disable
     * Return true if enabled, or false if disabled. It will be reported back to Initiator
     */
    var processSetProfile = defaultProcessSetProfile

    var processProfileSpecificData: (sourceChannel: Byte, sourceMUID: Int, destinationMUID: Int, profileId: MidiCIProfileId, data: List<Byte>) -> Unit = { _, _, _, _, _ -> }

    // Property Exchange

    // Handling invalid messages

    val sendNakForUnknownCIMessage: (data: List<Byte>) -> Unit = { data ->
        val source = data[1]
        val originalSubId = data[3]
        val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
        val destinationMUID = CIRetrieval.midiCIGetDestinationMUID(data)
        val nak = MidiCIAckNakData(source, sourceMUID, destinationMUID, originalSubId, CINakStatus.MessageNotSupported, 0, listOf(), listOf())
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIAckNak(dst, true, nak))
    }
    var processUnknownCIMessage = sendNakForUnknownCIMessage

    // Property Exchange

    val sendPropertyCapabilitiesReply: (destination: Byte, sourceMUID: Int, destinationMUID: Int, max: Byte) -> Unit = { destination, sourceMUID, destinationMUID, max ->
        val establishedMaxSimultaneousPropertyRequests = if (max > maxSimultaneousPropertyRequests) maxSimultaneousPropertyRequests else max
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIPropertyGetCapabilities(dst, destination, true, muid, sourceMUID, establishedMaxSimultaneousPropertyRequests!!))

    }
    var processPropertyCapabilitiesInquiry = sendPropertyCapabilitiesReply

    val propertyService = CommonPropertyService(device)

    var getPropertyJson: (header: Json.JsonValue) -> Pair<Json.JsonValue,Json.JsonValue> = { propertyService.getPropertyData(it) }
    var setPropertyJson: (header: Json.JsonValue, body: Json.JsonValue) -> Json.JsonValue = { header, body -> propertyService.setPropertyData(header, body) }
    var subscribeJson: (sourceMUID: Int, header: Json.JsonValue) -> Json.JsonValue = { sourceMUID, header -> propertyService.subscribe(sourceMUID, header) }

    var getProperty: (sourceMUID: Int, destinationMUID: Int, requestId: Byte, header: List<Byte>) -> Pair<Json.JsonValue,Json.JsonValue> = {_,_,_,header ->
        val jsonInquiry = Json.parse(PropertyCommonConverter.decodeASCIIToString(header.toByteArray().decodeToString()))
        getPropertyJson(jsonInquiry)
    }
    var setProperty: (sourceMUID: Int, destinationMUID: Int, requestId: Byte, header: List<Byte>, data: List<Byte>) -> Json.JsonValue = {_,_,_,header,body ->
        val jsonHeader = Json.parse(PropertyCommonConverter.decodeASCIIToString(header.toByteArray().decodeToString()))
        val jsonBody = Json.parse(PropertyCommonConverter.decodeASCIIToString(body.toByteArray().decodeToString()))
        setPropertyJson(jsonHeader, jsonBody)
    }
    var subscribeProperty: (sourceMUID: Int, destinationMUID: Int, requestId: Byte, header: List<Byte>) -> Json.JsonValue = {sourceMUID, _, _, header ->
        val jsonInquiry = Json.parse(PropertyCommonConverter.decodeASCIIToString(header.toByteArray().decodeToString()))
        subscribeJson(sourceMUID, jsonInquiry)
    }

    val sendReplyToGetPropertyData: (sourceMUID: Int, destinationMUID: Int, requestId: Byte, header: List<Byte>) -> Unit = { sourceMUID, destinationMUID, requestId, header ->
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        val result = getProperty(sourceMUID, destinationMUID, requestId, header)
        val replyHeader = PropertyCommonConverter.encodeStringToASCII(Json.serialize(result.first)).toByteArray().toList()
        val replyBody = PropertyCommonConverter.encodeStringToASCII(Json.serialize(result.second)).toByteArray().toList()
        CIFactory.midiCIPropertyChunks(dst, CIFactory.SUB_ID_2_PROPERTY_GET_DATA_REPLY,
            muid, sourceMUID, requestId, replyHeader, replyBody).forEach {
            sendOutput(it)
        }
    }
    var processGetPropertyData = sendReplyToGetPropertyData

    private val defaultProcessSetPropertyData: (sourceMUID: Int, destinationMUID: Int, requestId: Byte, header: List<Byte>, numChunks: Int, chunkIndex: Int, data: List<Byte>) -> Unit = { sourceMUID, destinationMUID, requestId, header, numChunks, chunkIndex, data ->
        // FIXME: implement split chunk manager
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        if (numChunks == 1 && chunkIndex == 1) {
            setProperty(sourceMUID, destinationMUID, requestId, header, data)
            CIFactory.midiCIPropertyChunks(dst, CIFactory.SUB_ID_2_PROPERTY_GET_DATA_REPLY,
                muid, sourceMUID, requestId, header, data)
                .forEach { sendOutput(it) }
        } // FIXME: else -> return NAK saying that we cannot handle it
    }
    var processSetPropertyData = defaultProcessSetPropertyData

    private val defaultProcessSubscribe: (sourceMUID: Int, destinationMUID: Int, requestId: Byte, header: List<Byte>) -> Unit = { sourceMUID, destinationMUID, requestId, header ->
        subscribeProperty(sourceMUID, destinationMUID, requestId, header)
    }
    var processSubscribe = defaultProcessSubscribe

    fun processInput(data: List<Byte>) {
        if (data[0] != 0x7E.toByte() || data[2] != 0xD.toByte())
            return // not MIDI-CI sysex
        when (data[3]) {
            // Discovery
            CIFactory.SUB_ID_2_DISCOVERY_INQUIRY -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                // only available in MIDI-CI 1.2 or later.
                val initiatorOutputPath = if (data.size > 29) data[29] else 0
                processDiscovery(sourceMUID, initiatorOutputPath)
            }

            CIFactory.SUB_ID_2_ENDPOINT_MESSAGE_INQUIRY -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val destinationMUID = CIRetrieval.midiCIGetDestinationMUID(data)
                // only available in MIDI-CI 1.2 or later.
                val status = data[13]
                processEndpointMessage(sourceMUID, destinationMUID, status)
            }

            CIFactory.SUB_ID_2_INVALIDATE_MUID -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val targetMUID = CIRetrieval.midiCIGetMUIDToInvalidate(data)
                processInvalidateMUID(sourceMUID, targetMUID)
            }

            // Profile Configuration

            CIFactory.SUB_ID_2_PROFILE_INQUIRY -> {
                val source = data[1]
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val destinationMUID = CIRetrieval.midiCIGetDestinationMUID(data)
                processProfileInquiry(source, sourceMUID, destinationMUID)
            }

            CIFactory.SUB_ID_2_SET_PROFILE_ON, CIFactory.SUB_ID_2_SET_PROFILE_OFF -> {
                var enabled = data[3] == CIFactory.SUB_ID_2_SET_PROFILE_ON
                val destination = CIRetrieval.midiCIGetDestination(data)
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val destinationMUID = CIRetrieval.midiCIGetDestinationMUID(data)
                val profileId = CIRetrieval.midiCIGetProfileId(data)
                enabled = processSetProfile(destination, sourceMUID, destinationMUID, profileId, enabled)

                val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
                CIFactory.midiCIProfileReport(dst, destination, enabled, muid, profileId)
                sendOutput(dst)
            }

            CIFactory.SUB_ID_2_PROFILE_SPECIFIC_DATA -> {
                val sourceChannel = CIRetrieval.midiCIGetDestination(data)
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val destinationMUID = CIRetrieval.midiCIGetDestinationMUID(data)
                val profileId = CIRetrieval.midiCIGetProfileId(data)
                val dataLength = CIRetrieval.midiCIGetProfileSpecificDataSize(data)
                processProfileSpecificData(sourceChannel, sourceMUID, destinationMUID, profileId, data.drop(21).take(dataLength))
            }

            // Property Exchange

            CIFactory.SUB_ID_2_PROPERTY_CAPABILITIES_INQUIRY -> {
                val destination = CIRetrieval.midiCIGetDestination(data)
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val destinationMUID = CIRetrieval.midiCIGetDestinationMUID(data)
                val max = CIRetrieval.midiCIGetMaxPropertyRequests(data)
                
                processPropertyCapabilitiesInquiry(destination, sourceMUID, destinationMUID, max)
            }

            CIFactory.SUB_ID_2_PROPERTY_GET_DATA_INQUIRY -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val destinationMUID = CIRetrieval.midiCIGetDestinationMUID(data)
                val requestId = data[13]
                val headerSize = data[14] + (data[15].toInt() shl 7)
                val header = data.drop(16).take(headerSize)
                val pos = 16 + headerSize
                val numChunks = data[pos] + (data[pos + 1].toInt() shl 7)
                val chunkIndex = data[pos + 2] + (data[pos + 3].toInt() shl 7)
                val dataSize = data[pos + 4] + (data[pos + 5].toInt() shl 7)
                if (numChunks == 1 && chunkIndex == 1 && dataSize == 0) {
                    processGetPropertyData(sourceMUID, destinationMUID, requestId, header)
                } // FIXME: else -> error handling
            }

            CIFactory.SUB_ID_2_PROPERTY_SET_DATA_INQUIRY -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val destinationMUID = CIRetrieval.midiCIGetDestinationMUID(data)
                val requestId = data[13]
                val headerSize = data[14] + (data[15].toInt() shl 7)
                val header = data.drop(16).take(headerSize)
                val pos = 16 + headerSize
                val numChunks = data[pos] + (data[pos + 1].toInt() shl 7)
                val chunkIndex = data[pos + 2] + (data[pos + 3].toInt() shl 7)
                val dataSize = data[pos + 4] + (data[pos + 5].toInt() shl 7)
                val propData = data.drop (pos + 6).take(dataSize)
                processSetPropertyData(sourceMUID, destinationMUID, requestId, header, numChunks, chunkIndex, propData)
            }

            CIFactory.SUB_ID_2_PROPERTY_SUBSCRIBE -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val destinationMUID = CIRetrieval.midiCIGetDestinationMUID(data)
                val requestId = data[13]
                val headerSize = data[14] + (data[15].toInt() shl 7)
                val header = data.drop(16).take(headerSize)
                processSubscribe(sourceMUID, destinationMUID, requestId, header)
            }

            else -> {
                processUnknownCIMessage(data)
            }
        }
    }

    init {
        if (muid != muid and 0x7F7F7F7F)
            throw IllegalArgumentException("muid must consist of 7-bit byte values i.e. each 8-bit number must not have the topmost bit as 0. (`muid` must be equivalent to `muid and 0x7F7F7F7F`")
    }
}
