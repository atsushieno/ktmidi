package dev.atsushieno.ktmidi.ci.propertycommonrules

import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.ci.json.Json
import dev.atsushieno.ktmidi.ci.json.JsonParserException
import kotlin.random.Random

private val defaultPropertyList = listOf(
    PropertyMetadata(PropertyResourceNames.DEVICE_INFO).apply { originator = PropertyMetadata.Originator.SYSTEM },
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

fun PropertyMetadata.toJsonValue(): Json.JsonValue = Json.JsonValue(
    jsonValuePairs().toMap()
)

class CommonRulesPropertyService(logger: Logger, private val muid: Int, var deviceInfo: MidiCIDeviceInfo,
                                 private val metadataList: MutableList<PropertyMetadata> = mutableListOf(),
                                 private val channelList: Json.JsonValue? = null,
                                 private val jsonSchema: Json.JsonValue? = null
    )
    : CommonRulesPropertyHelper(logger), MidiCIPropertyService {

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

    override fun getPropertyData(msg: Message.GetPropertyData) : Result<Message.GetPropertyDataReply> {
        val jsonInquiry = try {
            Json.parse(MidiCIConverter.decodeASCIIToString(msg.header.toByteArray().decodeToString()))
        } catch(ex: JsonParserException) {
            return Result.failure(ex)
        }

        val result = getPropertyData(jsonInquiry)

        val replyHeader = MidiCIConverter.encodeStringToASCII(Json.serialize(result.first)).toASCIIByteArray().toList()
        val replyBody = MidiCIConverter.encodeStringToASCII(Json.serialize(result.second)).toASCIIByteArray().toList()
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
        metadataList.add(property)
        propertyCatalogUpdated.forEach { it() }
    }

    override fun removeMetadata(propertyId: String) {
        metadataList.removeAll { it.resource == propertyId }
        propertyCatalogUpdated.forEach { it() }
    }

    override val propertyCatalogUpdated = mutableListOf<() -> Unit>()

    // impl

    val linkedResources = mutableMapOf<String, Json.JsonValue>()
    private val values = mutableMapOf<String, Json.JsonValue>()
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
            json.getObjectValue(PropertyCommonHeaderKeys.MEDIA_TYPE)?.stringValue
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
    })

    fun getPropertyData(headerJson: Json.JsonValue): Pair<Json.JsonValue, Json.JsonValue> {
        val header = getPropertyHeader(headerJson)
        val body = when(header.resource) {
            PropertyResourceNames.RESOURCE_LIST -> Json.JsonValue(metadataList.map { it.toJsonValue() })
            PropertyResourceNames.DEVICE_INFO -> getDeviceInfoJson()
            PropertyResourceNames.CHANNEL_LIST -> channelList
            PropertyResourceNames.JSON_SCHEMA -> jsonSchema
            else -> {
                linkedResources[header.resId] ?: values[header.resource]
                    ?: throw PropertyExchangeException("Unknown property: ${header.resource} (resId: ${header.resId}")
            }
        }
        return Pair(getReplyHeaderJson(PropertyCommonReplyHeader(PropertyExchangeStatus.OK)), body ?: Json.EmptyObject)
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

        val decodedBody = when (header.mutualEncoding) {
            PropertyDataEncoding.MCODED7 -> PropertyCommonConverter.decodeMcoded7(body)
            PropertyDataEncoding.ZLIB_MCODED7 -> PropertyCommonConverter.decodeZlibMcoded7(body)
            PropertyDataEncoding.ASCII -> body
            null -> body
            else -> return Result.failure(PropertyExchangeException("Unknown mutualEncoding was specified: ${header.mutualEncoding}"))
        }

        if (header.mediaType != CommonRulesKnownMimeTypes.APPLICATION_JSON)
            TODO("FIXME: we need to change internal value list type to hold List<Byte> instead of JsonValue")

        val bodyJson = try {
            Json.parse(MidiCIConverter.decodeASCIIToString(decodedBody.toByteArray().decodeToString()))
        } catch(ex: JsonParserException) {
            return Result.failure(ex)
        }

        // Perform partial updates, if applicable
        if (headerJson.getObjectValue(PropertyCommonHeaderKeys.SET_PARTIAL)?.isBooleanTrue == true) {
            val existing = values[header.resource]
            if (existing == null) {
                logger.logError("Partial update is specified but there is no existing value for property ${header.resource}")
            } else {
                val result = PropertyPartialUpdater.applyPartialUpdates(existing, bodyJson)
                if (!result.first) {
                    logger.logError("Failed partial update for property ${header.resource}")
                }
                else
                    values[header.resource] = result.second
            }
        }
        else
            values[header.resource] = bodyJson
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
    override fun decodeBody(data: List<Byte>, encoding: String?): List<Byte> = decodeBodyInternal(data, encoding)
}