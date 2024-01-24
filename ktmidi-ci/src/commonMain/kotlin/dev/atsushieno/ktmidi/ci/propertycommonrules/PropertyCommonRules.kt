package dev.atsushieno.ktmidi.ci.propertycommonrules

import dev.atsushieno.ktmidi.ci.json.Json
import dev.atsushieno.ktmidi.ci.shl
import dev.atsushieno.ktmidi.ci.shr
import kotlinx.serialization.Serializable
import kotlin.experimental.and
import kotlin.math.min

class PropertyExchangeException(message: String = "Property Exchange exception", innerException: Exception? = null) : Exception(message, innerException)

object PropertyCommonHeaderKeys {
    const val RESOURCE = "resource"
    const val RES_ID = "resId"
    const val MUTUAL_ENCODING = "mutualEncoding"
    const val STATUS = "status"
    const val MESSAGE = "message"
    const val CACHE_TIME = "cacheTime"
    // M2-103-UM 5.5 Extra Header Property for Using Property Data which is Not JSON Data
    const val MEDIA_TYPE = "mediaType"
    // M2-103-UM 6.6.2 Pagination
    const val OFFSET = "offset"
    const val LIMIT = "limit"
    const val TOTAL_COUNT = "totalCount"
    // M2-103-UM 8. Full and Partial SET Inquiries
    const val SET_PARTIAL = "setPartial"
    // M2-103-UM 9. Subscribing to Property Data
    const val COMMAND = "command"
    // M2-103-UM 9.1 Extra Request Header Properties for Subscriptions
    const val SUBSCRIBE_ID = "subscribeId"
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

// required fields are non-null, optionals are nullable
data class PropertyCommonRequestHeader(
    val resource: String,
    val resId: String? = null,
    val mutualEncoding: String? = PropertyDataEncoding.ASCII,
    val mediaType: String? = CommonRulesKnownMimeTypes.APPLICATION_JSON,
    // M2-103-UM 6.6.2 Pagination
    val offset: Int? = null,
    val limit: Int? = null
)

// required fields are non-null, optionals are nullable
data class PropertyCommonReplyHeader(
    val status: Int,
    val message: String? = null,
    val mutualEncoding: String? = PropertyDataEncoding.ASCII,
    val cacheTime: String? = null,
    // M2-103-UM 5.5 Extra Header Property for Using Property Data which is Not JSON Data
    val mediaType: String? = null,
    // M2-103-UM 9.1 Extra Request Header Properties for Subscriptions
    val subscribeId: String? = null,
    // M2-103-UM 6.6.2 Pagination
    val totalCount: Int? = null
)

object PropertyCommonConverter {
    fun encodeToMcoded7(bytes: List<Byte>): List<Byte> {
        val result = mutableListOf<Byte>()
        bytes.chunked(7).forEach { part ->
            result.add(part.mapIndexed { index, it -> ((it shr 7) shl index).toByte() }.sum().toByte())
            result.addAll(part.map { it and 0x7F })
        }
        return result
    }

    fun decodeMcoded7(bytes: List<Byte>): List<Byte> =
        bytes.chunked(8).flatMap { part ->
            (0 until part.size - 1).map {
                (part[it + 1] + ((part[0] and (1 shl (it)).toByte()) shl 7)).toByte()
            }
        }.toList()

    // FIXME: it is not implemented in ALL platforms yet
    //    replace it with these once ktor-utils 3.0.0 is released to all our target platforms
    fun decodeZlib(bytes: ByteArray): List<Byte> =
        //DeflateEncoder.decode(ByteReadChannel(bytes)).toByteArray()
        decodeZlibWorkaround(bytes).toList()

    fun encodeZlib(bytes: ByteArray): List<Byte> =
        //DeflateEncoder.encode(ByteReadChannel(bytes)).toByteArray()
        encodeZlibWorkaround(bytes).toList()

    fun decodeZlibMcoded7(body: List<Byte>) = decodeZlib(decodeMcoded7(body).toByteArray())
    fun encodeToZlibMcoded7(data: List<Byte>) = encodeToMcoded7(encodeZlib(data.toByteArray()).toList())
}

expect fun decodeZlibWorkaround(bytes: ByteArray): ByteArray
expect fun encodeZlibWorkaround(bytes: ByteArray): ByteArray


object PropertySetAccess {
    const val NONE = "none"
    const val FULL = "full"
    const val PARTIAL = "partial"
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

@Serializable
class PropertyResourceColumn {
    var title: String = ""
    var property: String? = null
    var link: String? = null

    override fun equals(other: Any?): Boolean {
        val o = if (other is PropertyResourceColumn) other else return false
        return title == o.title && property == o.property && link == o.link
    }

    override fun hashCode(): Int {
        return title.hashCode() + property.hashCode() + link.hashCode()
    }

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

data class SubscriptionEntry(val resource: String, val muid: Int, val encoding: String?, val subscribeId: String)

