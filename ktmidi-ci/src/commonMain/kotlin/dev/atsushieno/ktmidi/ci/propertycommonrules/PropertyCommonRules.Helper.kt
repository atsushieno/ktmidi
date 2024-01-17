package dev.atsushieno.ktmidi.ci.propertycommonrules

import dev.atsushieno.ktmidi.ci.Logger
import dev.atsushieno.ktmidi.ci.MidiCIConverter
import dev.atsushieno.ktmidi.ci.MidiCISubscriptionCommand
import dev.atsushieno.ktmidi.ci.json.Json
import dev.atsushieno.ktmidi.ci.json.JsonParserException
import dev.atsushieno.ktmidi.ci.toASCIIByteArray
import kotlin.random.Random

abstract class CommonRulesPropertyHelper(protected val logger: Logger) {

    companion object {
        fun generateRandomSubscribeId() = Random.nextInt().toString(16)
    }

    fun getPropertyIdentifierInternal(header: List<Byte>): String {
        val json = try {
            Json.parse(MidiCIConverter.decodeASCIIToString(header.toByteArray().decodeToString()))
        } catch (ex: JsonParserException) {
            logger.logError(ex.message ?: "Failed to parse property header JSON")
            return ""
        }
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
        val result = resId ?: resource ?: ""
        if (result.isEmpty())
            logger.logError("The property header JSON does not indicate property ID via `resource` or `resId` field")
        return result
    }

    fun getResourceListRequestJson() = createRequestHeaderInternal(PropertyResourceNames.RESOURCE_LIST, null, false)

    fun getResourceListRequestBytes(): List<Byte> {
        val json = getResourceListRequestJson()
        val requestASCIIBytes = Json.getEscapedString(Json.serialize(json)).toASCIIByteArray().toList()
        return requestASCIIBytes
    }

    private val partialSetPair = Pair(Json.JsonValue(PropertyCommonHeaderKeys.SET_PARTIAL), Json.TrueValue)
    fun createRequestHeaderInternal(resourceIdentifier: String, encoding: String?, isPartialSet: Boolean): Json.JsonValue {
        val list = mutableListOf<Pair<Json.JsonValue,Json.JsonValue>>()
        list.add(Pair(
            Json.JsonValue(PropertyCommonHeaderKeys.RESOURCE),
            Json.JsonValue(resourceIdentifier)
        ))
        if (encoding != null)
            list.add(Pair(Json.JsonValue(PropertyCommonHeaderKeys.MUTUAL_ENCODING), Json.JsonValue(encoding)))
        if (isPartialSet)
            list.add(partialSetPair)
        return Json.JsonValue(list.toMap())
    }

    fun createRequestHeaderBytes(resourceIdentifier: String, encoding: String?, isPartialSet: Boolean): List<Byte> {
        val json = createRequestHeaderInternal(resourceIdentifier, encoding, isPartialSet)
        val requestASCIIBytes = Json.getEscapedString(Json.serialize(json)).toASCIIByteArray().toList()
        return requestASCIIBytes
    }

    internal fun createSubscribeHeaderInternal(resourceIdentifier: String, command: String, mutualEncoding: String?): Json.JsonValue {
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
        val json = createSubscribeHeaderInternal(resourceIdentifier, command, mutualEncoding)
        val requestASCIIBytes = Json.getEscapedString(Json.serialize(json)).toASCIIByteArray().toList()
        return requestASCIIBytes
    }

    fun getReplyHeaderField(header: List<Byte>, field: String): Json.JsonValue? {
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

    fun getReplyStatusForInternal(header: List<Byte>): Int? {
        val json = getReplyHeaderField(header, PropertyCommonHeaderKeys.STATUS)
        return if (json == null) null
        else if (json.token.type == Json.TokenType.Number) json.token.number.toInt()
        else null
    }

    fun getMediaTypeForInternal(replyHeader: List<Byte>): String {
        val defaultMimeType = CommonRulesKnownMimeTypes.APPLICATION_JSON
        val json = getReplyHeaderField(replyHeader, PropertyCommonHeaderKeys.MEDIA_TYPE)
        return if (json == null) defaultMimeType
        else if (json.token.type == Json.TokenType.String) json.stringValue
        else defaultMimeType
    }

    fun getEncodingForInternal(header: List<Byte>): String {
        val defaultEncoding = PropertyDataEncoding.ASCII
        val json = getReplyHeaderField(header, PropertyCommonHeaderKeys.MUTUAL_ENCODING)
        return if (json == null) defaultEncoding
        else if (json.token.type == Json.TokenType.String) json.stringValue
        else defaultEncoding
    }

    fun createUpdateNotificationHeader(subscribeId: String, command: String): Json.JsonValue {
        val resource = Pair(
            Json.JsonValue(PropertyCommonHeaderKeys.SUBSCRIBE_ID),
            Json.JsonValue(subscribeId)
        )
        val command = Pair(
            Json.JsonValue(PropertyCommonHeaderKeys.COMMAND),
            Json.JsonValue(command)
        )
        return Json.JsonValue(mapOf(resource, command))

    }
    fun createUpdateNotificationHeaderBytes(subscribeId: String, command: String): List<Byte> {
        val json = createUpdateNotificationHeader(subscribeId, command)
        val requestASCIIBytes = Json.getEscapedString(Json.serialize(json)).toASCIIByteArray().toList()
        return requestASCIIBytes
    }
}

object PropertyPartialUpdater {
    fun parseJsonPointer(s: String): List<String> =
        if (s.isNotEmpty() && s[0] == '/')
            s.substring(1).split('/').map { it.replace("~1", "/").replace("~0", "~") }
        else listOf()

    fun applyPartialUpdate(obj: Json.JsonValue, path: String, value: Json.JsonValue): Json.JsonValue =
        applyPartialUpdate(obj, parseJsonPointer(path), value)

    fun applyPartialUpdate(obj: Json.JsonValue, jsonPointerPath: List<String>, value: Json.JsonValue): Json.JsonValue =
        patch(obj, jsonPointerPath, value)

    private fun patch(obj: Json.JsonValue, path: List<String>, value: Json.JsonValue): Json.JsonValue {
        val entry = path.firstOrNull() ?: return obj // path is empty
        if (obj.token.type != Json.TokenType.Object) // obj is not an object
            return obj

        val contextKey = obj.objectValue.keys.firstOrNull { it.stringValue == entry }
            ?: return obj // specified path entry does not match
        // replace context entry with new object which might be recursively alter the content with the new value
        val replacement = if (path.size == 1) value else patch(obj.objectValue[contextKey]!!, path.drop(1), value)
        val newMap = obj.objectValue.keys.map {
            Pair(it, if (it == contextKey) replacement else obj.objectValue[it]!!)
        }
        return Json.JsonValue(newMap.toMap())
    }

    fun applyPartialUpdates(existingJson: Json.JsonValue, partialSpecJson: Json.JsonValue): Pair<Boolean, Json.JsonValue> {
        val failureReturn = Pair(false, existingJson)
        if (partialSpecJson.token.type != Json.TokenType.Object)
            return failureReturn // context should be JSON object

        var target = existingJson
        // apply all partial updates
        partialSpecJson.objectValue.keys.forEach {  key ->
            val path = if (key.token.type == Json.TokenType.String) key.stringValue else return Pair(false, existingJson)
            val newValue = partialSpecJson.objectValue[key]!!
            target = applyPartialUpdate(target, path, newValue)
        }
        return Pair(true, target)
    }
}