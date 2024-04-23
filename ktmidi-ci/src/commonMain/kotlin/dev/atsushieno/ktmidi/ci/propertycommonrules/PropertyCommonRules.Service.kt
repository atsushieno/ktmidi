package dev.atsushieno.ktmidi.ci.propertycommonrules

import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.ci.json.Json
import dev.atsushieno.ktmidi.ci.json.JsonParserException
import kotlin.jvm.JvmName
import kotlin.random.Random

private val defaultPropertyList = listOf(
    CommonRulesPropertyMetadata(PropertyResourceNames.DEVICE_INFO).apply { originator = CommonRulesPropertyMetadata.Originator.SYSTEM },
    CommonRulesPropertyMetadata(PropertyResourceNames.CHANNEL_LIST).apply { originator = CommonRulesPropertyMetadata.Originator.SYSTEM },
    CommonRulesPropertyMetadata(PropertyResourceNames.JSON_SCHEMA).apply { originator = CommonRulesPropertyMetadata.Originator.SYSTEM }
)


fun CommonRulesPropertyMetadata.toJsonValue(): Json.JsonValue = Json.JsonValue(
    sequence {
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
    }.toMap()
)

class CommonRulesPropertyService(private val device: MidiCIDevice)
    : MidiCIServicePropertyRules {
    private val helper = CommonRulesPropertyHelper(device)
    private val logger by device::logger
    private val muid by device::muid
    internal var deviceInfo
        @JvmName("get_deviceInfo")
        get() = device.config.deviceInfo
        @JvmName("set_deviceInfo")
        set(value) { device.config.deviceInfo = value }
    internal var channelList
        @JvmName("get_channelList")
        get() = device.config.channelList
        @JvmName("set_channelList")
        set(value) { device.config.channelList = value }
    internal var jsonSchemaString
        @JvmName("get_jsonSchemaString")
        get() = device.config.jsonSchemaString
        @JvmName("set_jsonSchemaString")
        set(value) { device.config.jsonSchemaString = value }
    private val values: List<PropertyValue>
        @JvmName("get_propertyValues")
        get() = device.config.propertyValues
    private val metadataList
        @JvmName("get_metadataList")
        get() = device.config.propertyMetadataList

    // MidiCIPropertyService implementation
    override val subscriptions = mutableListOf<SubscriptionEntry>()

    override fun getPropertyIdForHeader(header: List<Byte>) = helper.getPropertyIdentifierInternal(header)
    override fun getHeaderFieldString(header: List<Byte>, field: String) = helper.getHeaderFieldString(header, field)

    override fun createUpdateNotificationHeader(propertyId: String, fields: Map<String, Any?>) =
        helper.createSubscribePropertyHeaderBytes(
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

        val result =
            if (jsonHeader.getObjectValue(PropertyCommonHeaderKeys.COMMAND)?.stringValue == MidiCISubscriptionCommand.END)
                unsubscribe(getPropertyIdForHeader(msg.header), jsonHeader.getObjectValue(PropertyCommonHeaderKeys.SUBSCRIBE_ID)?.stringValue)
            else
                subscribe(msg.sourceMUID, jsonHeader)

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

    override fun createShutdownSubscriptionHeader(propertyId: String): List<Byte> {
        val sub = subscriptions.firstOrNull { it.resource == propertyId } ?: throw MidiCIException("Specified property $propertyId is not at subscribed state")
        val header = helper.createSubscribePropertyHeaderBytes(sub.subscribeId, MidiCISubscriptionCommand.END)
        subscriptions.remove(sub)
        return header
    }

    // impl

    val linkedResources = mutableMapOf<String, List<Byte>>()

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

    private fun MidiCIChannel.toJson() = Json.JsonValue(mapOf(
        Json.JsonValue(ChannelInfoPropertyNames.TITLE) to Json.JsonValue(title),
        Json.JsonValue(ChannelInfoPropertyNames.CHANNEL) to Json.JsonValue(channel.toDouble()),
        Json.JsonValue(ChannelInfoPropertyNames.PROGRAM_TITLE) to
                if (programTitle == null) null else Json.JsonValue(programTitle),
        Json.JsonValue(ChannelInfoPropertyNames.BANK_PC) to
                if (bankPC.all { it.toInt() == 0 }) null else Json.JsonValue(bankPC.map { Json.JsonValue(it.toDouble()) }),
        Json.JsonValue(ChannelInfoPropertyNames.CLUSTER_CHANNEL_START) to
                if (clusterChannelStart <= 1) null else Json.JsonValue(clusterChannelStart.toDouble()),
        Json.JsonValue(ChannelInfoPropertyNames.CLUSTER_LENGTH) to
                if (clusterChannelStart <= 1) null else Json.JsonValue(clusterLength.toDouble()),
        Json.JsonValue(ChannelInfoPropertyNames.CLUSTER_MIDI_MODE) to
                if (clusterMidiMode.toInt() == 3) null else Json.JsonValue(clusterMidiMode.toDouble()),
        Json.JsonValue(ChannelInfoPropertyNames.CLUSTER_TYPE) to
                if (clusterType == null || clusterType == ClusterType.OTHER) null else Json.JsonValue(clusterType)
    ).filterValues { it != null }.map { Pair(it.key, it.value!!) }.toMap())

    private fun getPropertyHeader(json: Json.JsonValue) =
        PropertyCommonRequestHeader(
            json.getObjectValue(PropertyCommonHeaderKeys.RESOURCE)?.stringValue ?: "",
            json.getObjectValue(PropertyCommonHeaderKeys.RES_ID)?.stringValue,
            json.getObjectValue(PropertyCommonHeaderKeys.MUTUAL_ENCODING)?.stringValue,
            json.getObjectValue(PropertyCommonHeaderKeys.MEDIA_TYPE)?.stringValue,
            json.getObjectValue(PropertyCommonHeaderKeys.OFFSET)?.numberValue?.toInt(),
            json.getObjectValue(PropertyCommonHeaderKeys.LIMIT)?.numberValue?.toInt(),
            json.getObjectValue(PropertyCommonHeaderKeys.SET_PARTIAL)?.isBooleanTrue,
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
            PropertyResourceNames.RESOURCE_LIST ->
                Json.JsonValue(getMetadataList().map { (it as CommonRulesPropertyMetadata).toJsonValue() })
            PropertyResourceNames.DEVICE_INFO ->
                getDeviceInfoJson()
            PropertyResourceNames.CHANNEL_LIST ->
                if (channelList.channels.isEmpty()) null else Json.JsonValue(channelList.channels.map { it.toJson() })
            PropertyResourceNames.JSON_SCHEMA ->
                if (device.config.jsonSchemaString.isNotBlank()) Json.parse(device.config.jsonSchemaString) else null
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
        if (defaultPropertyList.any { it.propertyId == header.resource })
            return Result.failure(PropertyExchangeException("Resource is readonly: ${header.resource}"))

        val decodedBody = decodeBody(header.mutualEncoding, body)
        // Perform partial updates, if applicable
        val existing = values.firstOrNull { it.id == header.resource }
        if (header.setPartial == true) {
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
            device.config.propertyValues.add(PropertyValue(header.resource, header.resId, header.mediaType ?: CommonRulesKnownMimeTypes.APPLICATION_JSON, decodedBody))
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

    fun unsubscribe(resource: String, subscribeId: String?) : Pair<Json.JsonValue, Json.JsonValue> {
        val existing = subscriptions.firstOrNull { subscribeId != null && it.subscribeId == subscribeId }
            ?: subscriptions.firstOrNull { it.resource == resource }
        subscriptions.remove(existing)
        // body is empty
        return Pair(getReplyHeaderJson(PropertyCommonReplyHeader(PropertyExchangeStatus.OK, subscribeId = subscribeId)), Json.JsonValue(mapOf()))
    }

    override fun encodeBody(data: List<Byte>, encoding: String?): List<Byte> = helper.encodeBody(data, encoding)
    override fun decodeBody(header: List<Byte>, body: List<Byte>): List<Byte> = helper.decodeBody(header, body)
    private fun decodeBody(mutualEncoding: String?, body: List<Byte>): List<Byte> = helper.decodeBody(mutualEncoding, body)
}