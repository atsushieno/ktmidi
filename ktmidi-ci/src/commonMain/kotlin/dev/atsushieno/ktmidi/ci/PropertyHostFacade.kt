package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.propertycommonrules.CommonRulesPropertyService
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyCommonHeaderKeys

/**
 * This class provides Profile Configuration *hosting* features primarily to end-user app developers,
 * You can add or remove properties, update their metadata or value.
 *
 * It is NOT for manipulating remote MIDI-CI device properties.
 * `ClientConnection` offers those features instead.
 *
 * Request handlers also invoke these members.
 */

class PropertyHostFacade(private val device: MidiCIDevice) {
    private val messenger by device::messenger

    val metadataList
        get() = propertyService.getMetadataList()

    fun addProperty(property: PropertyMetadata) = properties.addMetadata(property)
    fun removeProperty(propertyId: String) = properties.removeMetadata(propertyId)
    fun updatePropertyMetadata(oldPropertyId: String, property: PropertyMetadata) =
        properties.updateMetadata(oldPropertyId, property)

    // update property value. It involves notifications to subscribers.
    fun setPropertyValue(propertyId: String, resId: String?, data: List<Byte>, isPartial: Boolean) {
        properties.values.first { it.id == propertyId && (resId.isNullOrBlank() || it.resId == resId) }.body = data
        notifyPropertyUpdatesToSubscribers(propertyId, resId, data, isPartial)
    }

    fun updateCommonRulesDeviceInfo(deviceInfo: MidiCIDeviceInfo) {
        val p = propertyService
        if (p is CommonRulesPropertyService)
            p.deviceInfo = deviceInfo
    }

    fun updateCommonRulesChannelList(channelList: MidiCIChannelList) {
        val p = propertyService
        if (p is CommonRulesPropertyService)
            p.channelList = channelList
    }

    fun updateJsonSchema(stringValue: String) {
        val p = propertyService
        if (p is CommonRulesPropertyService)
            p.jsonSchemaString = stringValue
    }

    var propertyBinaryGetter: (propertyId: String, resId: String?) -> List<Byte>?
        get() = (propertyService as? CommonRulesPropertyService)?.propertyBinaryGetter ?: { _, _ -> null }
        set(value) { (propertyService as? CommonRulesPropertyService)?.propertyBinaryGetter = value }

    // These members were moved from `PropertyExchangeResponder` and might be still unsorted.

    private val muid by device::muid
    private val logger by device::logger
    private val config by device::config

    internal val propertyService: MidiCIServicePropertyRules by lazy { CommonRulesPropertyService(device) }
    val properties by lazy { ServiceObservablePropertyList(config.propertyValues, propertyService) }
    val subscriptions by lazy { ObservablePropertySubscriptionList(propertyService) }

    var notifyPropertyUpdatesToSubscribers: (propertyId: String, resId: String?, data: List<Byte>, isPartial: Boolean) -> Unit = { propertyId, resId, data, isPartial ->
        createPropertyNotification(propertyId, resId, data, isPartial).forEach { msg ->
            notifyPropertyUpdatesToSubscribers(msg)
        }
    }
    private fun createPropertyNotification(propertyId: String, resId: String?, data: List<Byte>, isPartial: Boolean): Sequence<Message.SubscribeProperty> = sequence {
        var lastEncoding: String? = null
        var lastEncodedData = data
        subscriptions.items.filter { it.resource == propertyId && (resId.isNullOrBlank() || it.resId == resId) }.forEach {
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
            yield(Message.SubscribeProperty(Message.Common(muid, MidiCIConstants.BROADCAST_MUID_32, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, config.group),
                messenger.requestIdSerial++, header, encodedData))
        }
    }

    private fun notifyPropertyUpdatesToSubscribers(msg: Message.SubscribeProperty) = messenger.send(msg)

    fun shutdownSubscription(destinationMUID: Int, propertyId: String, resId: String?) {
        messenger.send(createShutdownSubscriptionMessage(
            destinationMUID, propertyId, resId, device.config.group, device.messenger.requestIdSerial++))
    }

    // should be invoked when the host is being terminated
    fun terminateSubscriptionsToAllSubsctibers(group: Byte) {
        propertyService.subscriptions.forEach {
            messenger.send(createShutdownSubscriptionMessage(
                it.muid, it.resource, it.resId, group, device.messenger.requestIdSerial++))
        }
    }

    private fun createShutdownSubscriptionMessage(
        destinationMUID: Int,
        propertyId: String,
        resId: String?,
        group: Byte,
        requestId: Byte
    ): Message.SubscribeProperty {
        val msg = Message.SubscribeProperty(Message.Common(muid, destinationMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group),
            requestId,
            propertyService.createShutdownSubscriptionHeader(propertyId, resId), listOf()
        )
        return msg
    }

    // Message handlers

    fun processGetPropertyData(msg: Message.GetPropertyData) {
        val reply = propertyService.getPropertyData(msg)
        if (reply.isSuccess) {
            messenger.send(reply.getOrNull()!!)
        }
        else
            logger.logError(reply.exceptionOrNull()?.message ?: "Incoming GetPropertyData message resulted in an error")
    }

    fun processSetPropertyData(msg: Message.SetPropertyData) {
        val reply = propertyService.setPropertyData(msg)
        if (reply.isSuccess) {
            properties.updateValue(msg.header, msg.body)
            messenger.send(reply.getOrNull()!!)
        }
        else
            logger.logError(reply.exceptionOrNull()?.message ?: "Incoming SetPropertyData message resulted in an error")
    }

    fun processSubscribeProperty(msg: Message.SubscribeProperty) {
        val reply = propertyService.subscribeProperty(msg)
        if (reply.isSuccess)
            messenger.send(reply.getOrNull()!!)
        else
            logger.logError(reply.exceptionOrNull()?.message ?: "Incoming SubscribeProperty message resulted in an error")
    }
}