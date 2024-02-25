package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyCommonHeaderKeys
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyExchangeStatus
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyResourceNames

class PropertyExchangeInitiator(
    val parent: MidiCIDevice,
    private val config: MidiCIDeviceConfiguration
) {
    val muid by parent::muid
    val device by parent::device
    private val events by parent::events
    val logger by parent::logger
    private var requestIdSerial by parent::requestIdSerial

    enum class SubscriptionActionState {
        Subscribing,
        Subscribed,
        Unsubscribing,
        Unsubscribed
    }
    data class ClientSubscription(var pendingRequestId: Byte?, var subscriptionId: String?, val propertyId: String, var state: SubscriptionActionState)

    private val connections by parent::connections

    fun requestPropertyExchangeCapabilities(group: Byte, address: Byte, destinationMUID: Int, maxSimultaneousPropertyRequests: Byte) =

        requestPropertyExchangeCapabilities(Message.PropertyGetCapabilities(Message.Common(muid, destinationMUID, address, group),
            maxSimultaneousPropertyRequests))

    fun requestPropertyExchangeCapabilities(msg: Message.PropertyGetCapabilities) {
        logger.logMessage(msg, MessageDirection.Out)
        parent.send(msg)
    }

    // FIXME: too much exposure of Common Rules for PE
    fun saveAndSendGetPropertyData(group: Byte, destinationMUID: Int, resource: String, encoding: String? = null, paginateOffset: Int? = null, paginateLimit: Int? = null) {
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
            saveAndSendGetPropertyData(msg)
        }
    }

    fun saveAndSendGetPropertyData(msg: Message.GetPropertyData) {
        val conn = connections[msg.destinationMUID]
        conn?.addPendingRequest(msg)
        parent.send(msg)
    }

    // FIXME: too much exposure of Common Rules for PE
    fun sendSetPropertyData(group: Byte, destinationMUID: Int, resource: String, data: List<Byte>, encoding: String? = null, isPartial: Boolean = false) {
        val conn = connections[destinationMUID]
        if (conn != null) {
            val header = conn.propertyClient.createDataRequestHeader(resource, mapOf(
                PropertyCommonHeaderKeys.MUTUAL_ENCODING to encoding,
                PropertyCommonHeaderKeys.SET_PARTIAL to isPartial))
            val encodedBody = conn.propertyClient.encodeBody(data, encoding)
            parent.send(Message.SetPropertyData(Message.Common(muid, destinationMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group),
                requestIdSerial++, header, encodedBody))
        }
    }

    // FIXME: too much exposure of Common Rules for PE
    fun sendSubscribeProperty(group: Byte, destinationMUID: Int, resource: String, mutualEncoding: String?, subscriptionId: String? = null) {
        val conn = connections[destinationMUID]
        if (conn != null) {
            val header = conn.propertyClient.createSubscriptionHeader(resource, mapOf(
                PropertyCommonHeaderKeys.COMMAND to MidiCISubscriptionCommand.START,
                PropertyCommonHeaderKeys.MUTUAL_ENCODING to mutualEncoding))
            val msg = Message.SubscribeProperty(Message.Common(muid, destinationMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group),
                requestIdSerial++, header, listOf())
            conn.addPendingSubscription(msg.requestId, subscriptionId, resource)
            parent.send(msg)
        }
    }

    // FIXME: too much exposure of Common Rules for PE
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
            parent.send(msg)
        }
    }

    fun defaultProcessPropertyCapabilitiesReply(msg: Message.PropertyGetCapabilitiesReply) {
        val conn = connections[msg.sourceMUID]
        if (conn != null) {
            conn.maxSimultaneousPropertyRequests = msg.maxSimultaneousRequests

            // proceed to query resource list
            if (config.autoSendGetResourceList)
                saveAndSendGetPropertyData(conn.propertyClient.getPropertyListRequest(msg.group, msg.sourceMUID, requestIdSerial++))
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
        val conn = connections[msg.sourceMUID]
        val propertyId = conn?.updateProperty(msg)

        // If the reply was ResourceList, and the parsed body contained an entry for DeviceInfo, and
        //  if it is configured as auto-queried, then send another Get Property Data request for it.
        if (config.autoSendGetDeviceInfo && propertyId == PropertyResourceNames.RESOURCE_LIST) {
            val def = conn.propertyClient.getMetadataList()?.firstOrNull { it.resource == PropertyResourceNames.DEVICE_INFO }
            if (def != null)
                saveAndSendGetPropertyData(msg.group, msg.sourceMUID, def.resource, def.encodings.firstOrNull())
        }
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

    var processSubscribeProperty: (msg: Message.SubscribeProperty) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.subscribePropertyReceived.forEach { it(msg) }
        val conn = connections[msg.sourceMUID]
        if (conn != null) {
            val reply = conn.updateProperty(muid, msg)
            if (reply.second != null)
                parent.send(reply.second!!)
            // If the update was NOTIFY, then it is supposed to send Get Data request.
            if (reply.first == MidiCISubscriptionCommand.NOTIFY)
                saveAndSendGetPropertyData(msg.group, msg.sourceMUID, conn.propertyClient.getPropertyIdForHeader(msg.header),
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
}
