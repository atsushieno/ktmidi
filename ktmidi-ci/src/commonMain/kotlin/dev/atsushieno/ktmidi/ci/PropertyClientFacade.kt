package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.propertycommonrules.CommonRulesPropertyClient
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyCommonHeaderKeys
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyExchangeStatus

class PropertyClientFacade(private val device: MidiCIDevice, private val conn: ClientConnection) {
    private val muid by device::muid
    private val logger by device::logger
    private val messenger by device::messenger
    private val targetMUID by conn::targetMUID

    var propertyRules: MidiCIClientPropertyRules = CommonRulesPropertyClient(device, conn)

    val properties = ClientObservablePropertyList(logger, propertyRules)

    private val openRequests = mutableListOf<Message.PropertyMessage>()
    val subscriptions = mutableListOf<ClientSubscription>()
    val subscriptionUpdated = mutableListOf<(sub: ClientSubscription)->Unit>()

    val pendingChunkManager = PropertyChunkManager()

    private fun updatePropertyBySubscribe(msg: Message.SubscribeProperty): Pair<String?, Message.SubscribePropertyReply?> {
        val command = properties.updateValue(msg)
        return Pair(
            command,
            Message.SubscribePropertyReply(
                Message.Common(muid, msg.sourceMUID, msg.address, msg.group),
                msg.requestId,
                propertyRules.createStatusHeader(PropertyExchangeStatus.OK), listOf()
            )
        )
    }

    private fun handleUnsubscriptionNotification(ourMUID: Int, msg: Message.SubscribeProperty): Pair<String?, Message.SubscribePropertyReply?> {
        val sub = subscriptions.firstOrNull { it.subscriptionId == propertyRules.getHeaderFieldString(msg.header,
            PropertyCommonHeaderKeys.SUBSCRIBE_ID
        ) } ?:
            subscriptions.firstOrNull { it.propertyId == propertyRules.getPropertyIdForHeader(msg.header) }
        if (sub == null)
            return Pair("subscription ID is not specified in the unsubscription request", null)
        subscriptions.remove(sub)
        return Pair(null, Message.SubscribePropertyReply(
            Message.Common(ourMUID, msg.sourceMUID, msg.address, msg.group),
            msg.requestId,
            propertyRules.createStatusHeader(PropertyExchangeStatus.OK), listOf()
        )
        )
    }

    private fun addPendingSubscription(requestId: Byte, subscriptionId: String?, propertyId: String, resId: String?) {
        val sub = ClientSubscription(
            requestId,
            subscriptionId,
            propertyId,
            resId,
            SubscriptionActionState.Subscribing
        )
        subscriptions.add(sub)
        subscriptionUpdated.forEach { it(sub) }
    }

    private fun promoteSubscriptionAsUnsubscribing(propertyId: String, resId: String?, newRequestId: Byte) {
        val sub = subscriptions.firstOrNull { it.propertyId == propertyId && (resId.isNullOrBlank() || it.resId == resId) }
        if (sub == null) {
            logger.logError("Cannot unsubscribe property as not found: $propertyId (resId: $resId)")
            return
        }
        if (sub.state == SubscriptionActionState.Unsubscribing) {
            logger.logError("Unsubscription for the property is already underway (property: ${sub.propertyId}, resId: ${sub.resId}, subscriptionId: ${sub.subscriptionId}, state: ${sub.state})")
            return
        }
        sub.pendingRequestId = newRequestId
        sub.state = SubscriptionActionState.Unsubscribing
        subscriptionUpdated.forEach { it(sub) }
    }

    internal fun processPropertySubscriptionReply(msg: Message.SubscribePropertyReply) {
        if (propertyRules.getHeaderFieldInteger(msg.header,
                PropertyCommonHeaderKeys.STATUS
            ) != PropertyExchangeStatus.OK
        )
            return // FIXME: should we do anything further here?

        val subscriptionId = propertyRules.getHeaderFieldString(msg.header, PropertyCommonHeaderKeys.SUBSCRIBE_ID)
        val sub = subscriptions.firstOrNull { subscriptionId == it.subscriptionId }
            ?: subscriptions.firstOrNull { it.pendingRequestId == msg.requestId }
        if (sub == null) {
            logger.logError("There was no pending subscription that matches subscribeId ($subscriptionId) or requestId (${msg.requestId})")
            return
        }
        // `subscribeId` must be attached everywhere, except for unsubscription reply.
        if (subscriptionId == null && sub.state != SubscriptionActionState.Unsubscribing) {
            logger.logError("Subscription ID is missing in the Reply to Subscription message. requestId: ${msg.requestId}")
            if (!ImplementationSettings.workaroundJUCEMissingSubscriptionIdIssue)
                return
        }

        when (sub.state) {
            SubscriptionActionState.Subscribed,
            SubscriptionActionState.Unsubscribed -> {
                logger.logError("Received Subscription Reply, but it is unexpected (existing subscription: property = ${sub.propertyId}, resId = ${sub.resId}, subscriptionId = ${sub.subscriptionId}, state = ${sub.state})")
                return
            }
            else -> {}
        }

        sub.subscriptionId = subscriptionId

        propertyRules.processPropertySubscriptionResult(sub, msg)

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

    // It is Common Rules specific
    fun sendGetPropertyData(resource: String, resId: String?, encoding: String? = null, paginateOffset: Int? = null, paginateLimit: Int? = null) {
        val header = propertyRules.createDataRequestHeader(resource, mapOf(
            PropertyCommonHeaderKeys.RES_ID to resId,
            PropertyCommonHeaderKeys.MUTUAL_ENCODING to encoding,
            PropertyCommonHeaderKeys.SET_PARTIAL to false,
            PropertyCommonHeaderKeys.OFFSET to paginateOffset,
            PropertyCommonHeaderKeys.LIMIT to paginateLimit
        ).filter { it.value != null })
        val msg = Message.GetPropertyData(
            Message.Common(muid, targetMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, device.config.group),
            messenger.requestIdSerial++, header
        )
        sendGetPropertyData(msg)
    }

    // unlike the other overload, it is not specific to Common Rules for PE
    fun sendGetPropertyData(msg: Message.GetPropertyData) {
        openRequests.add(msg)
        messenger.send(msg)
    }

    // It is Common Rules specific
    fun sendSetPropertyData(resource: String, resId: String?, data: List<Byte>, encoding: String? = null, isPartial: Boolean = false) {
        val header = propertyRules.createDataRequestHeader(resource, mapOf(
            PropertyCommonHeaderKeys.RES_ID to resId,
            PropertyCommonHeaderKeys.MUTUAL_ENCODING to encoding,
            PropertyCommonHeaderKeys.SET_PARTIAL to isPartial))
        val encodedBody = propertyRules.encodeBody(data, encoding)
        val msg =
            Message.SetPropertyData(
                Message.Common(muid, targetMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, device.config.group),
                messenger.requestIdSerial++, header, encodedBody
            )
        sendSetPropertyData(msg)
    }

    // unlike the other overload, it is not specific to Common Rules for PE
    fun sendSetPropertyData(msg: Message.SetPropertyData) {
        // We need to update our local property value, but we should confirm that
        // the operation was successful by verifying Reply To Set Data message status.
        // To ensure that, we store `msg` as a pending request.
        openRequests.add(msg)
        messenger.send(msg)
    }

    fun sendSubscribeProperty(resource: String, resId: String?, mutualEncoding: String? = null, subscriptionId: String? = null) {
        val header = propertyRules.createSubscriptionHeader(resource, mapOf(
            PropertyCommonHeaderKeys.COMMAND to MidiCISubscriptionCommand.START,
            PropertyCommonHeaderKeys.MUTUAL_ENCODING to mutualEncoding))
        val msg = Message.SubscribeProperty(
            Message.Common(muid, targetMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, device.config.group),
            messenger.requestIdSerial++, header, listOf()
        )
        addPendingSubscription(msg.requestId, subscriptionId, resource, resId)
        messenger.send(msg)
    }

    fun sendUnsubscribeProperty(propertyId: String, resId: String?) {
        val newRequestId = messenger.requestIdSerial++
        val header = propertyRules.createSubscriptionHeader(propertyId, mapOf(
            PropertyCommonHeaderKeys.COMMAND to MidiCISubscriptionCommand.END,
            PropertyCommonHeaderKeys.SUBSCRIBE_ID to subscriptions.firstOrNull { it.propertyId == propertyId && (resId.isNullOrBlank() || it.resId == resId )}?.subscriptionId))
        val msg = Message.SubscribeProperty(
            Message.Common(muid, targetMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, device.config.group),
            newRequestId, header, listOf()
        )
        promoteSubscriptionAsUnsubscribing(propertyId, resId, newRequestId)
        messenger.send(msg)
    }

    // client PE event receivers

    internal fun processPropertyCapabilitiesReply(msg: Message.PropertyGetCapabilitiesReply) {
        conn.maxSimultaneousPropertyRequests = msg.maxSimultaneousRequests

        // proceed to query resource list
        if (device.config.autoSendGetResourceList)
            propertyRules.requestPropertyList(msg.group)
    }

    internal fun processGetDataReply(msg: Message.GetPropertyDataReply) {
        val req = openRequests.firstOrNull { it.requestId == msg.requestId }
            ?: return
        openRequests.remove(req)
        val status = propertyRules.getHeaderFieldInteger(msg.header, PropertyCommonHeaderKeys.STATUS)
            ?: return
        if (status == PropertyExchangeStatus.OK) {
            val propertyId = propertyRules.getPropertyIdForHeader(req.header)
            properties.updateValue(propertyId, msg.header, msg.body)
            propertyRules.propertyValueUpdated(propertyId, msg.body)
        }
    }

    internal fun processSetDataReply(msg: Message.SetPropertyDataReply) {
        val req = openRequests.firstOrNull { it.requestId == msg.requestId }
            ?: return
        openRequests.remove(req)
        val status = propertyRules.getHeaderFieldInteger(msg.header, PropertyCommonHeaderKeys.STATUS)
            ?: return
        if (status == PropertyExchangeStatus.OK) {
            val propertyId = propertyRules.getPropertyIdForHeader(req.header)
            properties.updateValue(propertyId, req.header, req.body)
            propertyRules.propertyValueUpdated(propertyId, msg.body)
        }
    }

    internal fun processSubscribeProperty(msg: Message.SubscribeProperty) {
        val reply = when (propertyRules.getHeaderFieldString(msg.header, PropertyCommonHeaderKeys.COMMAND)) {
            MidiCISubscriptionCommand.END -> handleUnsubscriptionNotification(muid, msg)
            else -> updatePropertyBySubscribe(msg)
        }
        if (reply.second != null)
            messenger.send(reply.second!!)
        // If the update was NOTIFY, then it is supposed to send Get Data request.
        if (reply.first == MidiCISubscriptionCommand.NOTIFY)
            sendGetPropertyData(
                propertyRules.getPropertyIdForHeader(msg.header),
                propertyRules.getHeaderFieldString(msg.header, PropertyCommonHeaderKeys.RES_ID))
    }
}