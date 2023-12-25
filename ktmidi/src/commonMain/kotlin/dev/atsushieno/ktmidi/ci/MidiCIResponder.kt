package dev.atsushieno.ktmidi.ci

import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlin.random.Random

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
                      val muid: Int = Random.nextInt() and 0x7F7F7F7F) {

    var capabilityInquirySupported = MidiCISupportedCategories.THREE_P
    var receivableMaxSysExSize = MidiCIConstants.DEFAULT_RECEIVABLE_MAX_SYSEX_SIZE
    val profileSet: MutableList<MidiCIProfile> = mutableListOf()
    var functionBlock: Byte = MidiCIConstants.NO_FUNCTION_BLOCK
    var productInstanceId: String = ""
    var maxSimultaneousPropertyRequests: Byte = 1

    var midiCIBufferSize = 4096

    val propertyService = CommonRulesPropertyService(muid, device)

    // smaller value of initiator's maxSimulutaneousPropertyRequests vs. this.maxSimulutaneousPropertyRequests upon PEx inquiry request
    // FIXME: enable this when we start supporting Property Exchange.
    //var establishedMaxSimulutaneousPropertyRequests: Byte? = null

    val sendDiscoveryReply: (msg: Message.DiscoveryReply) -> Unit = { msg ->
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(
            CIFactory.midiCIDiscoveryReply(
                dst, MidiCIConstants.CI_VERSION_AND_FORMAT, msg.sourceMUID, msg.destinationMUID,
                msg.device.manufacturer, msg.device.family, msg.device.modelNumber, msg.device.softwareRevisionLevel,
                capabilityInquirySupported, receivableMaxSysExSize,
                msg.outputPathId, msg.functionBlock
            )
        )
    }
    val getDiscoveryReplyForInquiry: (msg: Message.DiscoveryInquiry) -> Message.DiscoveryReply = { msg ->
        val deviceDetails = DeviceDetails(device.manufacturerId, device.familyId, device.modelId, device.versionId)
        Message.DiscoveryReply(muid, msg.muid, deviceDetails, capabilityInquirySupported, receivableMaxSysExSize, msg.outputPathId, functionBlock)
    }
    var processDiscovery: (msg: Message.DiscoveryInquiry) -> Unit = { msg ->
        sendDiscoveryReply(getDiscoveryReplyForInquiry(msg))
    }

    val sendEndpointReply: (msg: Message.EndpointReply) -> Unit = { msg ->
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(
            CIFactory.midiCIEndpointMessageReply(
                dst, MidiCIConstants.CI_VERSION_AND_FORMAT, msg.sourceMUID, msg.destinationMUID, msg.status, msg.data)
        )
    }
    val getEndpointReplyForInquiry: (msg: Message.EndpointInquiry) -> Message.EndpointReply = { msg ->
        val prodId = productInstanceId
        if (prodId.length > 16)
            throw IllegalStateException("productInstanceId shall not be any longer than 16 bytes in size")
        Message.EndpointReply(muid, msg.sourceMUID, msg.status,
            if (msg.status == MidiCIConstants.ENDPOINT_STATUS_PRODUCT_INSTANCE_ID && prodId != null) prodId.toByteArray(
                Charsets.ISO_8859_1
            ).toList() else listOf() // FIXME: verify that it is only ASCII chars?
        )
    }
    var processEndpointMessage: (msg: Message.EndpointInquiry) -> Unit = { msg ->
        sendEndpointReply(getEndpointReplyForInquiry(msg))
    }

    val sendAck: (sourceMUID: Int) -> Unit = { sourceMUID ->
        // FIXME: we need to implement some sort of state management for each initiator
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIAckNak(dst, false, MidiCIConstants.DEVICE_ID_MIDI_PORT,
            MidiCIConstants.CI_VERSION_AND_FORMAT, muid, sourceMUID, CISubId2.INVALIDATE_MUID, 0, 0, listOf(), listOf()))
    }
    var processInvalidateMUID = { sourceMUID: Int, targetMUID: Int -> sendAck(sourceMUID) }

    // Profile Configuration
    private fun MutableList<MidiCIProfile>.getMatchingProfiles(address: Byte, enabled: Boolean) =
        when (address) {
            0x7E.toByte() -> this.filter { it.enabled == enabled }.map { it.profile }
            else -> this.filter { it.address == address && it.enabled == enabled }.map { it.profile }
        }

    val sendProfileReply: (msg: Message.ProfileReply) -> Unit = { msg ->
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIProfileInquiryReply(dst, msg.address, msg.sourceMUID, msg.destinationMUID,
            msg.enabledProfiles,
            msg.disabledProfiles)
        )
    }
    val getProfileRepliesForInquiry: (msg: Message.ProfileInquiry) -> Sequence<Message.ProfileReply> = { msg ->
        sequence {
            yield(Message.ProfileReply(msg.address, muid, msg.sourceMUID,
                profileSet.getMatchingProfiles(msg.address, true),
                profileSet.getMatchingProfiles(msg.address, false)))
        }
    }
    var processProfileInquiry: (msg: Message.ProfileInquiry) -> Unit = { msg ->
        getProfileRepliesForInquiry(msg).forEach {
            sendProfileReply(it)
        }
    }

    var onProfileSet: (profile: MidiCIProfile) -> Unit = {}

    private val defaultProcessSetProfile = { address: Byte, initiatorMUID: Int, destinationMUID: Int, profile: MidiCIProfileId, enabled: Boolean ->
        val newEntry = MidiCIProfile(profile, address, enabled)
        val existing = profileSet.firstOrNull { it.toString() == profile.toString() }
        if (existing != null)
            profileSet.remove(existing)
        profileSet.add(newEntry)
        onProfileSet(newEntry)
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

    var processProfileSpecificData: (address: Byte, sourceMUID: Int, destinationMUID: Int, profileId: MidiCIProfileId, data: List<Byte>) -> Unit = { _, _, _, _, _ -> }

    // Handling invalid messages

    fun sendNakForUnknownCIMessage(data: List<Byte>) {
        val source = data[1]
        val originalSubId = data[3]
        val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
        val destinationMUID = CIRetrieval.midiCIGetDestinationMUID(data)
        val nak = MidiCIAckNakData(source, sourceMUID, destinationMUID, originalSubId,
            CINakStatus.MessageNotSupported, 0, listOf(), listOf())
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIAckNak(dst, true, nak))
    }
    var processUnknownCIMessage: (data: List<Byte>) -> Unit = { data ->
        sendNakForUnknownCIMessage(data)
    }

    // Property Exchange

    // Should this also delegate to property service...?
    fun sendPropertyCapabilitiesReply(msg: Message.PropertyGetCapabilitiesReply) {
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIPropertyGetCapabilities(dst, msg.destination, true,
            muid, msg.sourceMUID, msg.maxSimultaneousRequests))
    }
    val getPropertyCapabilitiesReplyFor: (msg: Message.PropertyGetCapabilities) -> Message.PropertyGetCapabilitiesReply = { msg ->
        val establishedMaxSimultaneousPropertyRequests =
            if (msg.maxSimultaneousRequests > maxSimultaneousPropertyRequests) maxSimultaneousPropertyRequests
            else msg.maxSimultaneousRequests
        Message.PropertyGetCapabilitiesReply(msg.destination, muid, msg.sourceMUID, establishedMaxSimultaneousPropertyRequests)
    }
    var processPropertyCapabilitiesInquiry: (msg: Message.PropertyGetCapabilities) -> Unit = { msg ->
        sendPropertyCapabilitiesReply(getPropertyCapabilitiesReplyFor(msg))
    }

    fun sendPropertyGetDataReply(msg: Message.GetPropertyDataReply) {
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIPropertyChunks(
            dst, CISubId2.PROPERTY_GET_DATA_REPLY,
            msg.sourceMUID, msg.destinationMUID, msg.requestId, msg.header, msg.body
        ).forEach {
            sendOutput(it)
        }
    }
    var processGetPropertyData: (msg: Message.GetPropertyData) -> Unit = { msg ->
        sendPropertyGetDataReply(propertyService.getPropertyData(msg))
    }

    fun sendPropertySetDataReply(msg: Message.SetPropertyDataReply) {
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIPropertyChunks(
            dst, CISubId2.PROPERTY_SET_DATA_REPLY,
            msg.sourceMUID, msg.destinationMUID, msg.requestId, msg.header, listOf()
        ).forEach {
            sendOutput(it)
        }
    }
    var processSetPropertyData: (msg: Message.SetPropertyData) -> Unit = { msg ->
        sendPropertySetDataReply(propertyService.setPropertyData(msg))
    }

    fun sendPropertySubscribeReply(msg: Message.SubscribePropertyReply) {
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIPropertyChunks(
            dst, CISubId2.PROPERTY_SUBSCRIBE_REPLY,
            msg.sourceMUID, msg.destinationMUID, msg.requestId, msg.header, msg.body
        ).forEach {
            sendOutput(it)
        }
    }
    var processSubscribeProperty: (msg: Message.SubscribeProperty) -> Unit = { msg ->
        sendPropertySubscribeReply(propertyService.subscribeProperty(msg))
    }

    fun processInput(data: List<Byte>) {
        if (data[0] != 0x7E.toByte() || data[2] != 0xD.toByte())
            return // not MIDI-CI sysex

        val destinationMUID = CIRetrieval.midiCIGetDestinationMUID(data)
        if (destinationMUID != muid && destinationMUID != MidiCIConstants.BROADCAST_MUID_32)
            return // we are not the target

        when (data[3]) {
            // Discovery
            CISubId2.DISCOVERY_INQUIRY -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val device = CIRetrieval.midiCIGetDeviceDetails(data)
                val ciSupported = data[24]
                val max = CIRetrieval.midiCIMaxSysExSize(data)
                // only available in MIDI-CI 1.2 or later.
                val initiatorOutputPath = if (data.size > 29) data[29] else 0
                processDiscovery(Message.DiscoveryInquiry(sourceMUID, device, ciSupported, max, initiatorOutputPath))
            }

            CISubId2.ENDPOINT_MESSAGE_INQUIRY -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                // only available in MIDI-CI 1.2 or later.
                val status = data[13]
                processEndpointMessage(Message.EndpointInquiry(sourceMUID, destinationMUID, status))
            }

            CISubId2.INVALIDATE_MUID -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val targetMUID = CIRetrieval.midiCIGetMUIDToInvalidate(data)
                processInvalidateMUID(sourceMUID, targetMUID)
            }

            // Profile Configuration

            CISubId2.PROFILE_INQUIRY -> {
                val source = data[1]
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                processProfileInquiry(Message.ProfileInquiry(source, sourceMUID, destinationMUID))
            }

            CISubId2.SET_PROFILE_ON, CISubId2.SET_PROFILE_OFF -> {
                var enabled = data[3] == CISubId2.SET_PROFILE_ON
                val address = CIRetrieval.midiCIGetAddressing(data)
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val profileId = CIRetrieval.midiCIGetProfileId(data)
                enabled = processSetProfile(address, sourceMUID, destinationMUID, profileId, enabled)

                val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
                CIFactory.midiCIProfileReport(dst, address, enabled, muid, profileId)
                sendOutput(dst)
            }

            CISubId2.PROFILE_SPECIFIC_DATA -> {
                val address = CIRetrieval.midiCIGetAddressing(data)
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val profileId = CIRetrieval.midiCIGetProfileId(data)
                val dataLength = CIRetrieval.midiCIGetProfileSpecificDataSize(data)
                processProfileSpecificData(address, sourceMUID, destinationMUID, profileId, data.drop(21).take(dataLength))
            }

            // Property Exchange

            CISubId2.PROPERTY_CAPABILITIES_INQUIRY -> {
                val destination = CIRetrieval.midiCIGetAddressing(data)
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val max = CIRetrieval.midiCIGetMaxPropertyRequests(data)

                processPropertyCapabilitiesInquiry(
                    Message.PropertyGetCapabilities(destination, sourceMUID, destinationMUID, max))
            }

            CISubId2.PROPERTY_GET_DATA_INQUIRY -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val requestId = data[13]
                val headerSize = data[14] + (data[15].toInt() shl 7)
                val header = data.drop(16).take(headerSize)
                val pos = 16 + headerSize
                val numChunks = CIRetrieval.midiCIGetPropertyTotalChunks(data)
                val chunkIndex = CIRetrieval.midiCIGetPropertyChunkIndex(data)
                val dataSize = data[pos + 4] + (data[pos + 5].toInt() shl 7)
                processGetPropertyData(Message.GetPropertyData(sourceMUID, destinationMUID, requestId, header))
            }

            CISubId2.PROPERTY_SET_DATA_INQUIRY -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val requestId = data[13]
                val headerSize = data[14] + (data[15].toInt() shl 7)
                val header = data.drop(16).take(headerSize)
                val pos = 16 + headerSize
                val numChunks = CIRetrieval.midiCIGetPropertyTotalChunks(data)
                val chunkIndex = CIRetrieval.midiCIGetPropertyChunkIndex(data)
                val dataSize = data[pos + 4] + (data[pos + 5].toInt() shl 7)
                val propData = data.drop (pos + 6).take(dataSize)
                processSetPropertyData(Message.SetPropertyData(sourceMUID, destinationMUID, requestId, header, numChunks, chunkIndex, propData))
            }

            CISubId2.PROPERTY_SUBSCRIBE -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val requestId = data[13]
                val headerSize = data[14] + (data[15].toInt() shl 7)
                val header = data.drop(16).take(headerSize)
                processSubscribeProperty(Message.SubscribeProperty(sourceMUID, destinationMUID, requestId, header))
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