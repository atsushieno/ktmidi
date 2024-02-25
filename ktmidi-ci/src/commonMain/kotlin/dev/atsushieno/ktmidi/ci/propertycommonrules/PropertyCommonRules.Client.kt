package dev.atsushieno.ktmidi.ci.propertycommonrules

import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.ci.json.Json
import dev.atsushieno.ktmidi.ci.json.JsonParserException

class CommonRulesPropertyClient(logger: Logger, private val muid: Int, deviceDetails: DeviceDetails) :
    CommonRulesPropertyHelper(logger), MidiCIPropertyClient {
    override fun createDataRequestHeader(propertyId: String, fields: Map<String, Any?>): List<Byte> =
        createRequestHeaderBytes(propertyId, fields)

    override fun createSubscriptionHeader(propertyId: String, fields: Map<String, Any?>): List<Byte> =
        createSubscribeHeaderBytes(propertyId, fields[PropertyCommonHeaderKeys.COMMAND] as String, fields[PropertyCommonHeaderKeys.MUTUAL_ENCODING] as String?)

    override fun getPropertyIdForHeader(header: List<Byte>) = getPropertyIdentifierInternal(header)

    override fun getMetadataList(): List<PropertyMetadata> = resourceList

    override fun getPropertyListRequest(group: Byte, destinationMUID: Int, requestId: Byte) =
        getResourceListRequest(group, destinationMUID, requestId)

    override fun onGetPropertyDataReply(request: Message.GetPropertyData, reply: Message.GetPropertyDataReply) {
        when (getPropertyIdForHeader(request.header)) {
            PropertyResourceNames.RESOURCE_LIST -> {
                val list = getMetadataListForMessage(request, reply) ?: return
                resourceList.clear()
                resourceList.addAll(list)
                propertyCatalogUpdated.forEach { it() }
            }
            PropertyResourceNames.DEVICE_INFO -> {
                val json = getBodyJson(reply.body)
                deviceInfo = MidiCIDeviceInfo(
                    json.getObjectValue(DeviceInfoPropertyNames.MANUFACTURER_ID)?.numberValue?.toInt() ?: 0,
                    json.getObjectValue(DeviceInfoPropertyNames.FAMILY_ID)?.numberValue?.toShort() ?: 0,
                    json.getObjectValue(DeviceInfoPropertyNames.MODEL_ID)?.numberValue?.toShort() ?: 0,
                    json.getObjectValue(DeviceInfoPropertyNames.VERSION_ID)?.numberValue?.toInt() ?: 0,
                    json.getObjectValue(DeviceInfoPropertyNames.MANUFACTURER)?.stringValue ?: "",
                    json.getObjectValue(DeviceInfoPropertyNames.FAMILY)?.stringValue ?: "",
                    json.getObjectValue(DeviceInfoPropertyNames.MODEL)?.stringValue ?: "",
                    json.getObjectValue(DeviceInfoPropertyNames.VERSION)?.stringValue ?: "",
                    json.getObjectValue(DeviceInfoPropertyNames.SERIAL_NUMBER)?.stringValue ?: "",
                    )
            }
        }
        // If the reply message is about ResourceList, then store the list internally.
    }

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
        val sub = subscriptionContext as PropertyExchangeInitiator.ClientSubscription
        val status = getHeaderFieldInteger(msg.header, PropertyCommonHeaderKeys.STATUS)
        if (status != PropertyExchangeStatus.OK)
            return
        val subscribeId = getHeaderFieldString(msg.header, PropertyCommonHeaderKeys.SUBSCRIBE_ID) ?: return

        if (sub.state == PropertyExchangeInitiator.SubscriptionActionState.Unsubscribing)
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

    override fun encodeBody(data: List<Byte>, encoding: String?): List<Byte> = encodeBodyInternal(data, encoding)
    override fun decodeBody(header: List<Byte>, body: List<Byte>): List<Byte> = decodeBodyInternal(header, body)

    override val propertyCatalogUpdated = mutableListOf<() -> Unit>()

    // implementation

    private val resourceList = mutableListOf<PropertyMetadata>()
    var deviceInfo = MidiCIDeviceInfo(
        deviceDetails.manufacturer, deviceDetails.family, deviceDetails.modelNumber, deviceDetails.softwareRevisionLevel,
        "", "", "", "", "")
    val subscriptions = mutableListOf<SubscriptionEntry>()

    private fun getResourceListRequest(group: Byte, destinationMUID: Int, requestId: Byte): Message.GetPropertyData {
        val requestASCIIBytes = getResourceListRequestBytes()
        return Message.GetPropertyData(Message.Common(muid, destinationMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group),
            requestId, requestASCIIBytes)
    }

    private fun getMetadataListForMessage(request: Message.GetPropertyData, reply: Message.GetPropertyDataReply): List<PropertyMetadata>? {
        val id = getPropertyIdForHeader(request.header)
        return if (id == PropertyResourceNames.RESOURCE_LIST) getMetadataListForBody(reply.body) else null
    }

    private fun getBodyJson(body: List<Byte>): Json.JsonValue =
        Json.parse(MidiCIConverter.decodeASCIIToString(body.toByteArray().decodeToString()))

    private fun getMetadataListForBody(body: List<Byte>): List<PropertyMetadata> {
        try {
            val json = getBodyJson(body)
            return getMetadataListForBody(json)
        } catch(ex: JsonParserException) {
            logger.logError(ex.message!!)
            return listOf()
        }
    }

    private fun getMetadataListForBody(body: Json.JsonValue): List<PropertyMetadata> {
        // list of entries (name: value such as {"resource": "foo", "canSet": "partial"})
        val list = body.token.seq.toList()
        return list.map { entry ->
            val res = PropertyMetadata()
            entry.token.map.forEach {
                val v = it.value
                when (it.key.stringValue) {
                    PropertyResourceFields.RESOURCE -> res.resource = v.stringValue
                    PropertyResourceFields.CAN_GET -> res.canGet = v.token.type == Json.TokenType.True
                    PropertyResourceFields.CAN_SET -> res.canSet = v.stringValue
                    PropertyResourceFields.CAN_SUBSCRIBE -> res.canSubscribe = v.token.type == Json.TokenType.True
                    PropertyResourceFields.REQUIRE_RES_ID -> res.requireResId = v.token.type == Json.TokenType.True
                    PropertyResourceFields.ENCODINGS -> res.encodings = v.token.seq.map { e -> e.stringValue }.toList()
                    PropertyResourceFields.MEDIA_TYPE -> res.mediaTypes = v.token.seq.map { e -> e.stringValue }.toList()
                    PropertyResourceFields.SCHEMA -> res.schema = Json.getUnescapedString(v)
                    PropertyResourceFields.CAN_PAGINATE -> res.canPaginate = v.token.type == Json.TokenType.True
                    PropertyResourceFields.COLUMNS -> res.columns = v.token.seq.map { c ->
                        val col = PropertyResourceColumn()
                        c.token.map.forEach { prc ->
                            val cv = prc.value
                            when (prc.key.stringValue) {
                                PropertyResourceColumnFields.PROPERTY -> col.property = cv.stringValue
                                PropertyResourceColumnFields.LINK -> col.link = cv.stringValue
                                PropertyResourceColumnFields.TITLE -> col.title = cv.stringValue
                            }
                        }
                        col
                    }.toList()
                }
            }
            res
        }.toList()
    }
}