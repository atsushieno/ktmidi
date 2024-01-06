package dev.atsushieno.ktmidi.ci

import io.ktor.utils.io.core.*

private val defaultPropertyList = listOf(
    PropertyMetadata(PropertyResourceNames.DEVICE_INFO),
    //PropertyResource(PropertyResourceNames.CHANNEL_LIST),
    //PropertyResource(PropertyResourceNames.JSON_SCHEMA)
)


private fun PropertyMetadata.jsonValuePairs() = sequence {
    yield(Pair(Json.JsonValue(PropertyResourceFields.RESOURCE), Json.JsonValue(resource)))
    if (!canGet)
        yield(Pair(Json.JsonValue(PropertyResourceFields.CAN_GET), if (canGet) Json.TrueValue else Json.FalseValue))
    if (canSet != PropertySetAccess.NONE)
        yield(Pair(Json.JsonValue(PropertyResourceFields.CAN_SET), Json.JsonValue(canSet)))
    if (canSubscribe)
        yield(Pair(Json.JsonValue(PropertyResourceFields.CAN_SUBSCRIBE), if (canSubscribe) Json.TrueValue else Json.FalseValue))
    if (requireResId)
        yield(Pair(Json.JsonValue(PropertyResourceFields.REQUIRE_RES_ID), if (requireResId) Json.TrueValue else Json.FalseValue))
    if (mediaTypes.size != 1 || mediaTypes[0] != "application/json")
        yield(Pair(
            Json.JsonValue(PropertyResourceFields.MEDIA_TYPE),
            Json.JsonValue(mediaTypes.map { s -> Json.JsonValue(s) })
        ))
    if (encodings.size != 1 || encodings[0] != "ASCII")
        yield(Pair(
            Json.JsonValue(PropertyResourceFields.ENCODINGS),
            Json.JsonValue(encodings.map { s -> Json.JsonValue(s) })
        ))
    if (schema != null)
        yield(Pair(Json.JsonValue(PropertyResourceFields.SCHEMA), schema!!))
    if (canPaginate)
        yield(Pair(Json.JsonValue(PropertyResourceFields.CAN_PAGINATE), if (canPaginate) Json.TrueValue else Json.FalseValue))
    if (columns.any())
        yield(Pair(
            Json.JsonValue(PropertyResourceFields.COLUMNS),
            Json.JsonValue(columns.map { c -> c.toJsonValue() })
        ))
}

fun PropertyMetadata.toJsonValue(): Json.JsonValue = Json.JsonValue(
    jsonValuePairs().toMap()
)

class CommonRulesPropertyService(private val muid: Int, var deviceInfo: MidiCIDeviceInfo,
                                 private val metadataList: MutableList<PropertyMetadata> = mutableListOf<PropertyMetadata>().apply { addAll(defaultPropertyList) })
    : MidiCIPropertyService {

    // MidiCIPropertyService implementation
    override fun getPropertyIdForHeader(header: List<Byte>) = CommonRulesPropertyHelper.getPropertyIdentifier(header)

    override fun getMetadataList(): List<PropertyMetadata> {
        return metadataList
    }

    override fun getPropertyData(msg: Message.GetPropertyData) : Message.GetPropertyDataReply {
        val jsonInquiry =
            Json.parseOrNull(PropertyCommonConverter.decodeASCIIToString(msg.header.toByteArray().decodeToString()))
        // FIXME: log error if it failed to parse JSON

        val result = if (jsonInquiry != null) getPropertyData(jsonInquiry) else null

        // FIXME: header should set error status
        val replyHeader = if (result == null) listOf() else PropertyCommonConverter.encodeStringToASCII(Json.serialize(result.first)).toByteArray().toList()
        val replyBody = if (result == null) listOf() else PropertyCommonConverter.encodeStringToASCII(Json.serialize(result.second)).toByteArray().toList()
        return Message.GetPropertyDataReply(muid, msg.sourceMUID, msg.requestId, replyHeader, 1, 1, replyBody)
    }
    override fun setPropertyData(msg: Message.SetPropertyData) : Message.SetPropertyDataReply {
        val jsonInquiryHeader =
            Json.parseOrNull(PropertyCommonConverter.decodeASCIIToString(msg.header.toByteArray().decodeToString()))
        val jsonInquiryBody =
            Json.parseOrNull(PropertyCommonConverter.decodeASCIIToString(msg.body.toByteArray().decodeToString()))
        // FIXME: log errors if it parsed to fail either of JSON above.

        val result =
            if (jsonInquiryHeader != null && jsonInquiryBody != null)
                setPropertyData(jsonInquiryHeader, jsonInquiryBody)
            else null

        // FIXME: header should set error status
        val replyHeader = if (result == null) listOf() else PropertyCommonConverter.encodeStringToASCII(Json.serialize(result)).toByteArray().toList()
        return Message.SetPropertyDataReply(muid, msg.sourceMUID, msg.requestId, replyHeader)
    }

    override fun subscribeProperty(msg: Message.SubscribeProperty): Message.SubscribePropertyReply {
        val jsonHeader =
            Json.parseOrNull(PropertyCommonConverter.decodeASCIIToString(msg.header.toByteArray().decodeToString()))
        // body is ignored in PropertyCommonRules.
        // FIXME: log errors if it parsed to fail either of JSON above.

        val result = if (jsonHeader != null) subscribe(msg.sourceMUID, jsonHeader) else null

        // FIXME: header should set error status
        val replyHeader = if (result == null) listOf() else PropertyCommonConverter.encodeStringToASCII(Json.serialize(result.first)).toByteArray().toList()
        val replyBody = if (result == null) listOf() else PropertyCommonConverter.encodeStringToASCII(Json.serialize(result.second)).toByteArray().toList()
        return Message.SubscribePropertyReply(muid, msg.sourceMUID, msg.requestId, replyHeader, replyBody)
    }

    override fun getReplyStatusFor(header: List<Byte>): Int? = CommonRulesPropertyHelper.getReplyStatusFor(header)

    override fun getMediaTypeFor(replyHeader: List<Byte>): String =
        CommonRulesPropertyHelper.getMediaTypeFor(replyHeader)

    override fun addMetadata(property: PropertyMetadata) {
        metadataList.add(property)
        propertyCatalogUpdated.forEach { it() }
    }

    override val propertyCatalogUpdated = mutableListOf<() -> Unit>()

    // impl

    data class SubscriptionEntry(val resource: String, val muid: Int)

    val linkedResources = mutableMapOf<String, Json.JsonValue>()
    private val values = mutableMapOf<String, Json.JsonValue>()
    private val subscriptions = mutableListOf<SubscriptionEntry>()

    private fun bytesToJsonArray(list: List<Byte>) = list.map { Json.JsonValue(it.toDouble()) }
    private fun getDeviceInfoJson(): Json.JsonValue {
        return Json.JsonValue(
            mapOf(
                Pair(
                    Json.JsonValue(DeviceInfoPropertyNames.MANUFACTURER_ID),
                    Json.JsonValue(bytesToJsonArray(deviceInfo.manufacturerIdBytes()))
                ),
                Pair(
                    Json.JsonValue(DeviceInfoPropertyNames.FAMILY_ID),
                    Json.JsonValue(bytesToJsonArray(deviceInfo.familyIdBytes()))
                ),
                Pair(
                    Json.JsonValue(DeviceInfoPropertyNames.MODEL_ID),
                    Json.JsonValue(bytesToJsonArray(deviceInfo.modelIdBytes()))
                ),
                Pair(
                    Json.JsonValue(DeviceInfoPropertyNames.VERSION_ID),
                    Json.JsonValue(bytesToJsonArray(deviceInfo.versionIdBytes()))
                ),
                Pair(Json.JsonValue(DeviceInfoPropertyNames.MANUFACTURER), Json.JsonValue(deviceInfo.manufacturer)),
                Pair(Json.JsonValue(DeviceInfoPropertyNames.FAMILY), Json.JsonValue(deviceInfo.family)),
                Pair(Json.JsonValue(DeviceInfoPropertyNames.MODEL), Json.JsonValue(deviceInfo.model)),
                Pair(Json.JsonValue(DeviceInfoPropertyNames.VERSION), Json.JsonValue(deviceInfo.version)),
            ) + if (deviceInfo.serialNumber != null) mapOf(
                Pair(Json.JsonValue(DeviceInfoPropertyNames.SERIAL_NUMBER), Json.JsonValue(deviceInfo.serialNumber!!)),
            ) else mapOf()
        )
    }

    private fun getPropertyHeader(json: Json.JsonValue) =
        PropertyCommonRequestHeader(
            json.getObjectValue(PropertyCommonHeaderKeys.RESOURCE)?.stringValue ?: "",
            json.getObjectValue(PropertyCommonHeaderKeys.RES_ID)?.stringValue,
            json.getObjectValue(PropertyCommonHeaderKeys.MUTUAL_ENCODING)?.stringValue
        )
    private fun getReplyHeaderJson(src: PropertyCommonReplyHeader) = Json.JsonValue(mutableMapOf(
        Pair(Json.JsonValue(PropertyCommonHeaderKeys.STATUS), Json.JsonValue(src.status.toDouble()))
    ).apply {
        if (src.message != null)
            this[Json.JsonValue(PropertyCommonHeaderKeys.MESSAGE)] = Json.JsonValue(src.message)
        if (src.mutualEncoding != null && src.mutualEncoding != PropertyDataEncoding.ASCII)
            this[Json.JsonValue(PropertyCommonHeaderKeys.MUTUAL_ENCODING)] = Json.JsonValue(src.mutualEncoding)
        if (src.cacheTime != null)
            this[Json.JsonValue(PropertyCommonHeaderKeys.CACHE_TIME)] = Json.JsonValue(src.cacheTime)
        if (src.mediaType != null)
            this[Json.JsonValue(PropertyCommonHeaderKeys.MEDIA_TYPE)] = Json.JsonValue(src.mediaType)
    })

    fun getPropertyData(headerJson: Json.JsonValue): Pair<Json.JsonValue, Json.JsonValue> {
        val header = getPropertyHeader(headerJson)
        val body = when(header.resource) {
            PropertyResourceNames.RESOURCE_LIST -> Json.JsonValue(metadataList.map { it.toJsonValue() })
            PropertyResourceNames.DEVICE_INFO -> getDeviceInfoJson()
            PropertyResourceNames.CHANNEL_LIST -> Json.JsonValue(mapOf()) // FIXME: implement
            PropertyResourceNames.JSON_SCHEMA -> Json.JsonValue(mapOf()) // FIXME: implement
            else -> {
                linkedResources[header.resId] ?: values[header.resource]
                    ?: throw PropertyExchangeException("Unknown property: ${header.resource} (resId: ${header.resId}")
            }
        }
        return Pair(getReplyHeaderJson(PropertyCommonReplyHeader(PropertyExchangeStatus.OK)), body)
    }

    fun setPropertyData(headerJson: Json.JsonValue, bodyJson: Json.JsonValue): Json.JsonValue {
        val header = getPropertyHeader(headerJson)
        when (header.resource) {
            PropertyResourceNames.RESOURCE_LIST -> throw PropertyExchangeException("Property is readonly: ${PropertyResourceNames.RESOURCE_LIST}")
            PropertyResourceNames.JSON_SCHEMA -> throw PropertyExchangeException("Property is readonly: ${PropertyResourceNames.JSON_SCHEMA}")
            PropertyResourceNames.CHANNEL_LIST -> throw PropertyExchangeException("Property is readonly: ${PropertyResourceNames.CHANNEL_LIST}")
        }
        values[header.resource] = bodyJson
        return getReplyHeaderJson(PropertyCommonReplyHeader(PropertyExchangeStatus.OK))
    }

    fun subscribe(subscriberMUID: Int, headerJson: Json.JsonValue) : Pair<Json.JsonValue, Json.JsonValue> {
        val header = getPropertyHeader(headerJson)
        subscriptions.add(SubscriptionEntry(header.resource, subscriberMUID))
        // body is empty
        return Pair(getReplyHeaderJson(PropertyCommonReplyHeader(PropertyExchangeStatus.OK)), Json.JsonValue(mapOf()))
    }
}