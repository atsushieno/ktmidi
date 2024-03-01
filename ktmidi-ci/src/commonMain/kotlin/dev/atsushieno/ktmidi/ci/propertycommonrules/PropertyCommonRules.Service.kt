package dev.atsushieno.ktmidi.ci.propertycommonrules

import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.ci.json.Json
import dev.atsushieno.ktmidi.ci.json.JsonParserException
import kotlin.random.Random

private val defaultPropertyList = listOf(
    CommonRulesPropertyMetadata(PropertyResourceNames.DEVICE_INFO).apply { originator = CommonRulesPropertyMetadata.Originator.SYSTEM },
    //PropertyResource(PropertyResourceNames.CHANNEL_LIST),
    //PropertyResource(PropertyResourceNames.JSON_SCHEMA)
)


private fun CommonRulesPropertyMetadata.jsonValuePairs() = sequence {
    yield(Pair(Json.JsonValue(PropertyResourceFields.RESOURCE), Json.JsonValue(resource)))
    if (!canGet)
        yield(Pair(Json.JsonValue(PropertyResourceFields.CAN_GET), if (canGet) Json.TrueValue else Json.FalseValue))
    if (canSet != PropertySetAccess.NONE)
        yield(Pair(Json.JsonValue(PropertyResourceFields.CAN_SET), Json.JsonValue(canSet)))
    if (canSubscribe)
        yield(Pair(Json.JsonValue(PropertyResourceFields.CAN_SUBSCRIBE), if (canSubscribe) Json.TrueValue else Json.FalseValue))
    if (requireResId)
        yield(Pair(Json.JsonValue(PropertyResourceFields.REQUIRE_RES_ID), if (requireResId) Json.TrueValue else Json.FalseValue))
    if (mediaTypes.size != 1 || mediaTypes[0] != CommonRulesKnownMimeTypes.APPLICATION_JSON)
        yield(Pair(
            Json.JsonValue(PropertyResourceFields.MEDIA_TYPE),
            Json.JsonValue(mediaTypes.map { s -> Json.JsonValue(s) })
        ))
    if (encodings.size != 1 || encodings[0] != PropertyDataEncoding.ASCII)
        yield(Pair(
            Json.JsonValue(PropertyResourceFields.ENCODINGS),
            Json.JsonValue(encodings.map { s -> Json.JsonValue(s) })
        ))
    if (schema != null)
        yield(Pair(Json.JsonValue(PropertyResourceFields.SCHEMA), Json.parse(schema!!)))
    if (canPaginate)
        yield(Pair(Json.JsonValue(PropertyResourceFields.CAN_PAGINATE), if (canPaginate) Json.TrueValue else Json.FalseValue))
    if (columns.any())
        yield(Pair(
            Json.JsonValue(PropertyResourceFields.COLUMNS),
            Json.JsonValue(columns.map { c -> c.toJsonValue() })
        ))
}

fun CommonRulesPropertyMetadata.toJsonValue(): Json.JsonValue = Json.JsonValue(
    jsonValuePairs().toMap()
)

class CommonRulesPropertyService(logger: Logger, private val muid: Int, var deviceInfo: MidiCIDeviceInfo,
                                 private val values: MutableList<PropertyValue>,
                                 private val metadataList: MutableList<CommonRulesPropertyMetadata> = mutableListOf(),
                                 private val channelList: Json.JsonValue? = null,
                                 private val jsonSchema: Json.JsonValue? = null
    )
    : CommonRulesPropertyHelper(logger), MidiCIServicePropertyRules {

    // MidiCIPropertyService implementation
    override fun getPropertyIdForHeader(header: List<Byte>) = getPropertyIdentifierInternal(header)

    override fun createUpdateNotificationHeader(propertyId: String, fields: Map<String, Any?>) =
        createUpdateNotificationHeaderBytes(
            fields[PropertyCommonHeaderKeys.SUBSCRIBE_ID] as String,
            if (fields[PropertyCommonHeaderKeys.SET_PARTIAL] as Boolean)
                MidiCISubscriptionCommand.PARTIAL else MidiCISubscriptionCommand.FULL
        )

    override fun getMetadataList(): List<PropertyMetadata> {
        return defaultPropertyList + metadataList
    }

    // This is the entrypoint for Get Property Data inquiry implementation for the property host.
    // In Common Rules for PE (and ONLY in Common Rules), it needs to decode body based on `mutualEncoding`,
    // and serialize it back into the requested encoding.
    override fun getPropertyData(msg: Message.GetPropertyData) : Result<Message.GetPropertyDataReply> {
        val jsonInquiry = try {
            Json.parse(MidiCIConverter.decodeASCIIToString(msg.header.toByteArray().decodeToString()))
        } catch(ex: JsonParserException) {
            return Result.failure(ex)
        }

        val result = getPropertyData(jsonInquiry)

        val replyHeader = MidiCIConverter.encodeStringToASCII(Json.serialize(result.first)).toASCIIByteArray().toList()
        val replyBody = result.second
        return Result.success(Message.GetPropertyDataReply(Message.Common(muid, msg.sourceMUID, msg.address, msg.group),
            msg.requestId, replyHeader, replyBody))
    }
    override fun setPropertyData(msg: Message.SetPropertyData) : Result<Message.SetPropertyDataReply> {
        val jsonInquiryHeader = try {
            Json.parse(MidiCIConverter.decodeASCIIToString(msg.header.toByteArray().decodeToString()))
        } catch(ex: JsonParserException) {
            return Result.failure(ex)
        }

        val result = setPropertyData(jsonInquiryHeader, msg.body)
        if (result.isFailure)
            return Result.failure(result.exceptionOrNull()!!)

        val replyHeader = MidiCIConverter.encodeStringToASCII(Json.serialize(result.getOrNull()!!)).toASCIIByteArray().toList()
        return Result.success(Message.SetPropertyDataReply(Message.Common(muid, msg.sourceMUID, msg.address, msg.group),
            msg.requestId, replyHeader))
    }

    override fun subscribeProperty(msg: Message.SubscribeProperty): Result<Message.SubscribePropertyReply> {
        val jsonHeader = try {
            Json.parse(MidiCIConverter.decodeASCIIToString(msg.header.toByteArray().decodeToString()))
        } catch(ex: JsonParserException) {
            return Result.failure(ex)
        }
        // body is ignored in PropertyCommonRules.

        val result = subscribe(msg.sourceMUID, jsonHeader)

        val replyHeader = MidiCIConverter.encodeStringToASCII(Json.serialize(result.first)).toASCIIByteArray().toList()
        val replyBody = MidiCIConverter.encodeStringToASCII(Json.serialize(result.second)).toASCIIByteArray().toList()
        return Result.success(Message.SubscribePropertyReply(Message.Common(muid, msg.sourceMUID, msg.address, msg.group),
                msg.requestId, replyHeader, replyBody))
    }

    override fun addMetadata(property: PropertyMetadata) {
        metadataList.add(property as CommonRulesPropertyMetadata)
        propertyCatalogUpdated.forEach { it() }
    }

    override fun removeMetadata(propertyId: String) {
        metadataList.removeAll { it.propertyId == propertyId }
        propertyCatalogUpdated.forEach { it() }
    }

    override val propertyCatalogUpdated = mutableListOf<() -> Unit>()

    // impl

    val linkedResources = mutableMapOf<String, List<Byte>>()
    val subscriptions = mutableListOf<SubscriptionEntry>()

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
            json.getObjectValue(PropertyCommonHeaderKeys.MUTUAL_ENCODING)?.stringValue,
            json.getObjectValue(PropertyCommonHeaderKeys.MEDIA_TYPE)?.stringValue,
            json.getObjectValue(PropertyCommonHeaderKeys.OFFSET)?.numberValue?.toInt(),
            json.getObjectValue(PropertyCommonHeaderKeys.LIMIT)?.numberValue?.toInt(),
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
        if (src.subscribeId != null)
            this[Json.JsonValue(PropertyCommonHeaderKeys.SUBSCRIBE_ID)] = Json.JsonValue(src.subscribeId)
        if (src.totalCount != null)
            this[Json.JsonValue(PropertyCommonHeaderKeys.TOTAL_COUNT)] = Json.JsonValue(src.totalCount.toDouble())
    })

    private fun getPropertyDataJson(header: PropertyCommonRequestHeader): Pair<Json.JsonValue, Json.JsonValue> {
        val body = when(header.resource) {
            PropertyResourceNames.RESOURCE_LIST -> Json.JsonValue(getMetadataList().map { (it as CommonRulesPropertyMetadata).toJsonValue() })
            PropertyResourceNames.DEVICE_INFO -> getDeviceInfoJson()
            PropertyResourceNames.CHANNEL_LIST -> channelList
            PropertyResourceNames.JSON_SCHEMA -> jsonSchema
            else -> {
                val bytes = linkedResources[header.resId] ?: values.firstOrNull { it.id == header.resource }?.body
                    ?: throw PropertyExchangeException("Unknown property: ${header.resource} (resId: ${header.resId}")
                if (bytes.any()) Json.parse(bytes.toByteArray().decodeToString()) else Json.EmptyObject
            }
        }

        // Property list pagination (Common Rules for PE 6.6.2)
        val paginatedBody =
            if (body?.token?.type == Json.TokenType.Array && header.offset != null) {
                if (header.limit == null)
                    Json.JsonValue(body.token.seq.drop(header.offset).toList())
                else
                    Json.JsonValue(body.token.seq.drop(header.offset).take(header.limit).toList())
            }
            else body
        val totalCount = if (body != paginatedBody) body?.token?.seq?.toList()?.size else null
        return Pair(getReplyHeaderJson(PropertyCommonReplyHeader(PropertyExchangeStatus.OK, mutualEncoding = header.mutualEncoding, totalCount = totalCount)), paginatedBody ?: Json.EmptyObject)
    }

    // It returns the *encoded* result.
    private fun getPropertyData(headerJson: Json.JsonValue): Pair<Json.JsonValue, List<Byte>> {
        val header = getPropertyHeader(headerJson)
        if (header.mediaType == null || header.mediaType == CommonRulesKnownMimeTypes.APPLICATION_JSON) {
            val ret = getPropertyDataJson(header)
            val body = MidiCIConverter.encodeStringToASCII(Json.serialize(ret.second)).toASCIIByteArray().toList()
            val encodedBody = encodeBody(body, header.mutualEncoding)
            return Pair(ret.first, encodedBody)
        } else {
            val body = linkedResources[header.resId] ?: values.firstOrNull { it.id == header.resource }?.body
                ?: listOf()
            val encodedBody = encodeBody(body, header.mutualEncoding)
            val replyHeader = getReplyHeaderJson(PropertyCommonReplyHeader(PropertyExchangeStatus.OK, mutualEncoding = header.mutualEncoding))
            return Pair(replyHeader, encodedBody)
        }
    }

    fun setPropertyData(headerJson: Json.JsonValue, body: List<Byte>): Result<Json.JsonValue> {
        val header = getPropertyHeader(headerJson)
        when (header.resource) {
            PropertyResourceNames.RESOURCE_LIST ->
                return Result.failure(PropertyExchangeException("Property is readonly: ${PropertyResourceNames.RESOURCE_LIST}"))
            PropertyResourceNames.JSON_SCHEMA ->
                return Result.failure(PropertyExchangeException("Property is readonly: ${PropertyResourceNames.JSON_SCHEMA}"))
            PropertyResourceNames.CHANNEL_LIST ->
                return Result.failure(PropertyExchangeException("Property is readonly: ${PropertyResourceNames.CHANNEL_LIST}"))
        }

        val decodedBody = decodeBody(header.mutualEncoding, body)
        // Perform partial updates, if applicable
        val existing = values.firstOrNull { it.id == header.resource }
        if (headerJson.getObjectValue(PropertyCommonHeaderKeys.SET_PARTIAL)?.isBooleanTrue == true) {
            if (existing == null) {
                logger.logError("Partial update is specified but there is no existing value for property ${header.resource}")
            } else {
                val bodyJson = try {
                    Json.parse(MidiCIConverter.decodeASCIIToString(decodedBody.toByteArray().decodeToString()))
                } catch(ex: JsonParserException) {
                    return Result.failure(ex)
                }
                val result = PropertyPartialUpdater.applyPartialUpdates(Json.parse(existing.body.toByteArray().decodeToString()), bodyJson)
                if (!result.first) {
                    logger.logError("Failed partial update for property ${header.resource}")
                }
                else
                    existing.body = result.second.getSerializedBytes()
            }
        }
        else if (existing != null)
            existing.body = decodedBody
        else
            values.add(PropertyValue(header.resource, header.mediaType ?: CommonRulesKnownMimeTypes.APPLICATION_JSON, decodedBody))
        return Result.success(getReplyHeaderJson(PropertyCommonReplyHeader(PropertyExchangeStatus.OK)))
    }

    private fun createNewSubscriptionId(): String =
        Random.nextInt(100000000).toString()

    fun subscribe(subscriberMUID: Int, headerJson: Json.JsonValue) : Pair<Json.JsonValue, Json.JsonValue> {
        val header = getPropertyHeader(headerJson)
        val subscription = SubscriptionEntry(header.resource, subscriberMUID, header.mutualEncoding, createNewSubscriptionId())
        subscriptions.add(subscription)
        // body is empty
        return Pair(getReplyHeaderJson(PropertyCommonReplyHeader(PropertyExchangeStatus.OK, subscribeId = subscription.subscribeId)), Json.JsonValue(mapOf()))
    }

    fun createTerminateNotificationHeader(subscribeId: String): List<Byte> =
        createUpdateNotificationHeaderBytes(subscribeId, MidiCISubscriptionCommand.END)

    override fun encodeBody(data: List<Byte>, encoding: String?): List<Byte> = encodeBodyInternal(data, encoding)
    override fun decodeBody(header: List<Byte>, body: List<Byte>): List<Byte> = decodeBodyInternal(header, body)
    private fun decodeBody(mutualEncoding: String?, body: List<Byte>): List<Byte> = decodeBodyInternal(mutualEncoding, body)
}