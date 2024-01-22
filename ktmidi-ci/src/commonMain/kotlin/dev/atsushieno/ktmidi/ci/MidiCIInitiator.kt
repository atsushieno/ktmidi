package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.propertycommonrules.CommonRulesPropertyClient
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyCommonHeaderKeys
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyExchangeStatus
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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
    val config: MidiCIDeviceConfiguration,
    private val sendOutput: (group: Byte, data: List<Byte>) -> Unit
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
        val propertyClient: MidiCIPropertyClient = CommonRulesPropertyClient(parent.logger, parent.muid)
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
                Message.SubscribePropertyReply(Message.Common(ourMUID, msg.sourceMUID, msg.address, msg.group),
                    msg.requestId,
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

    fun sendEndpointMessage(group: Byte, targetMuid: Int, status: Byte = MidiCIConstants.ENDPOINT_STATUS_PRODUCT_INSTANCE_ID) =
        sendEndpointMessage(Message.EndpointInquiry(Message.Common(muid, targetMuid, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group), status))

    fun sendEndpointMessage(msg: Message.EndpointInquiry) {
        logger.logMessage(msg, MessageDirection.Out)
        sendOutput(msg.group, msg.serialize(config))
    }

    // Profile Configuration

    fun requestProfiles(group: Byte, destinationChannelOr7F: Byte, destinationMUID: Int) =
        requestProfiles(Message.ProfileInquiry(Message.Common(muid, destinationMUID, destinationChannelOr7F, group)))

    fun requestProfiles(msg: Message.ProfileInquiry) {
        logger.logMessage(msg, MessageDirection.Out)
        sendOutput(msg.group, msg.serialize(config))
    }

    fun setProfileOn(msg: Message.SetProfileOn) {
        logger.logMessage(msg, MessageDirection.Out)
        sendOutput(msg.group, msg.serialize(config))
    }

    fun setProfileOff(msg: Message.SetProfileOff) {
        logger.logMessage(msg, MessageDirection.Out)
        sendOutput(msg.group, msg.serialize(config))
    }

    fun requestProfileDetails(group: Byte, address: Byte, targetMUID: Int, profile: MidiCIProfileId, target: Byte) =
        requestProfileDetails(Message.ProfileDetailsInquiry(Message.Common(muid, targetMUID, address, group),
            profile, target))

    fun requestProfileDetails(msg: Message.ProfileDetailsInquiry) {
        logger.logMessage(msg, MessageDirection.Out)
        sendOutput(msg.group, msg.serialize(config))
    }

    // Property Exchange

    fun requestPropertyExchangeCapabilities(group: Byte, address: Byte, destinationMUID: Int, maxSimultaneousPropertyRequests: Byte) =

        requestPropertyExchangeCapabilities(Message.PropertyGetCapabilities(Message.Common(muid, destinationMUID, address, group),
            maxSimultaneousPropertyRequests))

    fun requestPropertyExchangeCapabilities(msg: Message.PropertyGetCapabilities) {
        logger.logMessage(msg, MessageDirection.Out)
        sendOutput(msg.group, msg.serialize(config))
    }

    fun sendGetPropertyData(group: Byte, destinationMUID: Int, resource: String, encoding: String?, paginateOffset: Int?, paginateLimit: Int?) {
        val conn = connections[destinationMUID]
        if (conn != null) {
            val header = conn.propertyClient.createDataRequestHeader(resource, mapOf(
                PropertyCommonHeaderKeys.MUTUAL_ENCODING to encoding,
                PropertyCommonHeaderKeys.SET_PARTIAL to false,
                PropertyCommonHeaderKeys.OFFSET to paginateOffset,
                PropertyCommonHeaderKeys.LIMIT to paginateLimit
            ).filter { it.value != null })
            val msg = Message.GetPropertyData(Message.Common(muid, destinationMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group),
                requestIdSerial++, header)
            sendGetPropertyData(msg)
        }
    }

    fun sendGetPropertyData(msg: Message.GetPropertyData) {
        logger.logMessage(msg, MessageDirection.Out)
        val conn = connections[msg.destinationMUID]
        conn?.addPendingRequest(msg)
        msg.serialize(config).forEach { sendOutput(msg.group, it) }
    }

    fun sendSetPropertyData(group: Byte, destinationMUID: Int, resource: String, data: List<Byte>, encoding: String? = null, isPartial: Boolean = false) {
        val conn = connections[destinationMUID]
        if (conn != null) {
            val header = conn.propertyClient.createDataRequestHeader(resource, mapOf(
                PropertyCommonHeaderKeys.MUTUAL_ENCODING to encoding,
                PropertyCommonHeaderKeys.SET_PARTIAL to isPartial))
            val encodedBody = conn.propertyClient.encodeBody(data, encoding)
            sendSetPropertyData(Message.SetPropertyData(Message.Common(muid, destinationMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group),
                requestIdSerial++, header, encodedBody))
        }
    }

    fun sendSetPropertyData(msg: Message.SetPropertyData) {
        logger.logMessage(msg, MessageDirection.Out)
        msg.serialize(config).forEach { sendOutput(msg.group, it) }
    }

    fun sendSubscribeProperty(group: Byte, destinationMUID: Int, resource: String, mutualEncoding: String?, subscriptionId: String? = null) {
        val conn = connections[destinationMUID]
        if (conn != null) {
            val header = conn.propertyClient.createSubscriptionHeader(resource, mapOf(
                PropertyCommonHeaderKeys.COMMAND to MidiCISubscriptionCommand.START,
                PropertyCommonHeaderKeys.MUTUAL_ENCODING to mutualEncoding))
            val msg = Message.SubscribeProperty(Message.Common(muid, destinationMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group),
                requestIdSerial++, header, listOf())
            conn.addPendingSubscription(msg.requestId, subscriptionId, resource)
            sendSubscribeProperty(msg)
        }
    }

    fun sendUnsubscribeProperty(group: Byte, destinationMUID: Int, resource: String, mutualEncoding: String?, subscriptionId: String? = null) {
        val conn = connections[destinationMUID]
        if (conn != null) {
            val newRequestId = requestIdSerial++
            val header = conn.propertyClient.createSubscriptionHeader(resource, mapOf(
                PropertyCommonHeaderKeys.COMMAND to MidiCISubscriptionCommand.END,
                PropertyCommonHeaderKeys.MUTUAL_ENCODING to mutualEncoding))
            val msg = Message.SubscribeProperty(Message.Common(muid, destinationMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group),
                newRequestId, header, listOf())
            conn.promoteSubscriptionAsUnsubscribing(resource, newRequestId)
            sendSubscribeProperty(msg)
        }
    }

    fun sendSubscribeProperty(msg: Message.SubscribeProperty) {
        logger.logMessage(msg, MessageDirection.Out)
        msg.serialize(config).forEach { sendOutput(msg.group, it) }
    }

    // Process Inquiry
    fun sendProcessInquiry(group: Byte, destinationMUID: Int) =
        sendProcessInquiry(Message.ProcessInquiry(Message.Common(muid, destinationMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group)))

    fun sendProcessInquiry(msg: Message.ProcessInquiry) {
        logger.logMessage(msg, MessageDirection.Out)
        sendOutput(msg.group, msg.serialize(config))
    }

    fun sendMidiMessageReportInquiry(group: Byte, address: Byte, destinationMUID: Int,
                                     messageDataControl: Byte,
                                     systemMessages: Byte,
                                     channelControllerMessages: Byte,
                                     noteDataMessages: Byte) =
        sendMidiMessageReportInquiry(Message.MidiMessageReportInquiry(
            Message.Common(muid, destinationMUID, address, group),
            messageDataControl, systemMessages, channelControllerMessages, noteDataMessages))

    fun sendMidiMessageReportInquiry(msg: Message.MidiMessageReportInquiry) {
        logger.logMessage(msg, MessageDirection.Out)
        sendOutput(msg.group, msg.serialize(config))
    }

    // Miscellaneous

    private var requestIdSerial: Byte = 1

    // Reply handler

    // Protocol Negotiation is deprecated. We do not send any of them anymore.

    // Profile Configuration
    fun defaultProcessProfileReply(msg: Message.ProfileReply) {
        val conn = connections[msg.sourceMUID]
        msg.enabledProfiles.forEach { conn?.profiles?.add(MidiCIProfile(it, msg.group, msg.address, true, if (msg.address >= 0x7E) 0 else 1)) }
        msg.disabledProfiles.forEach { conn?.profiles?.add(MidiCIProfile(it, msg.group, msg.address, false, if (msg.address >= 0x7E) 0 else 1)) }
    }
    var processProfileReply = { msg: Message.ProfileReply ->
        logger.logMessage(msg, MessageDirection.In)
        events.profileInquiryReplyReceived.forEach { it(msg) }
        defaultProcessProfileReply(msg)
    }

    fun defaultProcessProfileAddedReport(msg: Message.ProfileAdded) {
        val conn = connections[msg.sourceMUID]
        conn?.profiles?.add(MidiCIProfile(msg.profile, msg.group, msg.address, false, if (msg.address >= 0x7E) 0 else 1))
    }
    var processProfileAddedReport = { msg: Message.ProfileAdded ->
        logger.logMessage(msg, MessageDirection.In)
        events.profileAddedReceived.forEach { it(msg) }
        defaultProcessProfileAddedReport(msg)
    }

    fun defaultProcessProfileRemovedReport(msg: Message.ProfileRemoved) {
        val conn = connections[msg.sourceMUID]
        conn?.profiles?.remove(MidiCIProfile(msg.profile, msg.group, msg.address, false, 0))
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
    fun defaultProcessPropertyCapabilitiesReply(msg: Message.PropertyGetCapabilitiesReply) {
        val conn = connections[msg.sourceMUID]
        if (conn != null) {
            conn.maxSimultaneousPropertyRequests = msg.maxSimultaneousRequests

            // proceed to query resource list
            if (config.autoSendGetResourceList)
                sendGetPropertyData(conn.propertyClient.getPropertyListRequest(msg.group, msg.sourceMUID, requestIdSerial++))
        }
        else
            parent.sendNakForUnknownMUID(Message.Common(muid, msg.sourceMUID, msg.group, msg.address),
                CISubId2.PROPERTY_CAPABILITIES_REPLY)
    }
    var processPropertyCapabilitiesReply: (msg: Message.PropertyGetCapabilitiesReply) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.propertyCapabilityReplyReceived.forEach { it(msg) }
        defaultProcessPropertyCapabilitiesReply(msg)
    }

    fun defaultProcessGetDataReply(msg: Message.GetPropertyDataReply) {
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
        msg.serialize(config).forEach { sendOutput(msg.group, it) }
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
                sendGetPropertyData(msg.group, msg.sourceMUID, conn.propertyClient.getPropertyIdForHeader(msg.header),
                    // is there mutualEncoding from SubscribeProperty?
                    encoding = null,
                    paginateOffset = null, paginateLimit = null)
        }
        else
            // Unknown MUID - send back NAK
            parent.sendNakForUnknownMUID(Message.Common(muid, msg.sourceMUID, msg.address, msg.group),
                CISubId2.PROPERTY_SUBSCRIBE)
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
