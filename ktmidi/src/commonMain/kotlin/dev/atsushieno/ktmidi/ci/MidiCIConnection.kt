package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.MidiCIProtocolType
import dev.atsushieno.ktmidi.MidiCIProtocolValue
import dev.atsushieno.ktmidi.MidiInput
import dev.atsushieno.ktmidi.MidiOutput
import dev.atsushieno.ktmidi.OnMidiReceivedEventListener
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.time.compareTo

data class DeviceDetails(val manufacturer: Int = 0, val family: Short = 0, val familyModelNumber: Short = 0, val softwareRevisionLevel: Int = 0) {
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

enum class MidiCIDiscoveryResponseCode {
    Reply,
    InvalidateMUID,
    NAK
}

object MidiCIDiscoveryCategoryFlags {
    const val None: Byte = 0
    const val ProtocolNegotiation: Byte = 1
    const val ProfileConfiguration: Byte = 2
    const val PropertyExchange: Byte = 4
    const val ThreePs: Byte = 7 // I'd inclined to say "All", but that may change in the future...
}

// It is used to determine default authority level
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
    const val CI_VERSION_AND_FORMAT: Byte = 0x1

    const val RECEIVABLE_MAX_SYSEX_SIZE = 4096

    const val RESPONDER_CI_CATEGORY_SUPPORTED: Byte = 0x7F

    const val DEVICE_ID_MIDI_PORT: Byte = 0x7F

    val Midi1ProtocolTypeInfo = MidiCIProtocolTypeInfo(MidiCIProtocolType
        .MIDI1.toByte(), MidiCIProtocolValue.MIDI1.toByte(), 0, 0, 0)
    val Midi2ProtocolTypeInfo = MidiCIProtocolTypeInfo(MidiCIProtocolType
        .MIDI2.toByte(), MidiCIProtocolValue.MIDI2_V1.toByte(), 0, 0, 0)
    // The list is ordered from most preferred protocol type info, as per MIDI-CI spec. section 6.5 describes.
    val Midi2ThenMidi1Protocols = listOf(Midi2ProtocolTypeInfo, Midi1ProtocolTypeInfo)
    val Midi1ThenMidi2Protocols = listOf(Midi1ProtocolTypeInfo, Midi2ProtocolTypeInfo)
}

/*
    Typical MIDI-CI processing flow

    - MidiCIInitiator.sendDiscovery()
    - MidiCIResponder.processDiscovery()
    - MidiCIInitiator.initiateProtocolNegotiation()
    - MidiCIResponder.processNegotiationInquiry()
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
                      val authorityLevel: Byte = MidiCIAuthorityLevelBasis.NodeServer,
                      val muid: Int = Random.nextInt()) {

    var device: DeviceDetails = DeviceDetails.empty
    var midiCIBufferSize = 1024

    var state: MidiCIInitiatorState = MidiCIInitiatorState.Initial

    var currentMidiProtocol: MidiCIProtocolTypeInfo = MidiCIConstants.Midi1ProtocolTypeInfo
    var protocolTested: Boolean = false

    private var latestDiscoveryResponseCode: MidiCIDiscoveryResponseCode? = null
    private var discoveredDevice: DeviceDetails? = null
    private var protocolTestData: List<Byte>? = null

    var preferredProtocols = MidiCIConstants.Midi2ThenMidi1Protocols

    // FIXME: make them public once we start supporting Prpoerty Exchange.
    //var establishedMaxSimulutaneousPropertyRequests: Byte? = null

    // Input handlers

    private val defaultProcessDiscoveryResponse = { responseCode: MidiCIDiscoveryResponseCode, device: DeviceDetails, sourceMUID: Int, destinationMUID: Int ->
        if (destinationMUID == muid) {
            latestDiscoveryResponseCode = responseCode
            state =
                if (responseCode == MidiCIDiscoveryResponseCode.Reply) MidiCIInitiatorState.DISCOVERED else MidiCIInitiatorState.Initial

            // If successfully discovered, continue to protocol promotion to MIDI 2.0
            if (responseCode == MidiCIDiscoveryResponseCode.Reply) {
                discoveredDevice = device
                initiateProtocolNegotiation(sourceMUID, authorityLevel, preferredProtocols)
            }
        }
    }
    var processDiscoveryResponse = defaultProcessDiscoveryResponse

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

    var processProfileInquiryResponse: (destinationChannel: Byte, sourceMUID: Int, profileSet: List<Pair<MidiCIProfileId, Boolean>>) -> Unit = { _, _, _ -> }

    // FIXME: make them public once we start supporting Prpoerty Exchange.
    /*
    private val defaultProcessGetMaxSimultaneousPropertyReply: (destinationChannel: Byte, sourceMUID: Int, destinationMUID: Int, max: Byte) -> Unit = { _, _, _, max ->
        establishedMaxSimulutaneousPropertyRequests = max
    }
    var processGetMaxSimultaneousPropertyReply = defaultProcessGetMaxSimultaneousPropertyReply
    */

    // Discovery

    fun sendDiscovery(ciCategorySupported: Byte = MidiCIDiscoveryCategoryFlags.ThreePs) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIDiscovery(buf, MidiCIConstants.CI_VERSION_AND_FORMAT, muid, device.manufacturer, device.family, device.familyModelNumber,
            device.softwareRevisionLevel, ciCategorySupported, MidiCIConstants.RECEIVABLE_MAX_SYSEX_SIZE)
        // we set state before sending the MIDI data as it may process the rest of the events synchronously through the end...
        state = MidiCIInitiatorState.DISCOVERY_SENT
        sendOutput(buf)
    }

    // Protocol Negotiation

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

    // Profile Configuration

    fun requestProfiles(destinationChannelOr7F: Byte, destinationMUID: Int) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIProfileInquiry(buf, destinationChannelOr7F, muid, destinationMUID)
        sendOutput(buf)
    }

    // Property Exchange ... TODO: implement
    //  (it's going to take long time, read the entire Common Rules for PE, support split chunks in reader and writer, and JSON serializers)

    /*
    fun requestPropertyExchangeCapabilities(destinationChannelOr7F: Byte, destinationMUID: Int, maxSimulutaneousPropertyRequests: Byte) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIPropertyGetCapabilities(buf, destinationChannelOr7F, false, muid, destinationMUID, maxSimulutaneousPropertyRequests)
        sendOutput(buf)
    }

    fun requestHasPropertyData(destinationChannelOr7F: Byte, destinationMUID: Int, header: List<Byte>, body: List<Byte>) {

    }
    */

    // Reply handler

    fun processInput(data: List<Byte>) {
        if (data[0] != 0x7E.toByte() || data[2] != 0xD.toByte())
            return // not MIDI-CI sysex
        when (data[3]) {
            // Protocol Negotiation
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
            // Discovery
            CIFactory.SUB_ID_2_DISCOVERY_REPLY -> {
                // Reply to Discovery
                processDiscoveryResponse(MidiCIDiscoveryResponseCode.Reply,
                    CIRetrieval.midiCIGetDeviceDetails(data),
                    CIRetrieval.midiCIGetSourceMUID(data),
                    CIRetrieval.midiCIGetDestinationMUID(data))
            }
            CIFactory.SUB_ID_2_INVALIDATE_MUID -> {
                // Invalid MUID
                processDiscoveryResponse(MidiCIDiscoveryResponseCode.InvalidateMUID,
                    CIRetrieval.midiCIGetDeviceDetails(data),
                    CIRetrieval.midiCIGetSourceMUID(data),
                    0x7F7F7F7F)
            }
            CIFactory.SUB_ID_2_NAK -> {
                // NAK MIDI-CI
                processDiscoveryResponse(MidiCIDiscoveryResponseCode.NAK,
                    CIRetrieval.midiCIGetDeviceDetails(data),
                    CIRetrieval.midiCIGetSourceMUID(data),
                    CIRetrieval.midiCIGetDestinationMUID(data))
            }
            // Profile Configuration
            CIFactory.SUB_ID_2_PROFILE_INQUIRY_REPLY -> {
                processProfileInquiryResponse(CIRetrieval.midiCIGetDestination(data),
                    CIRetrieval.midiCIGetSourceMUID(data),
                    CIRetrieval.midiCIGetProfileSet(data))
            }
            // Property Exchange
            // FIXME: make them public once we start supporting Prpoerty Exchange.
            /*
            CIFactory.SUB_ID_2_PROPERTY_CAPABILITIES_REPLY -> {
                processGetMaxSimultaneousPropertyReply(
                    CIRetrieval.midiCIGetDestination(data),
                    CIRetrieval.midiCIGetSourceMUID(data),
                    CIRetrieval.midiCIGetSourceMUID(data),
                    CIRetrieval.midiCIGetMaxPropertyRequests(data))
            }
            CIFactory.SUB_ID_2_PROPERTY_HAS_DATA_REPLY -> {
                // Reply to Property Exchange Capabilities
            }
            CIFactory.SUB_ID_2_PROPERTY_GET_DATA_REPLY -> {
                // Reply to Property Exchange Capabilities
            }
            CIFactory.SUB_ID_2_PROPERTY_SET_DATA_REPLY -> {
                // Reply to Property Exchange Capabilities
            }
            CIFactory.SUB_ID_2_PROPERTY_SUBSCRIBE_REPLY -> {
                // Reply to Property Exchange Capabilities
            }
            */
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
class MidiCIResponder(private val sendOutput: (data: List<Byte>) -> Unit,
                      private val authorityLevel: Byte = MidiCIAuthorityLevelBasis.NodeServer,
                      private val muid: Int = Random.nextInt()) {

    var device: DeviceDetails = DeviceDetails.empty
    var supportedProtocols: List<MidiCIProtocolTypeInfo> = MidiCIConstants.Midi2ThenMidi1Protocols
    var profileSet: MutableList<Pair<MidiCIProfileId,Boolean>> = mutableListOf()

    var midiCIBufferSize = 128

    var initiatorDevice: DeviceDetails? = null
    var currentMidiProtocol: MidiCIProtocolTypeInfo = MidiCIConstants.Midi1ProtocolTypeInfo

    // smaller value of initiator's maxSimulutaneousPropertyRequests vs. this.maxSimulutaneousPropertyRequests upon PEx inquiry request
    // FIXME: enable this when we start supporting Property Exchange.
    //var establishedMaxSimulutaneousPropertyRequests: Byte? = null

    @OptIn(ExperimentalTime::class)
    private var protocolTestTimeout: Duration? = null

    private val defaultProcessDiscovery: (deviceDetails: DeviceDetails, initiatorMUID: Int) -> MidiCIDiscoveryResponseCode =
        { _, _ -> MidiCIDiscoveryResponseCode.Reply }
    var processDiscovery = defaultProcessDiscovery

    private val defaultProcessNegotiationInquiry: (supportedProtocols: List<MidiCIProtocolTypeInfo>, initiatorMUID: Int) -> List<MidiCIProtocolTypeInfo> =
        // we don't listen to initiator :p
        { _, _ -> supportedProtocols }
    var processNegotiationInquiry = defaultProcessNegotiationInquiry

    private var defaultProcessSetNewProtocol: (newProtocol: MidiCIProtocolTypeInfo, initiatorMUID: Int) -> Boolean = { newProtocol, initiateMUID ->
        supportedProtocols.any { newProtocol.type == it.type && newProtocol.version == it.version} }
    var processSetNewProtocol = defaultProcessSetNewProtocol

    @OptIn(ExperimentalTime::class)
    private val defaultProcessTestNewProtocol: (testData: List<Byte>, initiatorMUID: Int) -> Boolean = { _, _ ->
        val now = MidiCISystem.timeSource.markNow().elapsedNow()
        protocolTestTimeout != null && now.absoluteValue < protocolTestTimeout!!
    }
    var processTestNewProtocol = defaultProcessTestNewProtocol

    var processConfirmNewProtocol: (initiatorMUID: Int) -> Unit = { _ -> }

    private val defaultProcessProfileInquiry: (initiatorMUID: Int, destinationMUID: Int) -> List<Pair<MidiCIProfileId,Boolean>> = { _, _ -> profileSet }
    var processProfileInquiry = defaultProcessProfileInquiry

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

    // FIXME: make them public once we start supporting Prpoerty Exchange.
    // private val defaultProcessGetMaxSimultaneousPropertyRequests: (destinationChannelOr7F: Byte, sourceMUID: Int, destinationMUID: Int, max: Byte) -> Byte = { _, _, _, max -> max }
    //var processGetMaxSimultaneousPropertyRequests = defaultProcessGetMaxSimultaneousPropertyRequests

    @OptIn(ExperimentalTime::class)
    fun processInput(data: List<Byte>) {
        if (data[0] != 0x7E.toByte() || data[2] != 0xD.toByte())
            return // not MIDI-CI sysex
        when (data[3]) {
            // Discovery
            CIFactory.SUB_ID_2_DISCOVERY_INQUIRY -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                initiatorDevice = CIRetrieval.midiCIGetDeviceDetails(data)
                val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
                when (processDiscovery(device, sourceMUID)) {
                    MidiCIDiscoveryResponseCode.Reply -> CIFactory.midiCIDiscoveryReply(
                        dst, MidiCIConstants.CI_VERSION_AND_FORMAT, muid, sourceMUID,
                        device.manufacturer, device.family, device.familyModelNumber, device.softwareRevisionLevel,
                        MidiCIConstants.RESPONDER_CI_CATEGORY_SUPPORTED, MidiCIConstants.RECEIVABLE_MAX_SYSEX_SIZE
                    )

                    MidiCIDiscoveryResponseCode.InvalidateMUID -> CIFactory.midiCIDiscoveryInvalidateMuid(
                        dst, MidiCIConstants.CI_VERSION_AND_FORMAT, muid, sourceMUID
                    )

                    MidiCIDiscoveryResponseCode.NAK -> CIFactory.midiCIDiscoveryNak(
                        dst,
                        MidiCIConstants.DEVICE_ID_MIDI_PORT,
                        MidiCIConstants.CI_VERSION_AND_FORMAT,
                        muid,
                        sourceMUID
                    )
                }
                sendOutput(dst)
            }

            // Protocol Negotiation
            CIFactory.SUB_ID_2_PROTOCOL_NEGOTIATION_INQUIRY -> {
                val requestedProtocols = CIRetrieval.midiCIGetSupportedProtocols(data)
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
                CIFactory.midiCIProtocolNegotiation(
                    dst, true, muid, sourceMUID, authorityLevel,
                    processNegotiationInquiry(requestedProtocols, sourceMUID)
                )
                sendOutput(dst)
            }

            CIFactory.SUB_ID_2_SET_NEW_PROTOCOL -> {
                val requestedProtocol = CIRetrieval.midiCIGetNewProtocol(data)
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)

                if (processSetNewProtocol(requestedProtocol, sourceMUID)) {
                    currentMidiProtocol = requestedProtocol
                    protocolTestTimeout =
                        MidiCISystem.timeSource.markNow().elapsedNow().plus(300.milliseconds.absoluteValue)
                }
            }

            CIFactory.SUB_ID_2_TEST_NEW_PROTOCOL_I2R -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val testData = CIRetrieval.midiCIGetTestData(data)
                if (processTestNewProtocol(testData, sourceMUID)) {
                    val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
                    CIFactory.midiCIProtocolTest(dst, false, muid, sourceMUID, authorityLevel, testData)
                    sendOutput(dst)
                }
                protocolTestTimeout = null
            }

            CIFactory.SUB_ID_2_CONFIRM_NEW_PROTOCOL_ESTABLISHED -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                processConfirmNewProtocol(sourceMUID)
            }

            // Profile Configuration
            CIFactory.SUB_ID_2_PROFILE_INQUIRY -> {
                // Reply to Property Exchange Capabilities
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val destinationMUID = CIRetrieval.midiCIGetDestinationMUID(data)
                val profileSet = processProfileInquiry(sourceMUID, destinationMUID)

                val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
                CIFactory.midiCIProfileInquiryReply(dst, CIRetrieval.midiCIGetDestination(data), muid, sourceMUID,
                    profileSet.filter { it.second }.map { it.first },
                    profileSet.filter { !it.second }.map { it.first })
                sendOutput(dst)
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

            // Property Exchange - TODO: implement (future; see initiator comment)
            /*
            CIFactory.SUB_ID_2_PROPERTY_CAPABILITIES_INQUIRY -> {
                val destination = CIRetrieval.midiCIGetDestination(data)
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val destinationMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val max = CIRetrieval.midiCIGetMaxPropertyRequests(data)

                establishedMaxSimulutaneousPropertyRequests = processGetMaxSimultaneousPropertyRequests(destination, sourceMUID, destinationMUID, max)

                val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
                CIFactory.midiCIPropertyGetCapabilities(dst, destination, true, muid, sourceMUID, establishedMaxSimulutaneousPropertyRequests!!)
                sendOutput(dst)
            }*/
        }
    }
}
