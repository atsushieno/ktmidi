package dev.atsushieno.ktmidi.ci

import io.ktor.utils.io.core.*
import kotlinx.coroutines.sync.Mutex

class PropertyExchangeException(message: String = "Property Exchange exception", innerException: Exception? = null) : Exception(message, innerException)

data class MidiCIDeviceInfo(
    val manufacturerId: Int,
    val familyId: Short,
    val modelId: Short,
    val versionId: Int,
    val manufacturer: String,
    val family: String,
    val model: String,
    val version: String,
    val serialNumber: String? = null
) {
    private fun toBytes(v: Int) = listOf(
        (v and 0x7F).toByte(),
        ((v shr 8) and 0x7F).toByte(),
        ((v shr 16) and 0x7F).toByte()
    )
    private fun toBytes(v: Short) = listOf(
        (v.toInt() and 0x7F).toByte(),
        ((v.toInt() shr 8) and 0x7F).toByte()
    )
    fun manufacturerIdBytes() = toBytes(manufacturerId)
    fun familyIdBytes() = toBytes(familyId)
    fun modelIdBytes() = toBytes(modelId)
    fun versionIdBytes() = toBytes(versionId)
}

object PropertyCommonHeaderKeys {
    const val RESOURCE = "resource"
    const val RES_ID = "resId"
    const val MUTUAL_ENCODING = "mutualEncoding"
    const val STATUS = "status"
    const val MESSAGE = "message"
    const val CACHE_TIME = "cacheTime"
    // M2-103-UM 5.5 Extra Header Property for Using Property Data which is Not JSON Data
    const val MEDIA_TYPE = "mediaType"
}

object CommonRulesKnownMimeTypes {
    const val APPLICATION_JSON = "application/json"
}

object PropertyExchangeStatus {
    const val OK = 200
    const val Accepted = 202
    const val ResourceUnavailableOrError = 341
    const val BadData = 342
    const val TooManyRequests = 343
    const val BadRequest = 400
    const val Unauthorized = 403
    const val NotFound = 404
    const val NotAllowed = 405
    const val PayloadTooLarge = 413
    const val UnsupportedMediaType = 415
    const val InvalidDataVersion = 445
    const val InternalError = 500
}

object PropertyDataEncoding {
    const val ASCII = "ASCII"
    const val MCODED7 = "Mcoded7"
    const val ZLIB_MCODED7 = "zlib+Mcoded7"
}

object PropertyResourceNames {
    // 7.2 Foundational Resources Defined in other Specifications
    const val RESOURCE_LIST = "ResourceList"

    // M2-105-UM_v1-1-1_Property_Exchange_Foundational_Resources.pdf
    const val DEVICE_INFO = "DeviceInfo"
    const val CHANNEL_LIST = "ChannelList"
    const val JSON_SCHEMA = "JSONSchema"

    // M2-106-UM_v1-01_Property_Exchange_Mode_Resources.pdf
    const val MODE_LIST = "ModeList"
    const val CURRENT_MODE = "CurrentMode"

    // M2-108-UM_v1-01_Channel_Resources.pdf
    const val CHANNEL_MODE = "ChannelMode"
    const val BASIC_CHANNEL_RX = "BasicChannelRx"
    const val BASIC_CHANNEL_TX = "BasicChannelTx"

    // M2-109-UM_v1-01_LocalOn_Resource.pdf
    const val LOCAL_ON = "LocalOn"

    // M2-112-UM_v1-0_ExternalSync_Resource.pdf
    const val EXTERNAL_SYNC = "ExternalSync"
}

object DeviceInfoPropertyNames {
    const val MANUFACTURER_ID = "manufacturerId"
    const val FAMILY_ID = "familyId"
    const val MODEL_ID = "modelId"
    const val VERSION_ID = "versionId"
    const val MANUFACTURER = "manufacturer"
    const val FAMILY = "family"
    const val MODEL = "model"
    const val VERSION = "version"
    const val SERIAL_NUMBER = "serialNumber"
}

data class PropertyCommonRequestHeader(
    val resource: String,
    val resId: String? = null,
    val mutualEncoding: String? = PropertyDataEncoding.ASCII
)

data class PropertyCommonReplyHeader(
    val status: Int,
    val message: String? = null,
    val mutualEncoding: String? = PropertyDataEncoding.ASCII,
    val cacheTime: String? = null,
    // M2-103-UM 5.5 Extra Header Property for Using Property Data which is Not JSON Data
    val mediaType: String? = null
)

object PropertyCommonConverter {
    fun areBytesEquivalentTo(data: List<Byte>, expected: String): Boolean {
        if (data.size != expected.length)
            return false
        data.forEachIndexed { i, b ->
            if (b.toInt() != expected[i].code)
                return true
        }
        return false
    }

    fun areBytesResource(data: List<Byte>) = areBytesEquivalentTo(data, PropertyCommonHeaderKeys.RESOURCE)
    fun areBytesStatus(data: List<Byte>) = areBytesEquivalentTo(data, PropertyCommonHeaderKeys.STATUS)

    fun encodeStringToASCII(s: String): String {
        return if (s.all { it.code < 0x80 && !it.isISOControl() })
            s
        else
            s.map { if (it.code < 0x80) it.toString() else "\\u${it.code.toString(16)}" }.joinToString("")
    }
    fun decodeASCIIToString(s: String): String =
        s.split("\\u").mapIndexed { index, e ->
            if (index == 0)
                e
            else
                e.substring(0, 4).toInt(16).toChar() + s.substring(4)
        }.joinToString("")

    // FIXME: implement Mcoded7 and zlib+Mcoded7 conversions
}

object PropertySetAccess {
    const val NONE = "none"
    const val Full = "full"
    const val Partial = "partial"
}

object PropertyResourceFields {
    const val RESOURCE = "resource"
    const val CAN_GET = "canGet"
    const val CAN_SET = "canSet"
    const val CAN_SUBSCRIBE = "canSubscribe"
    const val REQUIRE_RES_ID = "requireResId"
    const val MEDIA_TYPE = "mediaTypes"
    const val ENCODINGS = "encodings"
    const val SCHEMA = "schema"
    const val CAN_PAGINATE = "canPaginate"
    const val COLUMNS = "columns"
}

object PropertyResourceColumnFields {

    const val PROPERTY = "property"
    const val LINK = "link"
    const val TITLE = "title"
}

class PropertyResource() {
    var resource: String = ""
    var canGet: Boolean = true
    var canSet: String = PropertySetAccess.NONE
    var canSubscribe: Boolean = false
    var requireResId: Boolean = false
    var mediaTypes: List<String> = listOf("application/json")
    var encodings: List<String> = listOf("ASCII")
    var schema: Json.JsonValue? = null
    // additional properties for List resources
    var canPaginate: Boolean = false
    var columns: List<PropertyResourceColumn> = listOf()

    constructor(resource: String) : this() { this.resource = resource }

    private fun jsonValuePairs() = sequence {
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
            yield(Pair(Json.JsonValue(PropertyResourceFields.MEDIA_TYPE), Json.JsonValue(mediaTypes.map { s -> Json.JsonValue(s) })))
        if (encodings.size != 1 || encodings[0] != "ASCII")
            yield(Pair(Json.JsonValue(PropertyResourceFields.ENCODINGS), Json.JsonValue(encodings.map { s -> Json.JsonValue(s) })))
        if (schema != null)
            yield(Pair(Json.JsonValue(PropertyResourceFields.SCHEMA), schema!!))
        if (canPaginate)
            yield(Pair(Json.JsonValue(PropertyResourceFields.CAN_PAGINATE), if (canPaginate) Json.TrueValue else Json.FalseValue))
        if (columns.any())
            yield(Pair(Json.JsonValue(PropertyResourceFields.COLUMNS), Json.JsonValue(columns.map { c -> c.toJsonValue() })))
    }

    fun toJsonValue(): Json.JsonValue = Json.JsonValue(
        jsonValuePairs().toMap()
    )
}

class PropertyResourceColumn {
    var title: String = ""
    var property: String? = null
    var link: String? = null

    fun toJsonValue(): Json.JsonValue = Json.JsonValue(
        mapOf(
            if (property != null)
                Pair(Json.JsonValue(PropertyResourceColumnFields.PROPERTY), Json.JsonValue(property ?: ""))
            else
                Pair(Json.JsonValue(PropertyResourceColumnFields.LINK), Json.JsonValue(link ?: "")),
            Pair(Json.JsonValue(PropertyResourceColumnFields.TITLE), Json.JsonValue(title))
        )
    )
}

private val defaultPropertyList = listOf(
    PropertyResource(PropertyResourceNames.DEVICE_INFO),
    PropertyResource(PropertyResourceNames.CHANNEL_LIST),
    PropertyResource(PropertyResourceNames.JSON_SCHEMA)
)

object CommonRulesPropertyHelper {
    fun getPropertyIdentifier(header: List<Byte>): String {
        val json = Json.parse(PropertyCommonConverter.decodeASCIIToString(header.toByteArray().decodeToString()))
        val resId =
            json.token.map.firstNotNullOfOrNull {
                if (Json.getUnescapedString(it.key) == PropertyCommonHeaderKeys.RES_ID)
                    Json.getUnescapedString(it.value)
                else null
            }
        val resource =
            json.token.map.firstNotNullOfOrNull {
                if (Json.getUnescapedString(it.key) == PropertyCommonHeaderKeys.RESOURCE)
                    Json.getUnescapedString(it.value)
                else null
            }
        return resId ?: resource ?: ""
    }

    fun getResourceListRequestJson() = createRequestHeader(PropertyResourceNames.RESOURCE_LIST)

    fun getResourceListRequestBytes(): List<Byte> {
        val json = getResourceListRequestJson()
        val requestASCIIBytes = Json.getEscapedString(Json.serialize(json)).toByteArray().toList()
        return requestASCIIBytes
    }

    fun createRequestHeader(resourceIdentifier: String): Json.JsonValue {
        val headerContent = Pair(
            Json.JsonValue(PropertyCommonHeaderKeys.RESOURCE),
            Json.JsonValue(resourceIdentifier))
        return Json.JsonValue(mapOf(headerContent))
    }

    fun createRequestHeaderBytes(resourceIdentifier: String): List<Byte> {
        val json = createRequestHeader(resourceIdentifier)
        val requestASCIIBytes = Json.getEscapedString(Json.serialize(json)).toByteArray().toList()
        return requestASCIIBytes
    }
}

class CommonRulesPropertyClient(private val muid: Int, private val sendGetPropertyData: (msg: Message.GetPropertyData) -> Unit) : MidiCIPropertyClient {
    override fun createRequestHeader(resourceIdentifier: String): List<Byte> = CommonRulesPropertyHelper.createRequestHeaderBytes(resourceIdentifier)

    override fun getPropertyIdForHeader(header: List<Byte>) = CommonRulesPropertyHelper.getPropertyIdentifier(header)

    override fun getPropertyList(): List<PropertyResource> = resourceList

    override suspend fun requestPropertyList(destinationMUID: Int, requestId: Byte) =
        requestResourceList(destinationMUID, requestId)

    override fun onGetPropertyDataReply(request: Message.GetPropertyData, reply: Message.GetPropertyDataReply) {
        // If the reply message is about ResourceList, then store the list internally.
        val list = getPropertyListForMessage(request, reply) ?: return
        resourceList.clear()
        resourceList.addAll(list)
        propertyCatalogUpdated.forEach { it() }
    }

    private fun getReplyHeaderField(header: List<Byte>, field: String): Json.JsonValue? {
        if (header.isEmpty())
            return null
        val replyString = Json.getUnescapedString(header.toByteArray().decodeToString())
        val replyJson = Json.parse(replyString)
        val valuePair = replyJson.token.map.toList().firstOrNull { Json.getUnescapedString(it.first) == field } ?: return null
        return valuePair.second
    }

    override fun getReplyStatusFor(header: List<Byte>): Int? {
        val json = getReplyHeaderField(header, PropertyCommonHeaderKeys.STATUS)
        return if (json == null) null
            else if (json.token.type == Json.TokenType.Number) json.token.number.toInt()
            else null
    }

    override fun getMediaTypeFor(replyHeader: List<Byte>): String? {
        val json = getReplyHeaderField(replyHeader, PropertyCommonHeaderKeys.MEDIA_TYPE)
        return if (json == null) null
        else if (json.token.type == Json.TokenType.String) Json.getUnescapedString(json)
        else null
    }

    override val propertyCatalogUpdated = mutableListOf<() -> Unit>()

    // implementation

    private val resourceList = mutableListOf<PropertyResource>()

    private fun requestResourceList(destinationMUID: Int, requestId: Byte) {
        val requestASCIIBytes = CommonRulesPropertyHelper.getResourceListRequestBytes()
        val msg = Message.GetPropertyData(muid, destinationMUID, requestId, requestASCIIBytes)
        sendGetPropertyData(msg)
    }

    private fun getPropertyListForMessage(request: Message.GetPropertyData, reply: Message.GetPropertyDataReply): List<PropertyResource>? {
        val id = getPropertyIdForHeader(request.header)
        return if (id == PropertyResourceNames.RESOURCE_LIST) getResourceListForBody(reply.body) else null
    }

    private fun getResourceListForBody(body: List<Byte>): List<PropertyResource> {
        val json = Json.parse(Json.getUnescapedString(body.toByteArray().decodeToString()))
        return getResourceListForBody(json)
    }

    private fun getResourceListForBody(body: Json.JsonValue): List<PropertyResource> {
        // list of entries (name: value sich as {"resource": "foo", "canSet": "partial"})
        val list = body.token.seq.toList()
        return list.map { entry ->
            val pairs = entry.token.map.toList()
            val res = PropertyResource()
            entry.token.map.forEach {
                val v = it.value
                when (Json.getUnescapedString(it.key)) {
                    PropertyResourceFields.RESOURCE -> res.resource = Json.getUnescapedString(v)
                    PropertyResourceFields.CAN_GET -> res.canGet = v.token.type == Json.TokenType.True
                    PropertyResourceFields.CAN_SET -> res.canSet = Json.getUnescapedString(v)
                    PropertyResourceFields.CAN_SUBSCRIBE -> res.canSubscribe = v.token.type == Json.TokenType.True
                    PropertyResourceFields.REQUIRE_RES_ID -> res.requireResId = v.token.type == Json.TokenType.True
                    PropertyResourceFields.ENCODINGS -> res.encodings = v.token.seq.map { e -> Json.getUnescapedString(e) }.toList()
                    PropertyResourceFields.MEDIA_TYPE -> res.mediaTypes = v.token.seq.map { e -> Json.getUnescapedString(e) }.toList()
                    PropertyResourceFields.SCHEMA -> res.schema = v
                    PropertyResourceFields.CAN_PAGINATE -> res.canPaginate = v.token.type == Json.TokenType.True
                    PropertyResourceFields.COLUMNS -> res.columns = v.token.seq.map { c ->
                        val col = PropertyResourceColumn()
                        c.token.map.forEach {
                            val cv = it.value
                            when (Json.getUnescapedString(it.key)) {
                                PropertyResourceColumnFields.PROPERTY -> col.property = Json.getUnescapedString(cv)
                                PropertyResourceColumnFields.LINK -> col.link = Json.getUnescapedString(cv)
                                PropertyResourceColumnFields.TITLE -> col.title = Json.getUnescapedString(cv)
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

class CommonRulesPropertyService(private val muid: Int, private val deviceInfo: MidiCIDeviceInfo,
                                 private val propertyList: MutableList<PropertyResource> = mutableListOf<PropertyResource>().apply { addAll(defaultPropertyList) })
    : MidiCIPropertyService {

    // MidiCIPropertyService implementation
    override fun getPropertyIdForHeader(header: List<Byte>) = CommonRulesPropertyHelper.getPropertyIdentifier(header)

    override fun getPropertyData(msg: Message.GetPropertyData) : Message.GetPropertyDataReply {
        val jsonInquiry = Json.parse(PropertyCommonConverter.decodeASCIIToString(msg.header.toByteArray().decodeToString()))

        val result = getPropertyData(jsonInquiry)

        val replyHeader = PropertyCommonConverter.encodeStringToASCII(Json.serialize(result.first)).toByteArray().toList()
        val replyBody = PropertyCommonConverter.encodeStringToASCII(Json.serialize(result.second)).toByteArray().toList()
        return Message.GetPropertyDataReply(muid, msg.sourceMUID, msg.requestId, replyHeader, 1, 1, replyBody)
    }
    override fun setPropertyData(msg: Message.SetPropertyData) : Message.SetPropertyDataReply {
        val jsonInquiryHeader = Json.parse(PropertyCommonConverter.decodeASCIIToString(msg.header.toByteArray().decodeToString()))
        val jsonInquiryBody = Json.parse(PropertyCommonConverter.decodeASCIIToString(msg.body.toByteArray().decodeToString()))

        val result = setPropertyData(jsonInquiryHeader, jsonInquiryBody)

        val replyHeader = PropertyCommonConverter.encodeStringToASCII(Json.serialize(result)).toByteArray().toList()
        return Message.SetPropertyDataReply(muid, msg.sourceMUID, msg.requestId, replyHeader)
    }

    override fun subscribeProperty(msg: Message.SubscribeProperty): Message.SubscribePropertyReply {
        val jsonHeader = Json.parse(PropertyCommonConverter.decodeASCIIToString(msg.header.toByteArray().decodeToString()))
        // body is ignored in PropertyCommonRules.

        val result = subscribe(msg.sourceMUID, jsonHeader)

        val replyHeader = PropertyCommonConverter.encodeStringToASCII(Json.serialize(result.first)).toByteArray().toList()
        val replyBody = PropertyCommonConverter.encodeStringToASCII(Json.serialize(result.second)).toByteArray().toList()
        return Message.SubscribePropertyReply(muid, msg.sourceMUID, msg.requestId, replyHeader, replyBody)
    }

    // impl

    data class SubscriptionEntry(val resource: String, val muid: Int)

    val linkedResources = mutableMapOf<String, Json.JsonValue>()
    private val values = mutableMapOf<String, Json.JsonValue>()
    private val subscriptions = mutableListOf<SubscriptionEntry>()

    private fun getPropertyString(json: Json.JsonValue, key: String): String? {
        val ret = json.token.map.firstNotNullOfOrNull {
            if (Json.getUnescapedString(it.key) == key) it.value else null
        }
        return if (ret != null) Json.getUnescapedString(ret) else null
    }

    private fun bytesToJsonArray(list: List<Byte>) = list.map { Json.JsonValue(it.toDouble()) }
    private fun getDeviceInfoJson() = Json.JsonValue(mapOf(
        Pair(Json.JsonValue(DeviceInfoPropertyNames.MANUFACTURER_ID), Json.JsonValue(bytesToJsonArray(deviceInfo.manufacturerIdBytes()))),
        Pair(Json.JsonValue(DeviceInfoPropertyNames.FAMILY_ID), Json.JsonValue(bytesToJsonArray(deviceInfo.familyIdBytes()))),
        Pair(Json.JsonValue(DeviceInfoPropertyNames.MODEL_ID), Json.JsonValue(bytesToJsonArray(deviceInfo.modelIdBytes()))),
        Pair(Json.JsonValue(DeviceInfoPropertyNames.VERSION_ID), Json.JsonValue(bytesToJsonArray(deviceInfo.versionIdBytes()))),
        Pair(Json.JsonValue(DeviceInfoPropertyNames.MANUFACTURER), Json.JsonValue(deviceInfo.manufacturer)),
        Pair(Json.JsonValue(DeviceInfoPropertyNames.FAMILY), Json.JsonValue(deviceInfo.family)),
        Pair(Json.JsonValue(DeviceInfoPropertyNames.MODEL), Json.JsonValue(deviceInfo.model)),
        Pair(Json.JsonValue(DeviceInfoPropertyNames.VERSION), Json.JsonValue(deviceInfo.version)),
    ) + if (deviceInfo.serialNumber != null) mapOf(
        Pair(Json.JsonValue(DeviceInfoPropertyNames.SERIAL_NUMBER), Json.JsonValue(deviceInfo.serialNumber)),
    ) else mapOf())

    private fun getPropertyHeader(json: Json.JsonValue) =
        PropertyCommonRequestHeader(
            getPropertyString(json, PropertyCommonHeaderKeys.RESOURCE) ?: "",
            getPropertyString(json, PropertyCommonHeaderKeys.RES_ID),
            getPropertyString(json, PropertyCommonHeaderKeys.MUTUAL_ENCODING),
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

    fun getPropertyData(headerJson: Json.JsonValue): Pair<Json.JsonValue,Json.JsonValue> {
        val header = getPropertyHeader(headerJson)
        val body = when(header.resource) {
            PropertyResourceNames.RESOURCE_LIST -> Json.JsonValue(propertyList.map { it.toJsonValue() })
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
