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
    val initiator by lazy { MidiCIInitiator(this, config, sendCIOutput) }
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

    val connections = mutableMapOf<Int, ClientConnection>()
    val connectionsChanged = mutableListOf<(change: ConnectionChange, connection: ClientConnection) -> Unit>()

    private var profileService: MidiCIProfileService = CommonRulesProfileService()
    val localProfiles = ObservableProfileList(config.localProfiles)
    // These events are invoked when it received Set Profile On/Off request from Initiator.
    val onProfileSet = mutableListOf<(profile: MidiCIProfile) -> Unit>()

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
        sendCIOutput(msg.group, msg.serialize(config))
    }

    fun sendNakForUnknownCIMessage(group: Byte, data: List<Byte>) {
        val dst = MutableList<Byte>(config.receivableMaxSysExSize) { 0 }
        val source = data[1]
        val originalSubId = data[3]
        val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
        val destinationMUID = CIRetrieval.midiCIGetDestinationMUID(data)
        sendCIOutput(group, CIFactory.midiCIAckNak(dst, true, source,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, destinationMUID, originalSubId,
            CINakStatus.MessageNotSupported, 0, listOf(), listOf()))
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
        sendCIOutput(msg.group, msg.serialize(config))
    }

    fun invalidateMUID(address: Byte, group: Byte, targetMUID: Int, message: String) {
        logger.logError(message)
        val msg = Message.InvalidateMUID(Message.Common(muid, targetMUID, address, group), targetMUID)
        invalidateMUID(msg)
    }

    fun invalidateMUID(msg: Message.InvalidateMUID) {
        logger.logMessage(msg, MessageDirection.Out)
        sendCIOutput(msg.group, msg.serialize(config))
    }

    // Input handlers

    var processUnknownCIMessage: (common: Message.Common, data: List<Byte>) -> Unit = { common, data ->
        logger.nak(common, data, MessageDirection.In)
        events.unknownMessageReceived.forEach { it(data) }
        sendNakForUnknownCIMessage(common.group, data)
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
    fun sendDiscoveryReply(msg: Message.DiscoveryReply) {
        logger.logMessage(msg, MessageDirection.Out)
        sendCIOutput(msg.group, msg.serialize(config))
    }
    fun getDiscoveryReplyForInquiry(request: Message.DiscoveryInquiry): Message.DiscoveryReply {
        val deviceDetails = DeviceDetails(device.manufacturerId, device.familyId, device.modelId, device.versionId)
        return Message.DiscoveryReply(Message.Common(muid, request.sourceMUID, request.address, request.group),
            deviceDetails, config.capabilityInquirySupported,
            config.receivableMaxSysExSize, request.outputPathId, config.functionBlock)
    }
    var processDiscovery: (msg: Message.DiscoveryInquiry) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.discoveryReceived.forEach { it(msg) }
        val reply = getDiscoveryReplyForInquiry(msg)
        sendDiscoveryReply(reply)
    }

    fun sendEndpointReply(msg: Message.EndpointReply) {
        logger.logMessage(msg, MessageDirection.Out)
        sendCIOutput(msg.group, msg.serialize(config))
    }
    fun getEndpointReplyForInquiry(msg: Message.EndpointInquiry): Message.EndpointReply {
        val prodId = config.productInstanceId
        if (prodId.length > 16 || prodId.any { it.code < 0x20 || it.code > 0x7E })
            throw IllegalStateException("productInstanceId shall not be any longer than 16 bytes in size and must be all in ASCII code between 32 and 126")
        return Message.EndpointReply(Message.Common(muid, msg.sourceMUID, msg.address, msg.group),
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
    fun handleNewEndpoint(msg: Message.DiscoveryReply) {
        // If successfully discovered, continue to endpoint inquiry
        val connection = ClientConnection(initiator, msg.sourceMUID, msg.device)
        val existing = connections[msg.sourceMUID]
        if (existing != null) {
            connections.remove(msg.sourceMUID)
            connectionsChanged.forEach { it(ConnectionChange.Removed, existing) }
        }
        connections[msg.sourceMUID]= connection
        connectionsChanged.forEach { it(ConnectionChange.Added, connection) }

        if (config.autoSendEndpointInquiry)
            sendEndpointMessage(msg.group, msg.sourceMUID, MidiCIConstants.ENDPOINT_STATUS_PRODUCT_INSTANCE_ID)

        if (config.autoSendProfileInquiry && (msg.ciCategorySupported.toInt() and MidiCISupportedCategories.PROFILE_CONFIGURATION.toInt()) != 0)
            requestProfiles(msg.group, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, msg.sourceMUID)
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


    fun sendEndpointMessage(group: Byte, targetMuid: Int, status: Byte = MidiCIConstants.ENDPOINT_STATUS_PRODUCT_INSTANCE_ID) =
        sendEndpointMessage(Message.EndpointInquiry(Message.Common(muid, targetMuid, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group), status))
    fun sendEndpointMessage(msg: Message.EndpointInquiry) {
        logger.logMessage(msg, MessageDirection.Out)
        sendCIOutput(msg.group, msg.serialize(config))
    }

    fun defaultProcessEndpointReply(msg: Message.EndpointReply) {
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

    // Remote Profile Configuration

    // FIXME: most of them should move into ProfileClient

    fun requestProfiles(group: Byte, address: Byte, destinationMUID: Int) =
        sendProfileInquiry(Message.ProfileInquiry(Message.Common(muid, destinationMUID, address, group)))
    internal fun sendProfileInquiry(msg: Message.ProfileInquiry) {
        logger.logMessage(msg, MessageDirection.Out)
        sendCIOutput(msg.group, msg.serialize(config))
    }

    fun requestProfileDetails(group: Byte, address: Byte, targetMUID: Int, profile: MidiCIProfileId, target: Byte) =
        sendProfileDetailsInquiry(Message.ProfileDetailsInquiry(Message.Common(muid, targetMUID, address, group),
            profile, target))
    internal fun sendProfileDetailsInquiry(msg: Message.ProfileDetailsInquiry) {
        logger.logMessage(msg, MessageDirection.Out)
        sendCIOutput(msg.group, msg.serialize(config))
    }

    internal fun sendSetProfileOn(msg: Message.SetProfileOn) {
        logger.logMessage(msg, MessageDirection.Out)
        sendCIOutput(msg.group, msg.serialize(config))
    }

    internal fun sendSetProfileOff(msg: Message.SetProfileOff) {
        logger.logMessage(msg, MessageDirection.Out)
        sendCIOutput(msg.group, msg.serialize(config))
    }

    var processProfileReply = { msg: Message.ProfileReply ->
        logger.logMessage(msg, MessageDirection.In)
        events.profileInquiryReplyReceived.forEach { it(msg) }
        continueOnClient(msg, CISubId2.PROFILE_INQUIRY_REPLY) { it.profileClient.processProfileReply(msg) }
    }

    var processProfileAddedReport = { msg: Message.ProfileAdded ->
        logger.logMessage(msg, MessageDirection.In)
        events.profileAddedReceived.forEach { it(msg) }
        continueOnClient(msg, CISubId2.PROFILE_ADDED_REPORT) { it.profileClient.processProfileAddedReport(msg) }
    }

    var processProfileRemovedReport: (msg: Message.ProfileRemoved) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.profileRemovedReceived.forEach { it(msg) }
        continueOnClient(msg, CISubId2.PROFILE_REMOVED_REPORT) { it.profileClient.processProfileRemovedReport(msg) }
    }

    var processProfileEnabledReport: (msg: Message.ProfileEnabled) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.profileEnabledReceived.forEach { it(msg) }
        continueOnClient(msg, CISubId2.PROFILE_ENABLED_REPORT) { it.profileClient.processProfileEnabledReport(msg) }
    }
    var processProfileDisabledReport: (msg: Message.ProfileDisabled) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.profileDisabledReceived.forEach { it(msg) }
        continueOnClient(msg, CISubId2.PROFILE_DISABLED_REPORT) { it.profileClient.processProfileDisabledReport(msg) }
    }

    var processProfileDetailsReply: (msg: Message.ProfileDetailsReply) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.profileDetailsReplyReceived.forEach { it(msg) }
        continueOnClient(msg, CISubId2.PROFILE_DETAILS_REPLY) { it.profileClient.processProfileDetailsReply(msg) }
    }

    // Local Profile Configuration

    fun sendProfileReply(msg: Message.ProfileReply) {
        logger.logMessage(msg, MessageDirection.Out)
        sendCIOutput(msg.group, msg.serialize(config))
    }

    private fun getAllAddresses(address: Byte) = sequence {
        if (address == MidiCIConstants.ADDRESS_FUNCTION_BLOCK)
            yieldAll(localProfiles.profiles.map { it.address }.distinct().sorted())
        else
            yield(address)
    }
    fun getProfileRepliesForInquiry(msg: Message.ProfileInquiry): Sequence<Message.ProfileReply> = sequence {
        getAllAddresses(msg.address).forEach { address ->
            yield(Message.ProfileReply(Message.Common(muid, msg.sourceMUID, address, msg.group),
                localProfiles.getMatchingProfiles(address, true),
                localProfiles.getMatchingProfiles(address, false)))
        }
    }
    var processProfileInquiry: (msg: Message.ProfileInquiry) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.profileInquiryReceived.forEach { it(msg) }
        getProfileRepliesForInquiry(msg).forEach { reply ->
            sendProfileReply(reply)
        }
    }

    private fun sendSetProfileEnabled(group: Byte, address: Byte, profile: MidiCIProfileId, numChannelsEnabled: Short) {
        val msg = Message.ProfileEnabled(Message.Common(muid, MidiCIConstants.BROADCAST_MUID_32, address, group), profile, numChannelsEnabled)
        sendCIOutput(group, msg.serialize(config))
    }
    private fun sendSetProfileDisabled(group: Byte, address: Byte, profile: MidiCIProfileId, numChannelsDisabled: Short) {
        val msg = Message.ProfileDisabled(Message.Common(muid, MidiCIConstants.BROADCAST_MUID_32, address, group), profile, numChannelsDisabled)
        sendCIOutput(group, msg.serialize(config))
    }

    fun sendProfileAddedReport(group: Byte, profile: MidiCIProfile) {
        val dst = MutableList<Byte>(config.receivableMaxSysExSize) { 0 }
        sendCIOutput(group, CIFactory.midiCIProfileAddedRemoved(dst, profile.address, false, muid, profile.profile))
    }

    fun sendProfileRemovedReport(group: Byte, profile: MidiCIProfile) {
        val dst = MutableList<Byte>(config.receivableMaxSysExSize) { 0 }
        sendCIOutput(group, CIFactory.midiCIProfileAddedRemoved(dst, profile.address, true, muid, profile.profile))
    }

    private fun defaultProcessSetProfile(group: Byte, address: Byte, profile: MidiCIProfileId, numChannelsRequested: Short, enabled: Boolean): Boolean {
        val newEntry = MidiCIProfile(profile, group, address, enabled, numChannelsRequested)
        val existing = localProfiles.profiles.firstOrNull { it.profile == profile && it.address == address }
        if (existing != null) {
            if (existing.enabled == enabled)
                return enabled // do not perform anything and return current state
            localProfiles.remove(existing)
        }
        localProfiles.add(newEntry)
        onProfileSet.forEach { it(newEntry) }
        return enabled
    }

    fun defaultProcessSetProfileOn(msg: Message.SetProfileOn) {
        // send Profile Enabled Report only when it is actually enabled
        if (defaultProcessSetProfile(msg.group, msg.address, msg.profile, msg.numChannelsRequested, true))
            sendSetProfileEnabled(msg.group, msg.address, msg.profile, msg.numChannelsRequested)
    }
    fun defaultProcessSetProfileOff(msg: Message.SetProfileOff) {
        // send Profile Disabled Report only when it is actually disabled
        if (!defaultProcessSetProfile(msg.group, msg.address, msg.profile, 0, false))
            // FIXME: supply numChannelsDisabled
            sendSetProfileDisabled(msg.group, msg.address, msg.profile, 1)
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
        sendCIOutput(msg.group, msg.serialize(config))
    }
    fun defaultProcessProfileDetailsInquiry(msg: Message.ProfileDetailsInquiry) {
        val data = profileService.getProfileDetails(msg.profile, msg.target)
        val reply = Message.ProfileDetailsReply(Message.Common(muid, msg.sourceMUID, msg.address, msg.group),
            msg.profile, msg.target, data ?: listOf())
        if (data != null)
            sendProfileDetailsReply(reply)
        else
            sendNakForError(Message.Common(muid, msg.sourceMUID, msg.address, msg.group),
                CISubId2.PROFILE_DETAILS_INQUIRY, CINakStatus.ProfileNotSupportedOnTarget, 0,
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

    // Local Property Exchange

    var processSubscribeProperty: (msg: Message.SubscribeProperty) -> Unit = { msg ->
        // It may be either a new subscription, a property update notification, or end of subscription from either side
        val command = responder.propertyService.getHeaderFieldString(msg.header, PropertyCommonHeaderKeys.COMMAND)
        when (command) {
            MidiCISubscriptionCommand.START -> responder.processSubscribeProperty(msg)
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

    // Remote Process Inquiry
    fun sendProcessInquiry(group: Byte, destinationMUID: Int) =
        sendProcessInquiry(Message.ProcessInquiry(Message.Common(muid, destinationMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group)))

    fun sendProcessInquiry(msg: Message.ProcessInquiry) {
        logger.logMessage(msg, MessageDirection.Out)
        sendCIOutput(msg.group, msg.serialize(config))
    }

    fun sendMidiMessageReportInquiry(group: Byte, address: Byte, destinationMUID: Int,
                                     messageDataControl: Byte,
                                     systemMessages: Byte,
                                     channelControllerMessages: Byte,
                                     noteDataMessages: Byte) =
        sendMidiMessageReportInquiry(Message.MidiMessageReportInquiry(
            Message.Common(muid, destinationMUID, address, group),
            messageDataControl, systemMessages, channelControllerMessages, noteDataMessages))

    internal fun sendMidiMessageReportInquiry(msg: Message.MidiMessageReportInquiry) {
        logger.logMessage(msg, MessageDirection.Out)
        sendCIOutput(msg.group, msg.serialize(config))
    }

    var processProcessInquiryReply: (msg: Message.ProcessInquiryReply) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.processInquiryReplyReceived.forEach { it(msg) }
        // no particular things to do. Event handlers should be used if any.
    }

    var processMidiMessageReportReply: (msg: Message.MidiMessageReportReply) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.midiMessageReportReplyReceived.forEach { it(msg) }
        // no particular things to do. Event handlers should be used if any.
    }

    var processEndOfMidiMessageReport: (msg: Message.MidiMessageReportNotifyEnd) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.endOfMidiMessageReportReceived.forEach { it(msg) }
        // no particular things to do. Event handlers should be used if any.
    }

    // processInput()

    private val emptyNakDetails = List<Byte>(5) {0}

    private fun <T> continueOnClient(msg: T, subId2: Byte, func: (conn: ClientConnection)->Unit) where T: Message {
        val conn = connections[msg.sourceMUID]
        if (conn != null)
            func(conn)
        else
            sendNakForError(getResponseCommonForInput(msg.common), subId2, CINakStatus.Nak, 0, emptyNakDetails, "Profile Reply from unknown MUID")
    }

    private fun getResponseCommonForInput(common: Message.Common) =
        Message.Common(muid, common.sourceMUID, common.address, common.group)

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
        if (data[4] != MidiCIConstants.CI_VERSION_AND_FORMAT)
            sendNakForError(getResponseCommonForInput(common), data[3], CINakStatus.CIVersionNotSupported, 0, emptyNakDetails, "")
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
            sendNakForError(getResponseCommonForInput(common), data[3], CINakStatus.MalformedMessage, 0, emptyNakDetails, ex.message ?: ex.toString())
        }
    }

    private val localPendingChunkManager = PropertyChunkManager()

    private fun handleChunk(common: Message.Common, requestId: Byte, chunkIndex: Short, numChunks: Short,
                            header: List<Byte>, body: List<Byte>,
                            onComplete: (header: List<Byte>, body: List<Byte>) -> Unit) {
        val pendingChunkManager = connections[common.sourceMUID]?.pendingChunkManager ?: localPendingChunkManager
        if (chunkIndex < numChunks) {
            pendingChunkManager.addPendingChunk(Clock.System.now().epochSeconds, common.sourceMUID, requestId, header, body)
        } else {
            val existing = if (chunkIndex > 1) pendingChunkManager.finishPendingChunk(common.sourceMUID, requestId, body) else null
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
                processProfileReply(Message.ProfileReply(
                    common,
                    profiles.filter { it.second }.map { it.first },
                    profiles.filter { !it.second }.map { it.first })
                )
            }
            CISubId2.PROFILE_ADDED_REPORT -> {
                val profile = CIRetrieval.midiCIGetProfileId(data)
                processProfileAddedReport(Message.ProfileAdded(common, profile))
            }
            CISubId2.PROFILE_REMOVED_REPORT -> {
                val profile = CIRetrieval.midiCIGetProfileId(data)
                processProfileRemovedReport(Message.ProfileRemoved(common, profile))
            }
            CISubId2.PROFILE_DETAILS_REPLY -> {
                val profile = CIRetrieval.midiCIGetProfileId(data)
                val target = data[18]
                val dataSize = data[19] + (data[20] shl 7)
                val details = data.drop(21).take(dataSize)
                processProfileDetailsReply(Message.ProfileDetailsReply(common, profile, target, details))
            }

            CISubId2.PROFILE_ENABLED_REPORT -> {
                val profile = CIRetrieval.midiCIGetProfileId(data)
                val channels = (data[18] + (data[19] shl 7)).toShort()
                processProfileEnabledReport(Message.ProfileEnabled(common, profile, channels))
            }
            CISubId2.PROFILE_DISABLED_REPORT -> {
                val profile = CIRetrieval.midiCIGetProfileId(data)
                val channels = (data[18] + (data[19] shl 7)).toShort()
                processProfileDisabledReport(Message.ProfileDisabled(common, profile, channels))
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
                processProcessInquiryReply(Message.ProcessInquiryReply(common, supportedFeatures))
            }
            CISubId2.PROCESS_INQUIRY_MIDI_MESSAGE_REPORT_REPLY -> {
                val systemMessages = data[13]
                // data[14] is reserved
                val channelControllerMessages = data[15]
                val noteDataMessages = data[16]
                processMidiMessageReportReply(Message.MidiMessageReportReply(
                    common, systemMessages, channelControllerMessages, noteDataMessages))
            }
            CISubId2.PROCESS_INQUIRY_END_OF_MIDI_MESSAGE -> {
                processEndOfMidiMessageReport(Message.MidiMessageReportNotifyEnd(common))
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
                processUnknownCIMessage(common, data)
            }
        }
    }
}