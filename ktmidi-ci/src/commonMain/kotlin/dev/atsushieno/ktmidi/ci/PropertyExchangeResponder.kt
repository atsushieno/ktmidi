package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.propertycommonrules.CommonRulesPropertyService
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyCommonHeaderKeys
import dev.atsushieno.ktmidi.ci.propertycommonrules.SubscriptionEntry

class PropertyExchangeResponder(
    val parent: MidiCIDevice,
    private val config: MidiCIDeviceConfiguration
) {
    val muid by parent::muid
    val device by parent::device
    private val events by parent::events
    val logger by parent::logger
    private var requestIdSerial by parent::requestIdSerial

    val propertyService by lazy { CommonRulesPropertyService(logger, muid, device, config.propertyValues, config.propertyMetadataList) }
    val properties by lazy { ServiceObservablePropertyList(config.propertyValues, propertyService) }
    val subscriptions: List<SubscriptionEntry> by propertyService::subscriptions

    // update property value. It involves updates to subscribers
    fun updatePropertyValue(group: Byte, propertyId: String, data: List<Byte>, isPartial: Boolean) {
        properties.values.first { it.id == propertyId }.body = data
        notifyPropertyUpdatesToSubscribers(group, propertyId, data, isPartial)
    }

    var notifyPropertyUpdatesToSubscribers: (group: Byte, propertyId: String, data: List<Byte>, isPartial: Boolean) -> Unit = { group, propertyId, data, isPartial ->
        createPropertyNotification(group, propertyId, data, isPartial).forEach { msg ->
            notifyPropertyUpdatesToSubscribers(msg)
        }
    }
    private fun createPropertyNotification(group: Byte, propertyId: String, data: List<Byte>, isPartial: Boolean): Sequence<Message.SubscribeProperty> = sequence {
        var lastEncoding: String? = null
        var lastEncodedData = data
        subscriptions.filter { it.resource == propertyId }.forEach {
            val encodedData = if (it.encoding == lastEncoding) lastEncodedData else if (it.encoding == null) data else propertyService.encodeBody(data, it.encoding)
            // do not invoke encodeBody() many times.
            if (it.encoding != lastEncoding && it.encoding != null) {
                lastEncoding = it.encoding
                lastEncodedData = encodedData
            }
            val header = propertyService.createUpdateNotificationHeader(it.resource, mapOf(
                PropertyCommonHeaderKeys.SUBSCRIBE_ID to it.subscribeId,
                PropertyCommonHeaderKeys.SET_PARTIAL to isPartial,
                PropertyCommonHeaderKeys.MUTUAL_ENCODING to it.encoding))
            yield(Message.SubscribeProperty(Message.Common(muid, MidiCIConstants.BROADCAST_MUID_32, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group),
                requestIdSerial++, header, encodedData))
        }
    }

    fun notifyPropertyUpdatesToSubscribers(msg: Message.SubscribeProperty) = parent.send(msg)

    // Notify end of subscription updates
    fun terminateSubscriptions(group: Byte) {
        propertyService.subscriptions.forEach {
            val msg = Message.SubscribeProperty(Message.Common(muid, it.muid, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group),
                requestIdSerial++,
                propertyService.createTerminateNotificationHeader(it.subscribeId), listOf()
            )
            parent.send(msg)
        }
    }

    // Message handlers

    // Should this also delegate to property service...?
    val getPropertyCapabilitiesReplyFor: (msg: Message.PropertyGetCapabilities) -> Message.PropertyGetCapabilitiesReply = { msg ->
        val establishedMaxSimultaneousPropertyRequests =
            if (msg.maxSimultaneousRequests > parent.config.maxSimultaneousPropertyRequests) parent.config.maxSimultaneousPropertyRequests
            else msg.maxSimultaneousRequests
        Message.PropertyGetCapabilitiesReply(Message.Common(muid, msg.sourceMUID, msg.address, msg.group),
            establishedMaxSimultaneousPropertyRequests)
    }
    var processPropertyCapabilitiesInquiry: (msg: Message.PropertyGetCapabilities) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.propertyCapabilityInquiryReceived.forEach { it(msg) }
        val reply = getPropertyCapabilitiesReplyFor(msg)
        parent.send(reply)
    }

    var processGetPropertyData: (msg: Message.GetPropertyData) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.getPropertyDataReceived.forEach { it(msg) }
        val reply = propertyService.getPropertyData(msg)
        if (reply.isSuccess) {
            parent.send(reply.getOrNull()!!)
        }
        else
            logger.logError(reply.exceptionOrNull()?.message ?: "Incoming GetPropertyData message resulted in an error")
    }

    var processSetPropertyData: (msg: Message.SetPropertyData) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.setPropertyDataReceived.forEach { it(msg) }
        val reply = propertyService.setPropertyData(msg)
        if (reply.isSuccess)
            parent.send(reply.getOrNull()!!)
        else
            logger.logError(reply.exceptionOrNull()?.message ?: "Incoming SetPropertyData message resulted in an error")
    }

    var processSubscribeProperty: (msg: Message.SubscribeProperty) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.subscribePropertyReceived.forEach { it(msg) }
        val reply = propertyService.subscribeProperty(msg)
        if (reply.isSuccess)
            parent.send(reply.getOrNull()!!)
        else
            logger.logError(reply.exceptionOrNull()?.message ?: "Incoming SubscribeProperty message resulted in an error")
    }

    // It receives reply to property notifications
    var processSubscribePropertyReply: (msg: Message.SubscribePropertyReply) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.subscribePropertyReplyReceived.forEach { it(msg) }
    }
}