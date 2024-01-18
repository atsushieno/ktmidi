package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.Message.Companion.muidString
import dev.atsushieno.ktmidi.ci.propertycommonrules.CommonRulesPropertyClient
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyCommonHeaderKeys
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyExchangeStatus
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/*
    Typical MIDI-CI processing flow

    - MidiCIInitiator.sendDiscovery()
    - MidiCIResponder.processInput() -> .processDiscovery
    - MidiCIInitiator.sendEndpointMessage(), .requestProfiles(), .requestPropertyExchangeCapabilities()
    - MidiCIResponder receives and processes each of the replies ...

 The argument `sendOutput` takes a sysex bytestream which is NOT specific to MIDI 1.0 bytestream (i.e. it should
 support sysex7 UMPs) and thus does NOT contain F0 and F7.
 Same goes for `processInput()` function.

*/

class MidiCIInitiator(
    val parent: MidiCIDevice,
    val config: MidiCIInitiatorConfiguration,
    private val sendOutput: (data: List<Byte>) -> Unit
) {
    val muid by parent::muid
    val device by parent::device
    val events by parent::events
    val logger by parent::logger

    enum class SubscriptionActionState {
        Subscribing,
        Subscribed,
        Unsubscribing,
        Unsubscribed
    }
    data class ClientSubscription(var pendingRequestId: Byte?, var subscriptionId: String?, val propertyId: String, var state: SubscriptionActionState)

    class ClientConnection(
        private val parent: MidiCIInitiator,
        val targetMUID: Int,
        val device: DeviceDetails,
        var maxSimultaneousPropertyRequests: Byte = 0,
        var productInstanceId: String = "",
        val propertyClient: MidiCIPropertyClient = CommonRulesPropertyClient(parent.logger, parent.muid) { msg -> parent.sendGetPropertyData(msg) }
    ) {

        val profiles = ObservableProfileList(mutableListOf())

        val properties = ClientObservablePropertyList(parent.logger, propertyClient)

        private val openRequests = mutableListOf<Message.GetPropertyData>()
        val subscriptions = mutableListOf<ClientSubscription>()
        val subscriptionUpdated = mutableListOf<(sub: ClientSubscription)->Unit>()

        val pendingChunkManager = PropertyChunkManager()

        fun updateProperty(msg: Message.GetPropertyDataReply) {
            val req = openRequests.firstOrNull { it.requestId == msg.requestId } ?: return
            openRequests.remove(req)
            val status = propertyClient.getHeaderFieldInteger(msg.header, PropertyCommonHeaderKeys.STATUS) ?: return

            if (status == PropertyExchangeStatus.OK) {
                propertyClient.onGetPropertyDataReply(req, msg)
                val propertyId = propertyClient.getPropertyIdForHeader(req.header)
                properties.updateValue(propertyId, msg)
            }
        }

        fun updateProperty(ourMUID: Int, msg: Message.SubscribeProperty): Pair<String?,Message.SubscribePropertyReply?> {
            val command = properties.updateValue(msg)
            return Pair(
                command,
                Message.SubscribePropertyReply(ourMUID, msg.sourceMUID, msg.requestId,
                    propertyClient.createStatusHeader(PropertyExchangeStatus.OK), listOf()
                )
            )
        }

        fun addPendingRequest(msg: Message.GetPropertyData) {
            openRequests.add(msg)
        }
        fun addPendingSubscription(requestId: Byte, subscriptionId: String?, propertyId: String) {
            val sub = ClientSubscription(requestId, subscriptionId, propertyId, SubscriptionActionState.Subscribing)
            subscriptions.add(sub)
            subscriptionUpdated.forEach { it(sub) }
        }

        fun promoteSubscriptionAsUnsubscribing(propertyId: String, newRequestId: Byte) {
            val sub = subscriptions.firstOrNull { it.propertyId == propertyId }
            if (sub == null) {
                parent.logger.logError("Cannot unsubscribe property as not found: $propertyId")
                return
            }
            if (sub.state == SubscriptionActionState.Unsubscribing) {
                parent.logger.logError("Unsubscription for the property is already underway (property: ${sub.propertyId}, subscriptionId: ${sub.subscriptionId}, state: ${sub.state})")
                return
            }
            sub.pendingRequestId = newRequestId
            sub.state = SubscriptionActionState.Unsubscribing
            subscriptionUpdated.forEach { it(sub) }
        }

        fun processPropertySubscriptionReply(msg: Message.SubscribePropertyReply) {
            val subscriptionId = propertyClient.getHeaderFieldString(msg.header, PropertyCommonHeaderKeys.SUBSCRIBE_ID)
            if (subscriptionId == null) {
                parent.logger.logError("Subscription ID is missing in the Reply to Subscription message. requestId: ${msg.requestId}")
                if (!ImplementationSettings.workaroundJUCEMissingSubscriptionIdIssue)
                    return
            }
            val sub = subscriptions.firstOrNull { subscriptionId == it.subscriptionId }
                ?: subscriptions.firstOrNull { it.pendingRequestId == msg.requestId }
            if (sub == null) {
                parent.logger.logError("There was no pending subscription that matches subscribeId ($subscriptionId) or requestId (${msg.requestId})")
                return
            }

            when (sub.state) {
                SubscriptionActionState.Subscribed,
                SubscriptionActionState.Unsubscribed -> {
                    parent.logger.logError("Received Subscription Reply, but it is unexpected (property: ${sub.propertyId}, subscriptionId: ${sub.subscriptionId}, state: ${sub.state})")
                    return
                }
                else -> {}
            }

            sub.subscriptionId = subscriptionId

            propertyClient.processPropertySubscriptionResult(sub, msg)

            if (sub.state == SubscriptionActionState.Unsubscribing) {
                // do unsubscribe
                sub.state = SubscriptionActionState.Unsubscribed
                subscriptions.remove(sub)
                subscriptionUpdated.forEach { it(sub) }
            } else {
                sub.state = SubscriptionActionState.Subscribed
                subscriptionUpdated.forEach { it(sub) }
            }
        }
    }

    val connections by parent::connections
    val connectionsChanged by parent::connectionsChanged

    // Initiator implementation

    // Discovery

    fun sendDiscovery(ciCategorySupported: Byte = MidiCISupportedCategories.THREE_P) =
        sendDiscovery(Message.DiscoveryInquiry(muid,
            DeviceDetails(device.manufacturerId, device.familyId, device.modelId, device.versionId),
            ciCategorySupported, parent.config.receivableMaxSysExSize, config.outputPathId))

    fun sendDiscovery(msg: Message.DiscoveryInquiry) {
        logger.logMessage(msg, MessageDirection.Out)
        val buf = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        sendOutput(CIFactory.midiCIDiscovery(
            buf, MidiCIConstants.CI_VERSION_AND_FORMAT, msg.sourceMUID, msg.device.manufacturer, msg.device.family, msg.device.modelNumber,
            msg.device.softwareRevisionLevel, msg.ciCategorySupported, msg.receivableMaxSysExSize, msg.outputPathId
        ))
    }

    fun sendEndpointMessage(targetMuid: Int, status: Byte = MidiCIConstants.ENDPOINT_STATUS_PRODUCT_INSTANCE_ID) =
        sendEndpointMessage(Message.EndpointInquiry(muid, targetMuid, status))

    fun sendEndpointMessage(msg: Message.EndpointInquiry) {
        logger.logMessage(msg, MessageDirection.Out)
        val buf = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        sendOutput(CIFactory.midiCIEndpointMessage(buf, MidiCIConstants.CI_VERSION_AND_FORMAT,
            msg.sourceMUID, msg.destinationMUID, msg.status))
    }

    // Profile Configuration

    fun requestProfiles(destinationChannelOr7F: Byte, destinationMUID: Int) =
        requestProfiles(Message.ProfileInquiry(destinationChannelOr7F, muid, destinationMUID))

    fun requestProfiles(msg: Message.ProfileInquiry) {
        logger.logMessage(msg, MessageDirection.Out)
        val buf = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        sendOutput(CIFactory.midiCIProfileInquiry(buf, msg.address, msg.sourceMUID, msg.destinationMUID))
    }

    fun setProfileOn(msg: Message.SetProfileOn) {
        logger.logMessage(msg, MessageDirection.Out)
        val buf = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        sendOutput(CIFactory.midiCIProfileSet(buf, msg.address, true, msg.sourceMUID, msg.destinationMUID, msg.profile, msg.numChannelsRequested))
    }

    fun setProfileOff(msg: Message.SetProfileOff) {
        logger.logMessage(msg, MessageDirection.Out)
        val buf = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        sendOutput(CIFactory.midiCIProfileSet(buf, msg.address, false, msg.sourceMUID, msg.destinationMUID, msg.profile, 0))
    }

    fun requestProfileDetails(address: Byte, muid: Int, profile: MidiCIProfileId, target: Byte) =
        requestProfileDetails(Message.ProfileDetailsInquiry(address, this.muid, muid, profile, target))

    fun requestProfileDetails(msg: Message.ProfileDetailsInquiry) {
        logger.logMessage(msg, MessageDirection.Out)
        val buf = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        sendOutput(CIFactory.midiCIProfileDetails(buf, msg.address, msg.sourceMUID, msg.destinationMUID, msg.profile, 0))
    }

    // Property Exchange

    fun requestPropertyExchangeCapabilities(address: Byte, destinationMUID: Int, maxSimultaneousPropertyRequests: Byte) =

        requestPropertyExchangeCapabilities(Message.PropertyGetCapabilities(address, muid, destinationMUID, maxSimultaneousPropertyRequests))

    fun requestPropertyExchangeCapabilities(msg: Message.PropertyGetCapabilities) {
        logger.logMessage(msg, MessageDirection.Out)
        val buf = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        sendOutput(CIFactory.midiCIPropertyGetCapabilities(
            buf,
            msg.address,
            false,
            msg.sourceMUID,
            msg.destinationMUID,
            msg.maxSimultaneousRequests
        ))
    }

    fun sendGetPropertyData(destinationMUID: Int, resource: String, encoding: String?) {
        val conn = connections[destinationMUID]
        if (conn != null) {
            val header = conn.propertyClient.createDataRequestHeader(resource, mapOf(
                PropertyCommonHeaderKeys.MUTUAL_ENCODING to encoding,
                PropertyCommonHeaderKeys.SET_PARTIAL to false))
            val msg = Message.GetPropertyData(muid, destinationMUID, requestIdSerial++, header)
            sendGetPropertyData(msg)
        }
    }

    fun sendGetPropertyData(msg: Message.GetPropertyData) {
        logger.logMessage(msg, MessageDirection.Out)
        val conn = connections[msg.destinationMUID]
        conn?.addPendingRequest(msg)
        val buf = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        CIFactory.midiCIPropertyChunks(buf, parent.config.maxPropertyChunkSize, CISubId2.PROPERTY_GET_DATA_INQUIRY,
            msg.sourceMUID, msg.destinationMUID, msg.requestId, msg.header, listOf()).forEach {
            sendOutput(it)
        }
    }

    fun sendSetPropertyData(destinationMUID: Int, resource: String, data: List<Byte>, encoding: String? = null, isPartial: Boolean = false) {
        val conn = connections[destinationMUID]
        if (conn != null) {
            val header = conn.propertyClient.createDataRequestHeader(resource, mapOf(
                PropertyCommonHeaderKeys.MUTUAL_ENCODING to encoding,
                PropertyCommonHeaderKeys.SET_PARTIAL to isPartial))
            val encodedBody = conn.propertyClient.encodeBody(data, encoding)
            sendSetPropertyData(Message.SetPropertyData(muid, destinationMUID, requestIdSerial++, header, encodedBody))
        }
    }

    fun sendSetPropertyData(msg: Message.SetPropertyData) {
        logger.logMessage(msg, MessageDirection.Out)
        val buf = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        CIFactory.midiCIPropertyChunks(buf, parent.config.maxPropertyChunkSize, CISubId2.PROPERTY_SET_DATA_INQUIRY,
            msg.sourceMUID, msg.destinationMUID, msg.requestId, msg.header, msg.body).forEach {
            sendOutput(it)
        }
    }

    fun sendSubscribeProperty(destinationMUID: Int, resource: String, mutualEncoding: String?, subscriptionId: String? = null) {
        val conn = connections[destinationMUID]
        if (conn != null) {
            val header = conn.propertyClient.createSubscriptionHeader(resource, mapOf(
                PropertyCommonHeaderKeys.COMMAND to MidiCISubscriptionCommand.START,
                PropertyCommonHeaderKeys.MUTUAL_ENCODING to mutualEncoding))
            val msg = Message.SubscribeProperty(muid, destinationMUID, requestIdSerial++, header, listOf())
            conn.addPendingSubscription(msg.requestId, subscriptionId, resource)
            sendSubscribeProperty(msg)
        }
    }

    fun sendUnsubscribeProperty(destinationMUID: Int, resource: String, mutualEncoding: String?, subscriptionId: String? = null) {
        val conn = connections[destinationMUID]
        if (conn != null) {
            val newRequestId = requestIdSerial++
            val header = conn.propertyClient.createSubscriptionHeader(resource, mapOf(
                PropertyCommonHeaderKeys.COMMAND to MidiCISubscriptionCommand.END,
                PropertyCommonHeaderKeys.MUTUAL_ENCODING to mutualEncoding))
            val msg = Message.SubscribeProperty(muid, destinationMUID, newRequestId, header, listOf())
            conn.promoteSubscriptionAsUnsubscribing(resource, newRequestId)
            sendSubscribeProperty(msg)
        }
    }

    fun sendSubscribeProperty(msg: Message.SubscribeProperty) {
        logger.logMessage(msg, MessageDirection.Out)
        val dst = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        CIFactory.midiCIPropertyChunks(
            dst, parent.config.maxPropertyChunkSize, CISubId2.PROPERTY_SUBSCRIBE,
            msg.sourceMUID, msg.destinationMUID, msg.requestId, msg.header, msg.body
        ).forEach {
            sendOutput(it)
        }
    }

    // Process Inquiry
    fun sendProcessInquiry(destinationMUID: Int) =
        sendProcessInquiry(Message.ProcessInquiry(muid, destinationMUID))

    fun sendProcessInquiry(msg: Message.ProcessInquiry) {
        logger.logMessage(msg, MessageDirection.Out)
        val buf = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        sendOutput(CIFactory.midiCIProcessInquiryCapabilities(buf, msg.sourceMUID, msg.destinationMUID))
    }

    fun sendMidiMessageReportInquiry(address: Byte, destinationMUID: Int,
                                     messageDataControl: Byte,
                                     systemMessages: Byte,
                                     channelControllerMessages: Byte,
                                     noteDataMessages: Byte) =
        sendMidiMessageReportInquiry(Message.MidiMessageReportInquiry(
            address, muid, destinationMUID, messageDataControl, systemMessages, channelControllerMessages, noteDataMessages))

    fun sendMidiMessageReportInquiry(msg: Message.MidiMessageReportInquiry) {
        logger.logMessage(msg, MessageDirection.Out)
        val buf = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        sendOutput(CIFactory.midiCIMidiMessageReport(buf, msg.address, msg.sourceMUID, msg.destinationMUID,
            msg.messageDataControl, msg.systemMessages, msg.channelControllerMessages, msg.noteDataMessages))
    }

    // Miscellaneous

    private var requestIdSerial: Byte = 1

    // Reply handler

    val handleNewEndpoint = { msg: Message.DiscoveryReply ->
        // If successfully discovered, continue to endpoint inquiry
        val connection = ClientConnection(this, msg.sourceMUID, msg.device)
        val existing = connections[msg.sourceMUID]
        if (existing != null) {
            connections.remove(msg.sourceMUID)
            connectionsChanged.forEach { it(ConnectionChange.Removed, existing) }
        }
        connections[msg.sourceMUID]= connection
        connectionsChanged.forEach { it(ConnectionChange.Added, connection) }

        if (config.autoSendEndpointInquiry)
            sendEndpointMessage(msg.sourceMUID, MidiCIConstants.ENDPOINT_STATUS_PRODUCT_INSTANCE_ID)

        if (config.autoSendProfileInquiry && (msg.ciCategorySupported.toInt() and MidiCISupportedCategories.PROFILE_CONFIGURATION.toInt()) != 0)
            requestProfiles(0x7F, msg.sourceMUID)
        if (config.autoSendPropertyExchangeCapabilitiesInquiry && (msg.ciCategorySupported.toInt() and MidiCISupportedCategories.PROPERTY_EXCHANGE.toInt()) != 0)
            requestPropertyExchangeCapabilities(0x7F, msg.sourceMUID, parent.config.maxSimultaneousPropertyRequests)
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
                    parent.invalidateMUID(msg.sourceMUID, "Invalid product instance ID")
            }
        }
    }
    var processEndpointReply: (msg: Message.EndpointReply) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.endpointReplyReceived.forEach { it(msg) }
        defaultProcessEndpointReply(msg)
    }

    // Protocol Negotiation is deprecated. We do not send any of them anymore.

    // Profile Configuration
    val defaultProcessProfileReply = { msg: Message.ProfileReply ->
        val conn = connections[msg.sourceMUID]
        msg.enabledProfiles.forEach { conn?.profiles?.add(MidiCIProfile(it, msg.address, true)) }
        msg.disabledProfiles.forEach { conn?.profiles?.add(MidiCIProfile(it, msg.address, false)) }
    }
    var processProfileReply = { msg: Message.ProfileReply ->
        logger.logMessage(msg, MessageDirection.In)
        events.profileInquiryReplyReceived.forEach { it(msg) }
        defaultProcessProfileReply(msg)
    }

    val defaultProcessProfileAddedReport: (msg: Message.ProfileAdded) -> Unit = { msg ->
        val conn = connections[msg.sourceMUID]
        conn?.profiles?.add(MidiCIProfile(msg.profile, msg.address, false))
    }
    var processProfileAddedReport = { msg: Message.ProfileAdded ->
        logger.logMessage(msg, MessageDirection.In)
        events.profileAddedReceived.forEach { it(msg) }
        defaultProcessProfileAddedReport(msg)
    }

    val defaultProcessProfileRemovedReport: (msg: Message.ProfileRemoved) -> Unit = { msg ->
        val conn = connections[msg.sourceMUID]
        conn?.profiles?.remove(MidiCIProfile(msg.profile, msg.address, false))
    }
    var processProfileRemovedReport: (msg: Message.ProfileRemoved) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.profileRemovedReceived.forEach { it(msg) }
        defaultProcessProfileRemovedReport(msg)
    }

    fun defaultProcessProfileEnabledReport(msg: Message.ProfileEnabled) {
        val conn = connections[msg.sourceMUID]
        conn?.profiles?.setEnabled(true, msg.address, msg.profile, msg.numChannelsRequested)
    }
    fun defaultProcessProfileDisabledReport(msg: Message.ProfileDisabled) {
        val conn = connections[msg.sourceMUID]
        conn?.profiles?.setEnabled(false, msg.address, msg.profile, msg.numChannelsRequested)
    }
    var processProfileEnabledReport: (msg: Message.ProfileEnabled) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.profileEnabledReceived.forEach { it(msg) }
        defaultProcessProfileEnabledReport(msg)
    }
    var processProfileDisabledReport: (msg: Message.ProfileDisabled) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.profileDisabledReceived.forEach { it(msg) }
        defaultProcessProfileDisabledReport(msg)
    }

    var processProfileDetailsReply: (msg: Message.ProfileDetailsReply) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.profileDetailsReplyReceived.forEach { it(msg) }
        // nothing to perform - use events if you need anything further
    }

    // Property Exchange
    @OptIn(DelicateCoroutinesApi::class)
    val defaultProcessPropertyCapabilitiesReply: (msg: Message.PropertyGetCapabilitiesReply) -> Unit = { msg ->
        val conn = connections[msg.sourceMUID]
        if (conn != null) {
            conn.maxSimultaneousPropertyRequests = msg.maxSimultaneousRequests

            // proceed to query resource list
            if (config.autoSendGetResourceList)
                GlobalScope.launch {
                    conn.propertyClient.requestPropertyList(msg.sourceMUID, requestIdSerial++)
                }
        }
        else
            parent.sendNakForUnknownMUID(CISubId2.PROPERTY_CAPABILITIES_REPLY, msg.address, msg.sourceMUID)
    }
    var processPropertyCapabilitiesReply: (msg: Message.PropertyGetCapabilitiesReply) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.propertyCapabilityReplyReceived.forEach { it(msg) }
        defaultProcessPropertyCapabilitiesReply(msg)
    }

    val defaultProcessGetDataReply: (msg: Message.GetPropertyDataReply) -> Unit = { msg ->
        connections[msg.sourceMUID]?.updateProperty(msg)
    }

    var processGetDataReply: (msg: Message.GetPropertyDataReply) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.getPropertyDataReplyReceived.forEach { it(msg) }
        defaultProcessGetDataReply(msg)
    }

    var processSetDataReply: (msg: Message.SetPropertyDataReply) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.setPropertyDataReplyReceived.forEach { it(msg) }
        // nothing to delegate further
    }

    fun sendPropertySubscribeReply(msg: Message.SubscribePropertyReply) {
        logger.logMessage(msg, MessageDirection.Out)
        val dst = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        CIFactory.midiCIPropertyChunks(
            dst, parent.config.maxPropertyChunkSize, CISubId2.PROPERTY_SUBSCRIBE_REPLY,
            msg.sourceMUID, msg.destinationMUID, msg.requestId, msg.header, msg.body
        ).forEach {
            sendOutput(it)
        }
    }
    var processSubscribeProperty: (msg: Message.SubscribeProperty) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.subscribePropertyReceived.forEach { it(msg) }
        val conn = connections[msg.sourceMUID]
        if (conn != null) {
            val reply = conn.updateProperty(muid, msg)
            if (reply.second != null)
                sendPropertySubscribeReply(reply.second!!)
            // If the update was NOTIFY, then it is supposed to send Get Data request.
            if (reply.first == MidiCISubscriptionCommand.NOTIFY)
                sendGetPropertyData(msg.sourceMUID, conn.propertyClient.getPropertyIdForHeader(msg.header), null) // is there mutualEncoding from SubscribeProperty?
        }
        else
            // Unknown MUID - send back NAK
            parent.sendNakForUnknownMUID(CISubId2.PROPERTY_SUBSCRIBE, msg.address, msg.sourceMUID)
    }

    fun defaultProcessSubscribePropertyReply(msg: Message.SubscribePropertyReply) {
        val conn = connections[msg.sourceMUID]
        if (conn != null) {
            if (conn.propertyClient.getHeaderFieldInteger(msg.header, PropertyCommonHeaderKeys.STATUS) == PropertyExchangeStatus.OK)
                conn.processPropertySubscriptionReply(msg)
        }
    }
    var processSubscribePropertyReply: (msg: Message.SubscribePropertyReply) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.subscribePropertyReplyReceived.forEach { it(msg) }
        defaultProcessSubscribePropertyReply(msg)
    }

    // Process Inquiry
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
}
