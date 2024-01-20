package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.Message.Companion.muidString
import dev.atsushieno.ktmidi.ci.profilecommonrules.CommonRulesProfileService
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyCommonHeaderKeys
import kotlinx.datetime.Clock

enum class ConnectionChange {
    Added,
    Removed
}

/**
 * Represents A MIDI-CI Device entity, or a Function Block[*1].
 *
 * [*1] M2-101-UM_v1_2: MIDI-CI specification section 3.1 states:
 * "Each Function Block that supports MIDI-CI shall have a different MUID and act as a unique MIDI-CI Device"
 *
 */
class MidiCIDevice(val muid: Int, val config: MidiCIDeviceConfiguration,
                   private val sendCIOutput: (group: Byte, data: List<Byte>) -> Unit,
                   private val sendMidiMessageReport: (protocol: MidiMessageReportProtocol, data: List<Byte>) -> Unit
) {
    val initiator by lazy { MidiCIInitiator(this, config.initiator, sendCIOutput) }
    val responder by lazy { MidiCIResponder(this, config.responder, sendCIOutput, sendMidiMessageReport) }

    val device: MidiCIDeviceInfo
        get() = config.device


    class Events {
        val unknownMessageReceived = mutableListOf<(data: List<Byte>) -> Unit>()

        val discoveryReceived = mutableListOf<(msg: Message.DiscoveryInquiry) -> Unit>()
        val discoveryReplyReceived = mutableListOf<(Message.DiscoveryReply) -> Unit>()
        val endpointInquiryReceived = mutableListOf<(msg: Message.EndpointInquiry) -> Unit>()
        val endpointReplyReceived = mutableListOf<(Message.EndpointReply) -> Unit>()
        val invalidateMUIDReceived = mutableListOf<(Message.InvalidateMUID) -> Unit>()

        val profileInquiryReceived = mutableListOf<(msg: Message.ProfileInquiry) -> Unit>()
        val profileInquiryReplyReceived = mutableListOf<(Message.ProfileReply) -> Unit>()
        val setProfileOnReceived = mutableListOf<(profile: Message.SetProfileOn) -> Unit>()
        val setProfileOffReceived = mutableListOf<(profile: Message.SetProfileOff) -> Unit>()
        val profileAddedReceived = mutableListOf<(Message.ProfileAdded) -> Unit>()
        val profileRemovedReceived = mutableListOf<(Message.ProfileRemoved) -> Unit>()
        val profileEnabledReceived = mutableListOf<(Message.ProfileEnabled) -> Unit>()
        val profileDisabledReceived = mutableListOf<(Message.ProfileDisabled) -> Unit>()
        val profileDetailsInquiryReceived = mutableListOf<(Message.ProfileDetailsInquiry) -> Unit>()
        val profileDetailsReplyReceived = mutableListOf<(Message.ProfileDetailsReply) -> Unit>()
        val profileSpecificDataReceived = mutableListOf<(Message.ProfileSpecificData) -> Unit>()

        val propertyCapabilityInquiryReceived = mutableListOf<(Message.PropertyGetCapabilities) -> Unit>()
        val propertyCapabilityReplyReceived = mutableListOf<(Message.PropertyGetCapabilitiesReply) -> Unit>()
        val getPropertyDataReceived = mutableListOf<(msg: Message.GetPropertyData) -> Unit>()
        val getPropertyDataReplyReceived = mutableListOf<(msg: Message.GetPropertyDataReply) -> Unit>()
        val setPropertyDataReceived = mutableListOf<(msg: Message.SetPropertyData) -> Unit>()
        val setPropertyDataReplyReceived = mutableListOf<(msg: Message.SetPropertyDataReply) -> Unit>()
        val subscribePropertyReplyReceived = mutableListOf<(msg: Message.SubscribePropertyReply) -> Unit>()
        val subscribePropertyReceived = mutableListOf<(msg: Message.SubscribeProperty) -> Unit>()
        val propertyNotifyReceived = mutableListOf<(msg: Message.PropertyNotify) -> Unit>()

        val processInquiryReceived = mutableListOf<(msg: Message.ProcessInquiry) -> Unit>()
        val processInquiryReplyReceived = mutableListOf<(msg: Message.ProcessInquiryReply) -> Unit>()
        val midiMessageReportReceived = mutableListOf<(msg: Message.MidiMessageReportInquiry) -> Unit>()
        val midiMessageReportReplyReceived = mutableListOf<(msg: Message.MidiMessageReportReply) -> Unit>()
        val endOfMidiMessageReportReceived = mutableListOf<(msg: Message.MidiMessageReportNotifyEnd) -> Unit>()
    }

    val events = Events()

    val logger = Logger()

    val connections = mutableMapOf<Int, MidiCIInitiator.ClientConnection>()
    val connectionsChanged = mutableListOf<(change: ConnectionChange, connection: MidiCIInitiator.ClientConnection) -> Unit>()

    var profileService: MidiCIProfileService = CommonRulesProfileService()
    val localProfiles = ObservableProfileList(config.localProfiles)

    // Request sender

    fun sendDiscovery(group: Byte, ciCategorySupported: Byte = MidiCISupportedCategories.THREE_P) =
        sendDiscovery(Message.DiscoveryInquiry(
            Message.Common(muid, MidiCIConstants.BROADCAST_MUID_32, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group),
            DeviceDetails(
                device.manufacturerId,
                device.familyId,
                device.modelId,
                device.versionId
            ),
            ciCategorySupported, config.receivableMaxSysExSize, config.outputPathId))

    fun sendDiscovery(msg: Message.DiscoveryInquiry) {
        logger.logMessage(msg, MessageDirection.Out)
        val buf = MutableList<Byte>(config.receivableMaxSysExSize) { 0 }
        sendCIOutput(msg.group, CIFactory.midiCIDiscovery(
            buf, MidiCIConstants.CI_VERSION_AND_FORMAT, msg.sourceMUID, msg.device.manufacturer, msg.device.family, msg.device.modelNumber,
            msg.device.softwareRevisionLevel, msg.ciCategorySupported, msg.receivableMaxSysExSize, msg.outputPathId
        ))
    }

    fun sendNakForUnknownCIMessage(group: Byte, data: List<Byte>) {
        val source = data[1]
        val originalSubId = data[3]
        val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
        val destinationMUID = CIRetrieval.midiCIGetDestinationMUID(data)
        val nak = MidiCIAckNakData(source, sourceMUID, destinationMUID, originalSubId,
            CINakStatus.MessageNotSupported, 0, listOf(), listOf())
        val dst = MutableList<Byte>(config.receivableMaxSysExSize) { 0 }
        sendCIOutput(group, CIFactory.midiCIAckNak(dst, true, nak))
    }

    fun sendNakForUnknownMUID(common: Message.Common, originalSubId2: Byte) {
        sendNakForError(common, originalSubId2, CINakStatus.Nak, 0, message = "CI Device is not connected. (MUID: ${common.destinationMUID.muidString})")
    }

    fun sendNakForError(common: Message.Common, originalSubId2: Byte, statusCode: Byte, statusData: Byte, details: List<Byte> = List(5) {0}, message: String) {
        sendNakForError(Message.Nak(common, originalSubId2, statusCode, statusData, details,
            MidiCIConverter.encodeStringToASCII(message).toASCIIByteArray().toList()))
    }

    fun sendNakForError(msg: Message.Nak) {
        logger.logMessage(msg, MessageDirection.Out)
        val buf = MutableList<Byte>(config.receivableMaxSysExSize) { 0 }
        sendCIOutput(msg.group, CIFactory.midiCIAckNak(buf, true, msg.address, MidiCIConstants.CI_VERSION_AND_FORMAT, msg.sourceMUID, msg.destinationMUID,
            msg.originalSubId, msg.statusCode, msg.statusData, msg.details, msg.message))
    }

    fun invalidateMUID(address: Byte, group: Byte, targetMUID: Int, message: String) {
        logger.logError(message)
        val msg = Message.InvalidateMUID(Message.Common(muid, targetMUID, address, group), targetMUID)
        invalidateMUID(msg)
    }

    fun invalidateMUID(msg: Message.InvalidateMUID) {
        logger.logMessage(msg, MessageDirection.Out)
        val buf = MutableList<Byte>(config.receivableMaxSysExSize) { 0 }
        sendCIOutput(msg.group, CIFactory.midiCIDiscoveryInvalidateMuid(buf, MidiCIConstants.CI_VERSION_AND_FORMAT, msg.sourceMUID, msg.targetMUID))
    }

    // Input handlers

    var processUnknownCIMessage: (group: Byte, data: List<Byte>) -> Unit = { group, data ->
        logger.nak(data, MessageDirection.In)
        events.unknownMessageReceived.forEach { it(data) }
        sendNakForUnknownCIMessage(group, data)
    }

    fun defaultProcessInvalidateMUID(msg: Message.InvalidateMUID) {
        val conn = connections[msg.targetMUID]
        if (conn != null) {
            connections.remove(msg.targetMUID)
            connectionsChanged.forEach { it(ConnectionChange.Removed, conn) }
        }
    }
    var processInvalidateMUID: (msg: Message.InvalidateMUID) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.invalidateMUIDReceived.forEach { it(msg) }
        defaultProcessInvalidateMUID(msg)
    }

    // to Discovery (responder)
    val sendDiscoveryReply: (msg: Message.DiscoveryReply) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.Out)
        val dst = MutableList<Byte>(config.receivableMaxSysExSize) { 0 }
        sendCIOutput(msg.group,
            CIFactory.midiCIDiscoveryReply(
                dst, MidiCIConstants.CI_VERSION_AND_FORMAT, msg.sourceMUID, msg.destinationMUID,
                msg.device.manufacturer, msg.device.family, msg.device.modelNumber, msg.device.softwareRevisionLevel,
                config.capabilityInquirySupported, config.receivableMaxSysExSize,
                msg.outputPathId, msg.functionBlock
            )
        )
    }
    val getDiscoveryReplyForInquiry: (request: Message.DiscoveryInquiry) -> Message.DiscoveryReply = { request ->
        val deviceDetails = DeviceDetails(device.manufacturerId, device.familyId, device.modelId, device.versionId)
        Message.DiscoveryReply(Message.Common(muid, request.sourceMUID, request.address, request.group),
            deviceDetails, config.capabilityInquirySupported,
            config.receivableMaxSysExSize, request.outputPathId, config.functionBlock)
    }
    var processDiscovery: (msg: Message.DiscoveryInquiry) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.discoveryReceived.forEach { it(msg) }
        val reply = getDiscoveryReplyForInquiry(msg)
        sendDiscoveryReply(reply)
    }

    val sendEndpointReply: (msg: Message.EndpointReply) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.Out)
        val dst = MutableList<Byte>(config.receivableMaxSysExSize) { 0 }
        sendCIOutput(msg.group,
            CIFactory.midiCIEndpointMessageReply(
                dst, MidiCIConstants.CI_VERSION_AND_FORMAT, msg.sourceMUID, msg.destinationMUID, msg.status, msg.data)
        )
    }
    val getEndpointReplyForInquiry: (msg: Message.EndpointInquiry) -> Message.EndpointReply = { msg ->
        val prodId = config.productInstanceId
        if (prodId.length > 16 || prodId.any { it.code < 0x20 || it.code > 0x7E })
            throw IllegalStateException("productInstanceId shall not be any longer than 16 bytes in size and must be all in ASCII code between 32 and 126")
        Message.EndpointReply(Message.Common(muid, msg.sourceMUID, msg.address, msg.group),
            msg.status,
            if (msg.status == MidiCIConstants.ENDPOINT_STATUS_PRODUCT_INSTANCE_ID && prodId.isNotBlank()) prodId.toASCIIByteArray().toList() else listOf()
        )
    }
    var processEndpointMessage: (msg: Message.EndpointInquiry) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.endpointInquiryReceived.forEach { it(msg) }
        val reply = getEndpointReplyForInquiry(msg)
        sendEndpointReply(reply)
    }

    // to Reply to Discovery (initiator)
    val handleNewEndpoint = { msg: Message.DiscoveryReply ->
        // If successfully discovered, continue to endpoint inquiry
        val connection = MidiCIInitiator.ClientConnection(initiator, msg.sourceMUID, msg.device)
        val existing = connections[msg.sourceMUID]
        if (existing != null) {
            connections.remove(msg.sourceMUID)
            connectionsChanged.forEach { it(ConnectionChange.Removed, existing) }
        }
        connections[msg.sourceMUID]= connection
        connectionsChanged.forEach { it(ConnectionChange.Added, connection) }

        if (config.autoSendEndpointInquiry)
            initiator.sendEndpointMessage(msg.group, msg.sourceMUID, MidiCIConstants.ENDPOINT_STATUS_PRODUCT_INSTANCE_ID)

        if (config.autoSendProfileInquiry && (msg.ciCategorySupported.toInt() and MidiCISupportedCategories.PROFILE_CONFIGURATION.toInt()) != 0)
            initiator.requestProfiles(msg.group, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, msg.sourceMUID)
        if (config.autoSendPropertyExchangeCapabilitiesInquiry && (msg.ciCategorySupported.toInt() and MidiCISupportedCategories.PROPERTY_EXCHANGE.toInt()) != 0)
            initiator.requestPropertyExchangeCapabilities(msg.group, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, msg.sourceMUID, config.maxSimultaneousPropertyRequests)
    }
    var processDiscoveryReply: (msg: Message.DiscoveryReply) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.discoveryReplyReceived.forEach { it(msg) }
        handleNewEndpoint(msg)
    }

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

    val defaultProcessEndpointReply = { msg: Message.EndpointReply ->
        val conn = connections[msg.sourceMUID]
        if (conn != null) {
            if (msg.status == MidiCIConstants.ENDPOINT_STATUS_PRODUCT_INSTANCE_ID) {
                val s = msg.data.toByteArray().decodeToString()
                if (s.all { it.code in 32..126 } && s.length <= 16)
                    conn.productInstanceId = s
                else
                    invalidateMUID(msg.address, msg.group, msg.sourceMUID, "Invalid product instance ID")
            }
        }
    }
    var processEndpointReply: (msg: Message.EndpointReply) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.endpointReplyReceived.forEach { it(msg) }
        defaultProcessEndpointReply(msg)
    }

    // Local Profile Configuration

    val sendProfileReply: (msg: Message.ProfileReply) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.Out)
        val dst = MutableList<Byte>(config.receivableMaxSysExSize) { 0 }
        sendCIOutput(msg.group, CIFactory.midiCIProfileInquiryReply(dst, msg.address, msg.sourceMUID, msg.destinationMUID,
            msg.enabledProfiles,
            msg.disabledProfiles)
        )
    }

    private fun getAllAddresses(address: Byte) = sequence {
        if (address == MidiCIConstants.ADDRESS_FUNCTION_BLOCK)
            yieldAll(localProfiles.profiles.map { it.address }.distinct().sorted())
        else
            yield(address)
    }
    val getProfileRepliesForInquiry: (msg: Message.ProfileInquiry) -> Sequence<Message.ProfileReply> = { msg ->
        sequence {
            getAllAddresses(msg.address).forEach { address ->
                yield(Message.ProfileReply(Message.Common(muid, msg.sourceMUID, address, msg.group),
                    localProfiles.getMatchingProfiles(address, true),
                    localProfiles.getMatchingProfiles(address, false)))
            }
        }
    }
    var processProfileInquiry: (msg: Message.ProfileInquiry) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.profileInquiryReceived.forEach { it(msg) }
        getProfileRepliesForInquiry(msg).forEach { reply ->
            sendProfileReply(reply)
        }
    }

    var onProfileSet = mutableListOf<(profile: MidiCIProfile) -> Unit>()

    private val defaultProcessSetProfile = { group: Byte, address: Byte, initiatorMUID: Int, destinationMUID: Int, profile: MidiCIProfileId, numChannelsRequested: Short, enabled: Boolean ->
        val newEntry = MidiCIProfile(profile, group, address, enabled, numChannelsRequested)
        val existing = localProfiles.profiles.firstOrNull { it.profile == profile }
        if (existing != null)
            localProfiles.remove(existing)
        localProfiles.add(newEntry)
        onProfileSet.forEach { it(newEntry) }
        enabled
    }

    private fun sendSetProfileReport(group: Byte, address: Byte, profile: MidiCIProfileId, enabled: Boolean) {
        val dst = MutableList<Byte>(config.receivableMaxSysExSize) { 0 }
        sendCIOutput(group, CIFactory.midiCIProfileReport(dst, address, enabled, muid, profile))
    }

    fun sendProfileAddedReport(group: Byte, profile: MidiCIProfile) {
        val dst = MutableList<Byte>(config.receivableMaxSysExSize) { 0 }
        sendCIOutput(group, CIFactory.midiCIProfileAddedRemoved(dst, profile.address, false, muid, profile.profile))
    }

    fun sendProfileRemovedReport(group: Byte, profile: MidiCIProfile) {
        val dst = MutableList<Byte>(config.receivableMaxSysExSize) { 0 }
        sendCIOutput(group, CIFactory.midiCIProfileAddedRemoved(dst, profile.address, true, muid, profile.profile))
    }

    fun defaultProcessSetProfileOn(msg: Message.SetProfileOn) {
        // send Profile Enabled Report only when it is actually enabled
        if (defaultProcessSetProfile(msg.group, msg.address, msg.sourceMUID, msg.destinationMUID, msg.profile, msg.numChannelsRequested, true))
            sendSetProfileReport(msg.group, msg.address, msg.profile, true)
    }
    fun defaultProcessSetProfileOff(msg: Message.SetProfileOff) {
        // send Profile Disabled Report only when it is actually disabled
        if (!defaultProcessSetProfile(msg.group, msg.address, msg.sourceMUID, msg.destinationMUID, msg.profile, 0, false))
            sendSetProfileReport(msg.group, msg.address, msg.profile, false)
    }

    var processSetProfileOn = { msg: Message.SetProfileOn ->
        logger.logMessage(msg, MessageDirection.In)
        events.setProfileOnReceived.forEach { it(msg) }
        defaultProcessSetProfileOn(msg)
    }
    var processSetProfileOff = { msg: Message.SetProfileOff ->
        logger.logMessage(msg, MessageDirection.In)
        events.setProfileOffReceived.forEach { it(msg) }
        defaultProcessSetProfileOff(msg)
    }

    fun sendProfileDetailsReply(msg: Message.ProfileDetailsReply) {
        val dst = MutableList<Byte>(config.receivableMaxSysExSize) { 0 }
        sendCIOutput(msg.group, CIFactory.midiCIProfileDetailsReply(dst, msg.address, msg.sourceMUID, msg.destinationMUID, msg.profile, msg.target, msg.data))
    }
    private fun getProfileDetailsReplyForInquiry(msg: Message.ProfileDetailsInquiry, data: List<Byte>) =
        Message.ProfileDetailsReply(Message.Common(muid, msg.sourceMUID, msg.address, msg.group),
            msg.profile, msg.target, data)
    fun defaultProcessProfileDetailsInquiry(msg: Message.ProfileDetailsInquiry) {
        val data = profileService.getProfileDetails(msg.profile, msg.target)
        if (data != null)
            sendProfileDetailsReply(getProfileDetailsReplyForInquiry(msg, data))
        else
            sendNakForError(Message.Common(muid, msg.sourceMUID, msg.address, msg.group),
                CISubId2.PROFILE_DETAILS_INQUIRY, 0, 0,
                message = "Profile Details Inquiry against unknown target (profile=${msg.profile}, target=${msg.target})")
    }

    var processProfileDetailsInquiry: (msg: Message.ProfileDetailsInquiry) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.profileDetailsInquiryReceived.forEach { it(msg) }
        defaultProcessProfileDetailsInquiry(msg)
    }

    var processProfileSpecificData: (msg: Message.ProfileSpecificData) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.profileSpecificDataReceived.forEach { it(msg) }
    }

    // Property Exchange

    var processSubscribeProperty: (msg: Message.SubscribeProperty) -> Unit = { msg ->
        // It may be either a new subscription, a property update notification, or end of subscription from either side
        val command = responder.propertyService.getHeaderFieldString(msg.header, PropertyCommonHeaderKeys.COMMAND)
        when (command) {
            MidiCISubscriptionCommand.START -> initiator.processSubscribeProperty(msg)
            MidiCISubscriptionCommand.FULL,
            MidiCISubscriptionCommand.PARTIAL,
            MidiCISubscriptionCommand.NOTIFY -> responder.processSubscribeProperty(msg)
            MidiCISubscriptionCommand.END -> {
                // We need to identify whether it is sent by the notifier or one of the subscribers
                // FIXME: select either of the targets
                initiator.processSubscribeProperty(msg) // for a subscriber
                responder.processSubscribeProperty(msg) // for the notifier
            }
        }
    }

    var processSubscribePropertyReply: (msg: Message.SubscribePropertyReply) -> Unit = { msg ->
        // It may be a reply to
        // - new subscription (contains new subscribeId) to initiator,
        // - value update (status) to responder
        // - end subscription from either side. We identify by subscriptionId
        //   (whether it is in our listening subscriptions xor it is request to unsubscribe from client)
        // We need to identify whether it is sent by the notifier or one of the subscribers
        // FIXME: select either of the targets
        initiator.processSubscribePropertyReply(msg) // for a subscriber
        responder.processSubscribePropertyReply(msg) // for the notifier
    }

    var processPropertyNotify: (propertyNotify: Message.PropertyNotify) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.propertyNotifyReceived.forEach { it(msg) }
        // no particular things to do. Event handlers should be used if any.
    }


    fun processInput(group: Byte, data: List<Byte>) {
        if (data[0] != MidiCIConstants.UNIVERSAL_SYSEX || data[2] != MidiCIConstants.SYSEX_SUB_ID_MIDI_CI)
            return // not MIDI-CI sysex
        if (data.size < Message.COMMON_HEADER_SIZE)
            return // insufficient buffer size in any case
        val common = Message.Common(
            CIRetrieval.midiCIGetSourceMUID(data),
            CIRetrieval.midiCIGetDestinationMUID(data),
            CIRetrieval.midiCIGetAddressing(data),
            group
            )
        if (data.size < (Message.messageSizes[data[3]] ?: Int.MAX_VALUE)) {
            logger.logError("Insufficient message size for ${data[3]}: ${data.size}")
            return // insufficient buffer size for the message
        }
        if (common.destinationMUID != muid && common.destinationMUID != MidiCIConstants.BROADCAST_MUID_32)
            return // we are not the target

        // catch errors for (potentially) insufficient buffer sizes
        try {
            processInputUnchecked(common, data)
        } catch(ex: IndexOutOfBoundsException) {
            val address = CIRetrieval.midiCIGetAddressing(data)
            val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
            sendNakForError(Message.Common(muid, sourceMUID, address, group), data[3], CINakStatus.MalformedMessage, 0, List(5) { 0 }, ex.message ?: ex.toString())
        }
    }

    private fun handleChunk(common: Message.Common, requestId: Byte, chunkIndex: Short, numChunks: Short,
                            header: List<Byte>, body: List<Byte>,
                            onComplete: (header: List<Byte>, body: List<Byte>) -> Unit) {
        val conn = connections[common.sourceMUID] ?: return
        if (chunkIndex < numChunks) {
            conn.pendingChunkManager.addPendingChunk(Clock.System.now().epochSeconds, common.sourceMUID, requestId, header, body)
        } else {
            val existing = if (chunkIndex > 1) conn.pendingChunkManager.finishPendingChunk(common.sourceMUID, requestId, body) else null
            val msgHeader = existing?.first ?: header
            val msgBody = existing?.second ?: body
            onComplete(msgHeader, msgBody)
        }
    }


    private fun processInputUnchecked(common: Message.Common, data: List<Byte>) {
        when (data[3]) {
            // Protocol Negotiation - we ignore them. Falls back to NAK

            // Discovery
            CISubId2.DISCOVERY_REPLY -> {
                val ciSupported = data[24]
                val device = CIRetrieval.midiCIGetDeviceDetails(data)
                val max = CIRetrieval.midiCIMaxSysExSize(data)
                // only available in MIDI-CI 1.2 or later.
                val initiatorOutputPath = if (data.size > 29) data[29] else 0
                val functionBlock = if (data.size > 30) data[30] else 0
                // Reply to Discovery
                processDiscoveryReply(Message.DiscoveryReply(
                    common, device, ciSupported, max, initiatorOutputPath, functionBlock))
            }
            CISubId2.ENDPOINT_MESSAGE_REPLY -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val status = data[13]
                val dataLength = data[14] + (data[15].toInt() shl 7)
                val dataValue = data.drop(16).take(dataLength)
                processEndpointReply(Message.EndpointReply(common, status, dataValue))
            }
            CISubId2.INVALIDATE_MUID -> {
                val targetMUID = CIRetrieval.midiCIGetMUIDToInvalidate(data)
                processInvalidateMUID(Message.InvalidateMUID(common, targetMUID))
            }
            CISubId2.ACK -> {
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
            CISubId2.NAK -> {
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
            CISubId2.PROFILE_INQUIRY_REPLY -> {
                val profiles = CIRetrieval.midiCIGetProfileSet(data)
                initiator.processProfileReply(Message.ProfileReply(
                    common,
                    profiles.filter { it.second }.map { it.first },
                    profiles.filter { !it.second }.map { it.first })
                )
            }
            CISubId2.PROFILE_ADDED_REPORT -> {
                val profile = CIRetrieval.midiCIGetProfileId(data)
                initiator.processProfileAddedReport(Message.ProfileAdded(common, profile))
            }
            CISubId2.PROFILE_REMOVED_REPORT -> {
                val profile = CIRetrieval.midiCIGetProfileId(data)
                initiator.processProfileRemovedReport(Message.ProfileRemoved(common, profile))
            }
            CISubId2.PROFILE_DETAILS_REPLY -> {
                val profile = CIRetrieval.midiCIGetProfileId(data)
                val target = data[18]
                val dataSize = data[19] + (data[20] shl 7)
                val details = data.drop(21).take(dataSize)
                initiator.processProfileDetailsReply(Message.ProfileDetailsReply(common, profile, target, details))
            }

            CISubId2.PROFILE_ENABLED_REPORT -> {
                val profile = CIRetrieval.midiCIGetProfileId(data)
                val channels = (data[18] + (data[19] shl 7)).toShort()
                initiator.processProfileEnabledReport(Message.ProfileEnabled(common, profile, channels))
            }
            CISubId2.PROFILE_DISABLED_REPORT -> {
                val profile = CIRetrieval.midiCIGetProfileId(data)
                val channels = (data[18] + (data[19] shl 7)).toShort()
                initiator.processProfileDisabledReport(Message.ProfileDisabled(common, profile, channels))
            }

            // Property Exchange
            CISubId2.PROPERTY_CAPABILITIES_REPLY -> {
                initiator.processPropertyCapabilitiesReply(Message.PropertyGetCapabilitiesReply(
                    common,
                    CIRetrieval.midiCIGetMaxPropertyRequests(data))
                )
            }
            CISubId2.PROPERTY_GET_DATA_REPLY -> {
                val header = CIRetrieval.midiCIGetPropertyHeader(data)
                val requestId = data[13]
                val numChunks = CIRetrieval.midiCIGetPropertyTotalChunks(data)
                val chunkIndex = CIRetrieval.midiCIGetPropertyChunkIndex(data)
                val body = CIRetrieval.midiCIGetPropertyBodyInThisChunk(data)

                handleChunk(common, requestId, chunkIndex, numChunks, header, body) { wholeHeader, wholeBody ->
                    initiator.processGetDataReply(Message.GetPropertyDataReply(common, requestId, wholeHeader, wholeBody))
                }
            }
            CISubId2.PROPERTY_SET_DATA_REPLY -> {
                val header = CIRetrieval.midiCIGetPropertyHeader(data)
                val requestId = data[13]
                initiator.processSetDataReply(Message.SetPropertyDataReply(
                    common, requestId, header))
            }
            CISubId2.PROPERTY_SUBSCRIBE -> {
                val requestId = data[13]
                val headerSize = data[14] + (data[15].toInt() shl 7)
                val header = data.drop(16).take(headerSize)
                val numChunks = CIRetrieval.midiCIGetPropertyTotalChunks(data)
                val chunkIndex = CIRetrieval.midiCIGetPropertyChunkIndex(data)
                val body = CIRetrieval.midiCIGetPropertyBodyInThisChunk(data)

                handleChunk(common, requestId, chunkIndex, numChunks, header, body) { wholeHeader, wholeBody ->
                    processSubscribeProperty(Message.SubscribeProperty(common, requestId, wholeHeader, wholeBody))
                }
            }
            CISubId2.PROPERTY_SUBSCRIBE_REPLY -> {
                val requestId = data[13]
                val headerSize = data[14] + (data[15].toInt() shl 7)
                val header = data.drop(16).take(headerSize)
                processSubscribePropertyReply(Message.SubscribePropertyReply(common, requestId, header, listOf()))
            }
            CISubId2.PROPERTY_NOTIFY -> {
                val requestId = data[13]
                val headerSize = data[14] + (data[15].toInt() shl 7)
                val header = data.drop(16).take(headerSize)
                val numChunks = CIRetrieval.midiCIGetPropertyTotalChunks(data)
                val chunkIndex = CIRetrieval.midiCIGetPropertyChunkIndex(data)
                val body = CIRetrieval.midiCIGetPropertyBodyInThisChunk(data)

                handleChunk(common, requestId, chunkIndex, numChunks, header, body) { wholeHeader, wholeBody ->
                    processPropertyNotify(Message.PropertyNotify(common, requestId, wholeHeader, wholeBody))
                }
            }

            // Process Inquiry
            CISubId2.PROCESS_INQUIRY_CAPABILITIES_REPLY -> {
                val supportedFeatures = data[13]
                initiator.processProcessInquiryReply(Message.ProcessInquiryReply(common, supportedFeatures))
            }
            CISubId2.PROCESS_INQUIRY_MIDI_MESSAGE_REPORT_REPLY -> {
                val systemMessages = data[13]
                // data[14] is reserved
                val channelControllerMessages = data[15]
                val noteDataMessages = data[16]
                initiator.processMidiMessageReportReply(Message.MidiMessageReportReply(
                    common, systemMessages, channelControllerMessages, noteDataMessages))
            }
            CISubId2.PROCESS_INQUIRY_END_OF_MIDI_MESSAGE -> {
                initiator.processEndOfMidiMessageReport(Message.MidiMessageReportNotifyEnd(common))
            }

            // Responder inputs

            // Discovery
            CISubId2.DISCOVERY_INQUIRY -> {
                val device = CIRetrieval.midiCIGetDeviceDetails(data)
                val ciSupported = data[24]
                val max = CIRetrieval.midiCIMaxSysExSize(data)
                // only available in MIDI-CI 1.2 or later.
                val initiatorOutputPath = if (data.size > 29) data[29] else 0
                processDiscovery(Message.DiscoveryInquiry(common, device, ciSupported, max, initiatorOutputPath))
            }

            CISubId2.ENDPOINT_MESSAGE_INQUIRY -> {
                // only available in MIDI-CI 1.2 or later.
                val status = data[13]
                processEndpointMessage(Message.EndpointInquiry(common, status))
            }

            // Profile Configuration

            CISubId2.PROFILE_INQUIRY -> {
                processProfileInquiry(Message.ProfileInquiry(common))
            }

            CISubId2.SET_PROFILE_ON, CISubId2.SET_PROFILE_OFF -> {
                val enabled = data[3] == CISubId2.SET_PROFILE_ON
                val profileId = CIRetrieval.midiCIGetProfileId(data)
                val channels = (data[18] + (data[19] shl 7)).toShort()
                if (enabled)
                    processSetProfileOn(Message.SetProfileOn(common, profileId, channels))
                else
                    processSetProfileOff(Message.SetProfileOff(common, profileId))
            }

            CISubId2.PROFILE_DETAILS_INQUIRY -> {
                val profileId = CIRetrieval.midiCIGetProfileId(data)
                val target = data[13]
                processProfileDetailsInquiry(Message.ProfileDetailsInquiry(common, profileId, target))
            }

            CISubId2.PROFILE_SPECIFIC_DATA -> {
                val profileId = CIRetrieval.midiCIGetProfileId(data)
                val dataLength = CIRetrieval.midiCIGetProfileSpecificDataSize(data)
                processProfileSpecificData(Message.ProfileSpecificData(common, profileId, data.drop(21).take(dataLength)))
            }

            // Property Exchange

            CISubId2.PROPERTY_CAPABILITIES_INQUIRY -> {
                val max = CIRetrieval.midiCIGetMaxPropertyRequests(data)

                responder.processPropertyCapabilitiesInquiry(
                    Message.PropertyGetCapabilities(common, max))
            }

            CISubId2.PROPERTY_GET_DATA_INQUIRY -> {
                val requestId = data[13]
                val header = CIRetrieval.midiCIGetPropertyHeader(data)
                responder.processGetPropertyData(Message.GetPropertyData(common, requestId, header))
            }

            CISubId2.PROPERTY_SET_DATA_INQUIRY -> {
                val requestId = data[13]
                val header = CIRetrieval.midiCIGetPropertyHeader(data)
                val numChunks = CIRetrieval.midiCIGetPropertyTotalChunks(data)
                val chunkIndex = CIRetrieval.midiCIGetPropertyChunkIndex(data)
                val body = CIRetrieval.midiCIGetPropertyBodyInThisChunk(data)

                handleChunk(common, requestId, chunkIndex, numChunks, header, body) { wholeHeader, wholeBody ->
                    responder.processSetPropertyData(Message.SetPropertyData(common, requestId, wholeHeader, wholeBody))
                }
            }

            // CISubId2.PROPERTY_SUBSCRIBE -> implemented earlier
            // CISubId2.PROPERTY_SUBSCRIBE_REPLY -> implemented earlier
            // CISubId2.PROPERTY_NOTIFY -> implemented earlier

            CISubId2.PROCESS_INQUIRY_CAPABILITIES -> {
                responder.processProcessInquiry(Message.ProcessInquiry(common))
            }

            CISubId2.PROCESS_INQUIRY_MIDI_MESSAGE_REPORT -> {
                val messageDataControl = data[13]
                val systemMessages = data[14]
                // data[15] is reserved
                val channelControllerMessages = data[16]
                val noteDataMessages = data[17]
                responder.processMidiMessageReport(Message.MidiMessageReportInquiry(
                    common, messageDataControl, systemMessages, channelControllerMessages, noteDataMessages))
            }

            else -> {
                processUnknownCIMessage(common.group, data)
            }
        }
    }
}