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
        sendOutput(
            CIFactory.midiCIDiscoveryReply(
                dst, MidiCIConstants.CI_VERSION_AND_FORMAT, muid, initiatorMUID,
                device.manufacturerId, device.familyId, device.modelId, device.versionId,
                capabilityInquirySupported, receivableMaxSysExSize,
                initiatorOutputPath, functionBlock
            )
        )
    }
    var processDiscovery = sendDiscoveryReplyForInquiry

    val sendEndpointReplyForInquiry: (initiatorMUID: Int, destinationMUID: Int, status: Byte) -> Unit = { initiatorMUID, destinationMUID, status ->
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        val prodId = productInstanceId
        sendOutput(
            CIFactory.midiCIEndpointMessageReply(
                dst, MidiCIConstants.CI_VERSION_AND_FORMAT, muid, initiatorMUID, status,
                if (status == MidiCIConstants.ENDPOINT_STATUS_PRODUCT_INSTANCE_ID && prodId != null) prodId.toByteArray(
                    Charsets.ISO_8859_1
                ).toList() else listOf() // FIXME: verify that it is only ASCII chars?
            )
        )
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
            profileSet.filter { !it.second }.map { it.first })
        )
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

    val propertyService = CommonPropertyService(device)

    // Should this also delegate to property service...?
    fun sendPropertyCapabilitiesReply(msg: Message.PropertyGetCapabilitiesReply) {
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIPropertyGetCapabilities(dst, msg.destination, true,
            muid, msg.sourceMUID, msg.max))
    }
    val getPropertyCapabilitiesReplyFor: (msg: Message.PropertyGetCapabilities) -> Message.PropertyGetCapabilitiesReply = { msg ->
        val establishedMaxSimultaneousPropertyRequests =
            if (msg.max > maxSimultaneousPropertyRequests) maxSimultaneousPropertyRequests
            else msg.max
        Message.PropertyGetCapabilitiesReply(msg.destination, muid, msg.sourceMUID, establishedMaxSimultaneousPropertyRequests)
    }
    var processPropertyCapabilitiesInquiry: (msg: Message.PropertyGetCapabilities) -> Unit = { msg ->
        sendPropertyCapabilitiesReply(getPropertyCapabilitiesReplyFor(msg))
    }

    fun sendPropertyGetDataReply(inquiry: Message.GetPropertyData, reply: Message.GetPropertyDataReply) {
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIPropertyChunks(
            dst, CIFactory.SUB_ID_2_PROPERTY_GET_DATA_REPLY,
            muid, inquiry.sourceMUID, inquiry.requestId, reply.header, reply.body
        ).forEach {
            sendOutput(it)
        }
    }
    var processGetPropertyData: (msg: Message.GetPropertyData) -> Unit = { msg ->
        sendPropertyGetDataReply(msg, propertyService.getPropertyData(msg))
    }

    fun sendPropertySetDataReply(inquiry: Message.SetPropertyData, reply: Message.SetPropertyDataReply) {
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIPropertyChunks(
            dst, CIFactory.SUB_ID_2_PROPERTY_SET_DATA_REPLY,
            muid, inquiry.sourceMUID, inquiry.requestId, reply.header, listOf()
        ).forEach {
            sendOutput(it)
        }
    }
    var processSetPropertyData: (msg: Message.SetPropertyData) -> Unit = { msg ->
        sendPropertySetDataReply(msg, propertyService.setPropertyData(msg))
    }

    fun sendPropertySubscribeReply(inquiry: Message.SubscribeProperty, reply: Message.SubscribePropertyReply) {
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIPropertyChunks(
            dst, CIFactory.SUB_ID_2_PROPERTY_SUBSCRIBE_REPLY,
            muid, inquiry.sourceMUID, inquiry.requestId, reply.header, reply.body
        ).forEach {
            sendOutput(it)
        }
    }
    var processSubscribeProperty: (msg: Message.SubscribeProperty) -> Unit = { msg ->
        sendPropertySubscribeReply(msg, propertyService.subscribeProperty(msg))
    }

    fun processInput(data: List<Byte>) {
        if (data[0] != 0x7E.toByte() || data[2] != 0xD.toByte())
            return // not MIDI-CI sysex

        val destinationMUID = CIRetrieval.midiCIGetDestinationMUID(data)
        if (destinationMUID != muid && destinationMUID != MidiCIConstants.BROADCAST_MUID_32)
            return // we are not the target

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
                processProfileInquiry(source, sourceMUID, destinationMUID)
            }

            CIFactory.SUB_ID_2_SET_PROFILE_ON, CIFactory.SUB_ID_2_SET_PROFILE_OFF -> {
                var enabled = data[3] == CIFactory.SUB_ID_2_SET_PROFILE_ON
                val destination = CIRetrieval.midiCIGetDestination(data)
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val profileId = CIRetrieval.midiCIGetProfileId(data)
                enabled = processSetProfile(destination, sourceMUID, destinationMUID, profileId, enabled)

                val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
                CIFactory.midiCIProfileReport(dst, destination, enabled, muid, profileId)
                sendOutput(dst)
            }

            CIFactory.SUB_ID_2_PROFILE_SPECIFIC_DATA -> {
                val sourceChannel = CIRetrieval.midiCIGetDestination(data)
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val profileId = CIRetrieval.midiCIGetProfileId(data)
                val dataLength = CIRetrieval.midiCIGetProfileSpecificDataSize(data)
                processProfileSpecificData(sourceChannel, sourceMUID, destinationMUID, profileId, data.drop(21).take(dataLength))
            }

            // Property Exchange

            CIFactory.SUB_ID_2_PROPERTY_CAPABILITIES_INQUIRY -> {
                val destination = CIRetrieval.midiCIGetDestination(data)
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val max = CIRetrieval.midiCIGetMaxPropertyRequests(data)

                processPropertyCapabilitiesInquiry(
                    Message.PropertyGetCapabilities(destination, sourceMUID, destinationMUID, max))
            }

            CIFactory.SUB_ID_2_PROPERTY_GET_DATA_INQUIRY -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val requestId = data[13]
                val headerSize = data[14] + (data[15].toInt() shl 7)
                val header = data.drop(16).take(headerSize)
                val pos = 16 + headerSize
                val numChunks = data[pos] + (data[pos + 1].toInt() shl 7)
                val chunkIndex = data[pos + 2] + (data[pos + 3].toInt() shl 7)
                val dataSize = data[pos + 4] + (data[pos + 5].toInt() shl 7)
                if (numChunks == 1 && chunkIndex == 1 && dataSize == 0) {
                    processGetPropertyData(Message.GetPropertyData(sourceMUID, destinationMUID, requestId, header))
                } // FIXME: else -> error handling (data chunk must be always empty)
            }

            CIFactory.SUB_ID_2_PROPERTY_SET_DATA_INQUIRY -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val requestId = data[13]
                val headerSize = data[14] + (data[15].toInt() shl 7)
                val header = data.drop(16).take(headerSize)
                val pos = 16 + headerSize
                val numChunks = data[pos] + (data[pos + 1].toInt() shl 7)
                val chunkIndex = data[pos + 2] + (data[pos + 3].toInt() shl 7)
                val dataSize = data[pos + 4] + (data[pos + 5].toInt() shl 7)
                val propData = data.drop (pos + 6).take(dataSize)
                // FIXME: implement chunk manager
                if (numChunks == 1 && chunkIndex == 1 && dataSize == 0)
                    processSetPropertyData(Message.SetPropertyData(sourceMUID, destinationMUID, requestId, header, propData))
            }

            CIFactory.SUB_ID_2_PROPERTY_SUBSCRIBE -> {
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