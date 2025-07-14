package dev.atsushieno.ktmidi.ci.propertycommonrules

import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.ci.json.Json
import dev.atsushieno.ktmidi.ci.json.JsonParserException

class CommonRulesPropertyClient(private val device: MidiCIDevice, private val conn: ClientConnection)
    : MidiCIClientPropertyRules {
    private val helper = CommonRulesPropertyHelper(device)
    private val logger by device::logger

    override fun createDataRequestHeader(propertyId: String, fields: Map<String, Any?>): List<Byte> =
        helper.createRequestHeaderBytes(propertyId, fields)

    override fun createSubscriptionHeader(propertyId: String, fields: Map<String, Any?>): List<Byte> =
        helper.createSubscribeHeaderBytes(propertyId, fields[PropertyCommonHeaderKeys.COMMAND] as String, fields[PropertyCommonHeaderKeys.MUTUAL_ENCODING] as String?)

    override fun getPropertyIdForHeader(header: List<Byte>) = helper.getPropertyIdentifierInternal(header)

    override fun getMetadataList(): List<PropertyMetadata> = resourceList

    override fun requestPropertyList(group: Byte) {
        val requestASCIIBytes = helper.getResourceListRequestBytes()
        val msg = Message.GetPropertyData(
            Message.Common(device.muid, conn.targetMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group),
            device.messenger.requestIdSerial++,
            requestASCIIBytes
        )
        // it needs to be sent via this method otherwise we will fail to record it as a "pending request"
        conn.propertyClient.sendGetPropertyData(msg)
    }

    override fun propertyValueUpdated(propertyId: String, data: List<Byte>) {
        // Here we only care about Fundamental Resources.
        // Other standard properties should be handled at `ObservablePropertyList.valueUpdated`.
        when (propertyId) {
            // If it is about ResourceList, then store the list internally.
            PropertyResourceNames.RESOURCE_LIST -> {
                try {
                    val list = FoundationalResources.parseResourceList(data)
                    resourceList.clear()
                    resourceList.addAll(list)
                    propertyCatalogUpdated.forEach { it() }

                    // If the parsed body contained an entry for DeviceInfo, and
                    //  if it is configured as auto-queried, then send another Get Property Data request for it.
                    if (device.config.autoSendGetDeviceInfo) {
                        val def = getMetadataList().firstOrNull { it.propertyId == PropertyResourceNames.DEVICE_INFO } as CommonRulesPropertyMetadata?
                        if (def != null)
                            conn.propertyClient.sendGetPropertyData(PropertyResourceNames.DEVICE_INFO, def.encodings.firstOrNull())
                    }
                } catch(ex: JsonParserException) {
                    logger.logError(ex.message!!)
                }
            }
        }
    }

    override fun getHeaderFieldInteger(header: List<Byte>, field: String): Int? = helper.getHeaderFieldInteger(header, field)

    override fun getHeaderFieldString(header: List<Byte>, field: String): String? = helper.getHeaderFieldString(header, field)

    override fun getSubscribedProperty(msg: Message.SubscribeProperty): String? {
        val subscribeId = getHeaderFieldString(msg.header, PropertyCommonHeaderKeys.SUBSCRIBE_ID)
        if (subscribeId == null) {
            logger.logError("Subscribe Id is not found in the property header")
            return null
        }
        val subscription = subscriptions.firstOrNull { it.subscribeId == subscribeId }
        if (subscription == null) {
            logger.logError("Property is not mapped to subscribeId $subscribeId")
            return null
        }
        return subscription.resource
    }

    override fun processPropertySubscriptionResult(subscriptionContext: Any, msg: Message.SubscribePropertyReply) {
        val sub = subscriptionContext as ClientSubscription
        val status = getHeaderFieldInteger(msg.header, PropertyCommonHeaderKeys.STATUS)
        if (status != PropertyExchangeStatus.OK)
            return
        val subscribeId = getHeaderFieldString(msg.header, PropertyCommonHeaderKeys.SUBSCRIBE_ID) ?: return

        if (sub.state == SubscriptionActionState.Unsubscribing)
            // should we rather compare subscribeId? Can we subscribe to one property multiple times? (If yes then this code is wrong)
            subscriptions.removeAll { it.resource == sub.propertyId }
        else
            // does this MUID matter...?
            // does encoding matter...? It could be explicitly specified by client itself, but responder should specify it anyway.
            subscriptions.add(SubscriptionEntry(sub.propertyId, msg.destinationMUID, null, subscribeId))
    }

    override fun createStatusHeader(status: Int): List<Byte> =
        Json.JsonValue(mapOf(Pair(
            Json.JsonValue(PropertyCommonHeaderKeys.STATUS),
            Json.JsonValue(status.toDouble())
        )))
            .getSerializedBytes()

    override fun getUpdatedValue(existing: PropertyValue?, isPartial: Boolean, mediaType: String, body: List<Byte>): Pair<Boolean, List<Byte>> {
        if (!isPartial)
            return Pair(true, body)

        if (existing == null) {
            logger.logError("Partial property update is specified but there is no existing value")
            return Pair(false, listOf())
        }
        if (existing.mediaType != CommonRulesKnownMimeTypes.APPLICATION_JSON) {
            logger.logError("Partial property update is specified but the media type for the existing value is not 'application/json'")
            return Pair(false, listOf())
        }
        if (mediaType != CommonRulesKnownMimeTypes.APPLICATION_JSON) {
            logger.logError("Partial property update is specified but the media type for the new value is not 'application/json'")
            return Pair(false, listOf())
        }

        val failureReturn = Pair(false, existing.body)

        val existingBytes = existing.body
        val existingJson = Json.parseOrNull(existingBytes.toByteArray().decodeToString())
            ?: return failureReturn // existing body is not a valid JSON string

        val bytes = body
        val jsonString = bytes.toByteArray().decodeToString()
        val bodyJson = Json.parseOrNull(MidiCIConverter.decodeASCIIToString(jsonString))
            ?: return failureReturn // reply body is not a valid JSON string

        val result = PropertyPartialUpdater.applyPartialUpdates(existingJson, bodyJson)
        return if (result.first)
            Pair(true, result.second.getSerializedBytes())
        else
            Pair(false, existing.body)
    }

    override fun encodeBody(data: List<Byte>, encoding: String?): List<Byte> = helper.encodeBody(data, encoding)
    override fun decodeBody(header: List<Byte>, body: List<Byte>): List<Byte> = helper.decodeBody(header, body)

    override val propertyCatalogUpdated = mutableListOf<() -> Unit>()

    // implementation

    val subscriptions = mutableListOf<SubscriptionEntry>()

    private val resourceList = mutableListOf<PropertyMetadata>()
}
