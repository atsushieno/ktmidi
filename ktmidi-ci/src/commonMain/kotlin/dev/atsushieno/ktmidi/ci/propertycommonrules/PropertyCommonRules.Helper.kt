package dev.atsushieno.ktmidi.ci.propertycommonrules

import dev.atsushieno.ktmidi.ci.MidiCIConverter
import dev.atsushieno.ktmidi.ci.MidiCIDevice
import dev.atsushieno.ktmidi.ci.json.Json
import dev.atsushieno.ktmidi.ci.json.JsonParserException
import dev.atsushieno.ktmidi.ci.toASCIIByteArray

internal class CommonRulesPropertyHelper(device: MidiCIDevice) {
    val logger by device::logger

    private fun getResourceListRequestJson() = createRequestHeader(PropertyResourceNames.RESOURCE_LIST, mapOf())

    fun getResourceListRequestBytes(): List<Byte> {
        val json = getResourceListRequestJson()
        val requestASCIIBytes = Json.getEscapedString(Json.serialize(json)).toASCIIByteArray().toList()
        return requestASCIIBytes
    }

    private val partialSetPair = Pair(Json.JsonValue(PropertyCommonHeaderKeys.SET_PARTIAL), Json.TrueValue)
    private fun createRequestHeader(resourceIdentifier: String, fields: Map<String, Any?>): Json.JsonValue {
        val resId = fields[PropertyCommonHeaderKeys.RES_ID] as String?
        val encoding = fields[PropertyCommonHeaderKeys.MUTUAL_ENCODING] as String?
        val isPartialSet = fields[PropertyCommonHeaderKeys.SET_PARTIAL] as Boolean?
        val paginateOffset = fields[PropertyCommonHeaderKeys.OFFSET] as Int?
        val paginateLimit = fields[PropertyCommonHeaderKeys.LIMIT] as Int?
        val list = mutableListOf<Pair<Json.JsonValue,Json.JsonValue>>()
        list.add(Pair(
            Json.JsonValue(PropertyCommonHeaderKeys.RESOURCE),
            Json.JsonValue(resourceIdentifier)
        ))
        if (resId != null)
            list.add(Pair(Json.JsonValue(PropertyCommonHeaderKeys.RES_ID), Json.JsonValue(resId)))
        if (encoding != null)
            list.add(Pair(Json.JsonValue(PropertyCommonHeaderKeys.MUTUAL_ENCODING), Json.JsonValue(encoding)))
        if (isPartialSet == true)
            list.add(partialSetPair)
        if (paginateOffset != null)
            list.add(Pair(Json.JsonValue(PropertyCommonHeaderKeys.OFFSET), Json.JsonValue(paginateOffset.toDouble())))
        if (paginateLimit != null)
            list.add(Pair(Json.JsonValue(PropertyCommonHeaderKeys.LIMIT), Json.JsonValue(paginateLimit.toDouble())))
        return Json.JsonValue(list.toMap())
    }

    fun createRequestHeaderBytes(resourceIdentifier: String, fields: Map<String, Any?>): List<Byte> {
        val json = createRequestHeader(resourceIdentifier, fields)
        val requestASCIIBytes = Json.getEscapedString(Json.serialize(json)).toASCIIByteArray().toList()
        return requestASCIIBytes
    }

    private fun createSubscribeHeader(resourceIdentifier: String, command: String, mutualEncoding: String?): Json.JsonValue {
        val resource = Pair(
            Json.JsonValue(PropertyCommonHeaderKeys.RESOURCE),
            Json.JsonValue(resourceIdentifier)
        )
        val commandJson = Pair(
            Json.JsonValue(PropertyCommonHeaderKeys.COMMAND),
            Json.JsonValue(command)
        )
        val list = mutableListOf(resource, commandJson)
        if (mutualEncoding != null)
            list.add(Pair(
                Json.JsonValue(PropertyCommonHeaderKeys.MUTUAL_ENCODING),
                Json.JsonValue(mutualEncoding)
            ))
        return Json.JsonValue(list.toMap())
    }

    fun createSubscribeHeaderBytes(resourceIdentifier: String, command: String, mutualEncoding: String?): List<Byte> {
        val json = createSubscribeHeader(resourceIdentifier, command, mutualEncoding)
        val requestASCIIBytes = Json.getEscapedString(Json.serialize(json)).toASCIIByteArray().toList()
        return requestASCIIBytes
    }

    private fun getHeaderFieldJson(header: List<Byte>, field: String): Json.JsonValue? {
        val replyString = MidiCIConverter.decodeASCIIToString(header.toByteArray().decodeToString())
        val replyJson = try {
            Json.parse(replyString)
        } catch (ex: JsonParserException) {
            logger.logError(ex.message ?: "Failed to parse property header JSON")
            return null
        }
        val valuePair = replyJson.token.map.toList().firstOrNull { it.first.stringValue == field } ?: return null
        return valuePair.second
    }

    fun getHeaderFieldInteger(header: List<Byte>, field: String): Int? {
        val json = getHeaderFieldJson(header, field)
        return if (json?.token?.type == Json.TokenType.Number) json.token.number.toInt() else null
    }

    fun getHeaderFieldString(header: List<Byte>, field: String): String? {
        val json = getHeaderFieldJson(header, field)
        return if (json?.token?.type == Json.TokenType.String) json.stringValue else null
    }

    private fun createSubscribePropertyHeader(subscribeId: String, command: String): Json.JsonValue {
        // M2-103-UM_v1.1 section 5.1.1: For subscription messages, the first Property shall be the command.
        val command = Pair(
            Json.JsonValue(PropertyCommonHeaderKeys.COMMAND),
            Json.JsonValue(command)
        )
        val resource = Pair(
            Json.JsonValue(PropertyCommonHeaderKeys.SUBSCRIBE_ID),
            Json.JsonValue(subscribeId)
        )
        return Json.JsonValue(mapOf(resource, command))

    }

    internal fun createSubscribePropertyHeaderBytes(subscribeId: String, command: String): List<Byte> {
        val json = createSubscribePropertyHeader(subscribeId, command)
        val requestASCIIBytes = Json.getEscapedString(Json.serialize(json)).toASCIIByteArray().toList()
        return requestASCIIBytes
    }

    internal fun encodeBody(data: List<Byte>, encoding: String?): List<Byte> {
        return when (encoding) {
            PropertyDataEncoding.ASCII -> data
            PropertyDataEncoding.MCODED7 -> PropertyCommonConverter.encodeToMcoded7(data)
            PropertyDataEncoding.ZLIB_MCODED7 -> PropertyCommonConverter.encodeToZlibMcoded7(data)
            null -> data
            else -> {
                logger.logError("Unrecognized mutualEncoding is specified: $encoding")
                data
            }
        }
    }

    internal fun decodeBody(header: List<Byte>, body: List<Byte>): List<Byte> =
        decodeBody(getHeaderFieldString(header, PropertyCommonHeaderKeys.MUTUAL_ENCODING), body)
    internal fun decodeBody(encoding: String?, body: List<Byte>): List<Byte> {
        return when (encoding) {
            PropertyDataEncoding.ASCII -> body
            PropertyDataEncoding.MCODED7 -> PropertyCommonConverter.decodeMcoded7(body)
            PropertyDataEncoding.ZLIB_MCODED7 -> PropertyCommonConverter.decodeZlibMcoded7(body)
            null -> body
            else -> {
                logger.logError("Unrecognized mutualEncoding is specified: $encoding")
                body
            }
        }
    }
}

