package dev.atsushieno.ktmidi.ci.propertycommonrules

import dev.atsushieno.ktmidi.ci.json.Json

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