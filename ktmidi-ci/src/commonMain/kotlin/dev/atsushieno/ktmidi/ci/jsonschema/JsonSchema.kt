package dev.atsushieno.ktmidi.ci.jsonschema

import dev.atsushieno.ktmidi.ci.json.Json
import kotlinx.serialization.Serializable

@Serializable
data class JsonSchema(
    val schema: String? = null,
    val id: String? = null,
    val type: JsonSchemaType? = null,
    val properties: Map<String, JsonSchema>? = null,
    val required: List<String>? = null,
    val items: JsonSchema? = null,
    val enum: List<String>? = null,
    val const: String? = null,
    val format: String? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val exclusiveMinimum: Double? = null,
    val exclusiveMaximum: Double? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val pattern: String? = null,
    val minItems: Int? = null,
    val maxItems: Int? = null,
    val uniqueItems: Boolean? = null,
    val minProperties: Int? = null,
    val maxProperties: Int? = null,
    val additionalProperties: JsonSchema? = null,
    val title: String? = null,
    val description: String? = null,
    val default: String? = null,
    val examples: List<String>? = null
) {
    companion object {
        fun parse(jsonValue: Json.JsonValue): JsonSchema {
            if (jsonValue.token.type != Json.TokenType.Object) {
                throw JsonSchemaException("Schema must be a JSON object")
            }

            val obj = jsonValue.objectValue
            return JsonSchema(
                schema = obj[Json.JsonValue("\$schema")]?.stringValue,
                id = obj[Json.JsonValue("\$id")]?.stringValue,
                type = obj[Json.JsonValue("type")]?.stringValue?.let { parseType(it) },
                properties = obj[Json.JsonValue("properties")]?.let { parseProperties(it) },
                required = obj[Json.JsonValue("required")]?.let { parseStringArray(it) },
                items = obj[Json.JsonValue("items")]?.let { parse(it) },
                enum = obj[Json.JsonValue("enum")]?.let { parseStringArray(it) },
                const = obj[Json.JsonValue("const")]?.stringValue,
                format = obj[Json.JsonValue("format")]?.stringValue,
                minimum = obj[Json.JsonValue("minimum")]?.numberValue?.toDouble(),
                maximum = obj[Json.JsonValue("maximum")]?.numberValue?.toDouble(),
                exclusiveMinimum = obj[Json.JsonValue("exclusiveMinimum")]?.numberValue?.toDouble(),
                exclusiveMaximum = obj[Json.JsonValue("exclusiveMaximum")]?.numberValue?.toDouble(),
                minLength = obj[Json.JsonValue("minLength")]?.numberValue?.toInt(),
                maxLength = obj[Json.JsonValue("maxLength")]?.numberValue?.toInt(),
                pattern = obj[Json.JsonValue("pattern")]?.stringValue,
                minItems = obj[Json.JsonValue("minItems")]?.numberValue?.toInt(),
                maxItems = obj[Json.JsonValue("maxItems")]?.numberValue?.toInt(),
                uniqueItems = obj[Json.JsonValue("uniqueItems")]?.let { it.isBooleanTrue },
                minProperties = obj[Json.JsonValue("minProperties")]?.numberValue?.toInt(),
                maxProperties = obj[Json.JsonValue("maxProperties")]?.numberValue?.toInt(),
                additionalProperties = obj[Json.JsonValue("additionalProperties")]?.let { parse(it) },
                title = obj[Json.JsonValue("title")]?.stringValue,
                description = obj[Json.JsonValue("description")]?.stringValue,
                default = obj[Json.JsonValue("default")]?.stringValue,
                examples = obj[Json.JsonValue("examples")]?.let { parseStringArray(it) }
            )
        }
        
        fun parse(jsonString: String): JsonSchema {
            return parse(Json.parse(jsonString))
        }

        private fun parseType(typeString: String): JsonSchemaType? {
            return when (typeString) {
                "null" -> JsonSchemaType.NULL
                "boolean" -> JsonSchemaType.BOOLEAN
                "object" -> JsonSchemaType.OBJECT
                "array" -> JsonSchemaType.ARRAY
                "number" -> JsonSchemaType.NUMBER
                "string" -> JsonSchemaType.STRING
                "integer" -> JsonSchemaType.INTEGER
                else -> null
            }
        }

        private fun parseProperties(jsonValue: Json.JsonValue): Map<String, JsonSchema>? {
            if (jsonValue.token.type != Json.TokenType.Object) return null
            return jsonValue.objectValue.mapKeys { it.key.stringValue }.mapValues { parse(it.value) }
        }

        private fun parseStringArray(jsonValue: Json.JsonValue): List<String>? {
            if (jsonValue.token.type != Json.TokenType.Array) return null
            return jsonValue.arrayValue.map { it.stringValue }.toList()
        }
    }
}

enum class JsonSchemaType {
    NULL, BOOLEAN, OBJECT, ARRAY, NUMBER, STRING, INTEGER
}
