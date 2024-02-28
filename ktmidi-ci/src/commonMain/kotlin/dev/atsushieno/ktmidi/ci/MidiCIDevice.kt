package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.Message.Companion.muidString
import dev.atsushieno.ktmidi.ci.profilecommonrules.CommonRulesProfileService
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyCommonHeaderKeys
import kotlinx.datetime.Clock
import kotlin.experimental.and

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
                   private val sendMidiMessageReport: (group: Byte, protocol: MidiMessageReportProtocol, data: List<Byte>) -> Unit
) {
    var requestIdSerial: Byte = 1

    val initiator by lazy { PropertyExchangeInitiator(this, config) }
    val responder by lazy { PropertyExchangeResponder(this, config) }

    val device: MidiCIDeviceInfo
        get() = config.device


    val unknownMessageReceived = mutableListOf<(data: List<Byte>) -> Unit>()
    val messageReceived = mutableListOf<(msg: Message) -> Unit>()

    val logger = Logger()

    val connections = mutableMapOf<Int, ClientConnection>()
    val connectionsChanged = mutableListOf<(change: ConnectionChange, connection: ClientConnection) -> Unit>()

    private var profileService: MidiCIProfileRules = CommonRulesProfileService()
    val localProfiles = ObservableProfileList(config.localProfiles)
    // These events are invoked when it received Set Profile On/Off request from Initiator.
    val onProfileSet = mutableListOf<(profile: MidiCIProfile) -> Unit>()

    var midiMessageReporter: MidiMessageReporter = object : MidiMessageReporter {
        // stub implementation
        override val midiTransportProtocol = MidiMessageReportProtocol.Midi1Stream

        override fun reportMidiMessages(
            groupAddress: Byte,
            channelAddress: Byte,
            messageDataControl: Byte,
            midiMessageReportSystemMessages: Byte,
            midiMessageReportChannelControllerMessages: Byte,
            midiMessageReportNoteDataMessages: Byte
        ): Sequence<List<Byte>> = sequenceOf()
    }

    // Request sender

    fun send(msg: Message) {
        logger.logMessage(msg, MessageDirection.Out)
        msg.serializeMulti(config).forEach {
            sendCIOutput(msg.group, it)
        }
    }

    fun sendDiscovery(group: Byte, ciCategorySupported: Byte = MidiCISupportedCategories.THREE_P) =
        send(Message.DiscoveryInquiry(
            Message.Common(muid, MidiCIConstants.BROADCAST_MUID_32, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group),
            DeviceDetails(
                device.manufacturerId,
                device.familyId,
                device.modelId,
                device.versionId
            ),
            ciCategorySupported, config.receivableMaxSysExSize, config.outputPathId))

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
        send(Message.Nak(common, originalSubId2, statusCode, statusData, details,
            MidiCIConverter.encodeStringToASCII(message).toASCIIByteArray().toList()))
    }

    fun invalidateMUID(address: Byte, group: Byte, targetMUID: Int, message: String) {
        logger.logError(message)
        send(Message.InvalidateMUID(Message.Common(muid, targetMUID, address, group), targetMUID))
    }

    // Input handlers

    var processUnknownCIMessage: (common: Message.Common, data: List<Byte>) -> Unit = { common, data ->
        logger.nak(common, data, MessageDirection.In)
        unknownMessageReceived.forEach { it(data) }
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
        messageReceived.forEach { it(msg) }
        defaultProcessInvalidateMUID(msg)
    }

    // to Discovery (responder)
    fun getDiscoveryReplyForInquiry(request: Message.DiscoveryInquiry): Message.DiscoveryReply {
        val deviceDetails = DeviceDetails(device.manufacturerId, device.familyId, device.modelId, device.versionId)
        return Message.DiscoveryReply(Message.Common(muid, request.sourceMUID, request.address, request.group),
            deviceDetails, config.capabilityInquirySupported,
            config.receivableMaxSysExSize, request.outputPathId, config.functionBlock)
    }
    var processDiscovery: (msg: Message.DiscoveryInquiry) -> Unit = { msg ->
        messageReceived.forEach { it(msg) }
        send(getDiscoveryReplyForInquiry(msg))
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
        messageReceived.forEach { it(msg) }
        send(getEndpointReplyForInquiry(msg))
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
        messageReceived.forEach { it(msg) }
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
        send(Message.EndpointInquiry(Message.Common(muid, targetMuid, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group), status))

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
        messageReceived.forEach { it(msg) }
        defaultProcessEndpointReply(msg)
    }

    // Remote Profile Configuration

    // FIXME: most of them should move into ProfileClient

    fun requestProfiles(group: Byte, address: Byte, destinationMUID: Int) =
        send(Message.ProfileInquiry(Message.Common(muid, destinationMUID, address, group)))

    fun requestProfileDetails(group: Byte, address: Byte, targetMUID: Int, profile: MidiCIProfileId, target: Byte) =
        send(Message.ProfileDetailsInquiry(Message.Common(muid, targetMUID, address, group),
            profile, target))

    var processProfileReply = { msg: Message.ProfileReply ->
        messageReceived.forEach { it(msg) }
        continueOnClient(msg, CISubId2.PROFILE_INQUIRY_REPLY) { it.profileClient.processProfileReply(msg) }
    }

    var processProfileAddedReport = { msg: Message.ProfileAdded ->
        messageReceived.forEach { it(msg) }
        continueOnClient(msg, CISubId2.PROFILE_ADDED_REPORT) { it.profileClient.processProfileAddedReport(msg) }
    }

    var processProfileRemovedReport: (msg: Message.ProfileRemoved) -> Unit = { msg ->
        messageReceived.forEach { it(msg) }
        continueOnClient(msg, CISubId2.PROFILE_REMOVED_REPORT) { it.profileClient.processProfileRemovedReport(msg) }
    }

    var processProfileEnabledReport: (msg: Message.ProfileEnabled) -> Unit = { msg ->
        messageReceived.forEach { it(msg) }
        continueOnClient(msg, CISubId2.PROFILE_ENABLED_REPORT) { it.profileClient.processProfileEnabledReport(msg) }
    }
    var processProfileDisabledReport: (msg: Message.ProfileDisabled) -> Unit = { msg ->
        messageReceived.forEach { it(msg) }
        continueOnClient(msg, CISubId2.PROFILE_DISABLED_REPORT) { it.profileClient.processProfileDisabledReport(msg) }
    }

    var processProfileDetailsReply: (msg: Message.ProfileDetailsReply) -> Unit = { msg ->
        messageReceived.forEach { it(msg) }
        continueOnClient(msg, CISubId2.PROFILE_DETAILS_REPLY) { it.profileClient.processProfileDetailsReply(msg) }
    }

    // Local Profile Configuration

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
        messageReceived.forEach { it(msg) }
        getProfileRepliesForInquiry(msg).forEach { reply ->
            send(reply)
        }
    }

    private fun sendSetProfileEnabled(group: Byte, address: Byte, profile: MidiCIProfileId, numChannelsEnabled: Short) {
        send(Message.ProfileEnabled(Message.Common(muid, MidiCIConstants.BROADCAST_MUID_32, address, group), profile, numChannelsEnabled))
    }
    private fun sendSetProfileDisabled(group: Byte, address: Byte, profile: MidiCIProfileId, numChannelsDisabled: Short) {
        send(Message.ProfileDisabled(Message.Common(muid, MidiCIConstants.BROADCAST_MUID_32, address, group), profile, numChannelsDisabled))
    }

    fun sendProfileAddedReport(group: Byte, profile: MidiCIProfile) {
        send(Message.ProfileAdded(Message.Common(muid, MidiCIConstants.BROADCAST_MUID_32, profile.address, group), profile.profile))
    }

    fun sendProfileRemovedReport(group: Byte, profile: MidiCIProfile) {
        send(Message.ProfileRemoved(Message.Common(muid, MidiCIConstants.BROADCAST_MUID_32, profile.address, group), profile.profile))
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
        messageReceived.forEach { it(msg) }
        defaultProcessSetProfileOn(msg)
    }
    var processSetProfileOff = { msg: Message.SetProfileOff ->
        messageReceived.forEach { it(msg) }
        defaultProcessSetProfileOff(msg)
    }

    fun defaultProcessProfileDetailsInquiry(msg: Message.ProfileDetailsInquiry) {
        val data = profileService.getProfileDetails(msg.profile, msg.target)
        val reply = Message.ProfileDetailsReply(Message.Common(muid, msg.sourceMUID, msg.address, msg.group),
            msg.profile, msg.target, data ?: listOf())
        if (data != null)
            send(reply)
        else
            sendNakForError(Message.Common(muid, msg.sourceMUID, msg.address, msg.group),
                CISubId2.PROFILE_DETAILS_INQUIRY, CINakStatus.ProfileNotSupportedOnTarget, 0,
                message = "Profile Details Inquiry against unknown target (profile=${msg.profile}, target=${msg.target})")
    }

    var processProfileDetailsInquiry: (msg: Message.ProfileDetailsInquiry) -> Unit = { msg ->
        messageReceived.forEach { it(msg) }
        defaultProcessProfileDetailsInquiry(msg)
    }

    var processProfileSpecificData: (msg: Message.ProfileSpecificData) -> Unit = { msg ->
        messageReceived.forEach { it(msg) }
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
        messageReceived.forEach { it(msg) }
        // no particular things to do. Event handlers should be used if any.
    }

    // Remote Process Inquiry
    fun sendProcessInquiry(group: Byte, destinationMUID: Int) =
        send(Message.ProcessInquiryCapabilities(Message.Common(muid, destinationMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group)))

    fun sendMidiMessageReportInquiry(group: Byte, address: Byte, destinationMUID: Int,
                                     messageDataControl: Byte,
                                     systemMessages: Byte,
                                     channelControllerMessages: Byte,
                                     noteDataMessages: Byte) =
        send(Message.MidiMessageReportInquiry(
            Message.Common(muid, destinationMUID, address, group),
            messageDataControl, systemMessages, channelControllerMessages, noteDataMessages))

    var processProcessInquiryReply: (msg: Message.ProcessInquiryCapabilitiesReply) -> Unit = { msg ->
        messageReceived.forEach { it(msg) }
        // no particular things to do. Event handlers should be used if any.
    }

    var processMidiMessageReportReply: (msg: Message.MidiMessageReportReply) -> Unit = { msg ->
        messageReceived.forEach { it(msg) }
        // no particular things to do. Event handlers should be used if any.
    }

    var processEndOfMidiMessageReport: (msg: Message.MidiMessageReportNotifyEnd) -> Unit = { msg ->
        messageReceived.forEach { it(msg) }
        // no particular things to do. Event handlers should be used if any.
    }

    // Local Process Inquiry

    fun getProcessInquiryReplyFor(msg: Message.ProcessInquiryCapabilities) =
        Message.ProcessInquiryCapabilitiesReply(Message.Common(muid, msg.sourceMUID, msg.address, msg.group),
            config.processInquirySupportedFeatures)
    var processProcessInquiry: (msg: Message.ProcessInquiryCapabilities) -> Unit = { msg ->
        messageReceived.forEach { it(msg) }
        send(getProcessInquiryReplyFor(msg))
    }

    fun getMidiMessageReportReplyFor(msg: Message.MidiMessageReportInquiry) =
        Message.MidiMessageReportReply(Message.Common(muid, msg.sourceMUID, msg.address, msg.group),
            msg.systemMessages and config.midiMessageReportSystemMessages,
            msg.channelControllerMessages and config.midiMessageReportChannelControllerMessages,
            msg.noteDataMessages and config.midiMessageReportNoteDataMessages)
    fun getEndOfMidiMessageReportFor(msg: Message.MidiMessageReportInquiry) =
        Message.MidiMessageReportNotifyEnd(Message.Common(muid, msg.sourceMUID, msg.address, msg.group))
    fun defaultProcessMidiMessageReport(msg: Message.MidiMessageReportInquiry) {
        send(getMidiMessageReportReplyFor(msg))

        // send specified MIDI messages
        midiMessageReporter.reportMidiMessages(
            msg.group,
            msg.address,
            config.midiMessageReportMessageDataControl,
            config.midiMessageReportSystemMessages,
            config.midiMessageReportChannelControllerMessages,
            config.midiMessageReportNoteDataMessages
        ).forEach {
            sendMidiMessageReport(msg.group, midiMessageReporter.midiTransportProtocol, it)
        }

        send(getEndOfMidiMessageReportFor(msg))
    }
    var processMidiMessageReport: (msg: Message.MidiMessageReportInquiry) -> Unit = { msg ->
        messageReceived.forEach { it(msg) }
        defaultProcessMidiMessageReport(msg)
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
                processProcessInquiryReply(Message.ProcessInquiryCapabilitiesReply(common, supportedFeatures))
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
                processProcessInquiry(Message.ProcessInquiryCapabilities(common))
            }

            CISubId2.PROCESS_INQUIRY_MIDI_MESSAGE_REPORT -> {
                val messageDataControl = data[13]
                val systemMessages = data[14]
                // data[15] is reserved
                val channelControllerMessages = data[16]
                val noteDataMessages = data[17]
                processMidiMessageReport(Message.MidiMessageReportInquiry(
                    common, messageDataControl, systemMessages, channelControllerMessages, noteDataMessages))
            }

            else -> {
                processUnknownCIMessage(common, data)
            }
        }
    }

    init {
        if (muid != muid and 0x7F7F7F7F)
            throw IllegalArgumentException("muid must consist of 7-bit byte values i.e. each 8-bit number must not have the topmost bit as 0. (`muid` must be equivalent to `muid and 0x7F7F7F7F`")
        messageReceived.add { logger.logMessage(it, MessageDirection.In) }
    }
}