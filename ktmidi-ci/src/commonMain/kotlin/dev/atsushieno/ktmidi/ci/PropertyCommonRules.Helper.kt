package dev.atsushieno.ktmidi.ci

import io.ktor.utils.io.core.*

object CommonRulesPropertyHelper {
    fun getPropertyIdentifier(header: List<Byte>): String {
        // FIXME: log error if JSON parser failed
        val json = Json.parseOrNull(PropertyCommonConverter.decodeASCIIToString(header.toByteArray().decodeToString()))
            ?: return ""
        val resId =
            json.token.map.firstNotNullOfOrNull {
                if (it.key.stringValue == PropertyCommonHeaderKeys.RES_ID)
                    it.value.stringValue
                else null
            }
        val resource =
            json.token.map.firstNotNullOfOrNull {
                if (it.key.stringValue == PropertyCommonHeaderKeys.RESOURCE)
                    it.value.stringValue
                else null
            }
        return resId ?: resource ?: ""
    }

    fun getResourceListRequestJson() = createRequestHeader(PropertyResourceNames.RESOURCE_LIST, false)

    fun getResourceListRequestBytes(): List<Byte> {
        val json = getResourceListRequestJson()
        val requestASCIIBytes = Json.getEscapedString(Json.serialize(json)).toByteArray().toList()
        return requestASCIIBytes
    }

    private val partialSetPair = Pair(Json.JsonValue(PropertyCommonHeaderKeys.SET_PARTIAL), Json.TrueValue)
    fun createRequestHeader(resourceIdentifier: String, isPartialSet: Boolean): Json.JsonValue {
        val headerContent = Pair(
            Json.JsonValue(PropertyCommonHeaderKeys.RESOURCE),
            Json.JsonValue(resourceIdentifier)
        )
        return Json.JsonValue(if (isPartialSet) mapOf(headerContent, partialSetPair) else mapOf(headerContent))
    }

    fun createRequestHeaderBytes(resourceIdentifier: String, isPartialSet: Boolean): List<Byte> {
        val json = createRequestHeader(resourceIdentifier, isPartialSet)
        val requestASCIIBytes = Json.getEscapedString(Json.serialize(json)).toByteArray().toList()
        return requestASCIIBytes
    }

    private fun getReplyHeaderField(header: List<Byte>, field: String): Json.JsonValue? {
        if (header.isEmpty())
            return null
        val replyString = PropertyCommonConverter.decodeASCIIToString(header.toByteArray().decodeToString())
        // FIXME: log error if JSON parser failed
        val replyJson = Json.parseOrNull(replyString) ?: return null
        val valuePair = replyJson.token.map.toList().firstOrNull { it.first.stringValue == field } ?: return null
        return valuePair.second
    }

    fun getReplyStatusFor(header: List<Byte>): Int? {
        val json = getReplyHeaderField(header, PropertyCommonHeaderKeys.STATUS)
        return if (json == null) null
        else if (json.token.type == Json.TokenType.Number) json.token.number.toInt()
        else null
    }

    fun getMediaTypeFor(replyHeader: List<Byte>): String {
        val defaultMimeType = CommonRulesKnownMimeTypes.APPLICATION_JSON
        val json = getReplyHeaderField(replyHeader, PropertyCommonHeaderKeys.MEDIA_TYPE)
        return if (json == null) defaultMimeType
        else if (json.token.type == Json.TokenType.String) json.stringValue
        else defaultMimeType
    }

}