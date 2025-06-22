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

            return JsonSchema(
                schema = jsonValue.getObjectValue("\$schema")?.stringValue,
                id = jsonValue.getObjectValue("\$id")?.stringValue,
                type = jsonValue.getObjectValue("type")?.stringValue?.let { parseType(it) },
                properties = jsonValue.getObjectValue("properties")?.let { parseProperties(it) },
                required = jsonValue.getObjectValue("required")?.let { parseStringArray(it) },
                items = jsonValue.getObjectValue("items")?.let { parse(it) },
                enum = jsonValue.getObjectValue("enum")?.let { parseStringArray(it) },
                const = jsonValue.getObjectValue("const")?.stringValue,
                format = jsonValue.getObjectValue("format")?.stringValue,
                minimum = jsonValue.getObjectValue("minimum")?.numberValue?.toDouble(),
                maximum = jsonValue.getObjectValue("maximum")?.numberValue?.toDouble(),
                exclusiveMinimum = jsonValue.getObjectValue("exclusiveMinimum")?.numberValue?.toDouble(),
                exclusiveMaximum = jsonValue.getObjectValue("exclusiveMaximum")?.numberValue?.toDouble(),
                minLength = jsonValue.getObjectValue("minLength")?.numberValue?.toInt(),
                maxLength = jsonValue.getObjectValue("maxLength")?.numberValue?.toInt(),
                pattern = jsonValue.getObjectValue("pattern")?.stringValue,
                minItems = jsonValue.getObjectValue("minItems")?.numberValue?.toInt(),
                maxItems = jsonValue.getObjectValue("maxItems")?.numberValue?.toInt(),
                uniqueItems = jsonValue.getObjectValue("uniqueItems")?.let { it.isBooleanTrue },
                minProperties = jsonValue.getObjectValue("minProperties")?.numberValue?.toInt(),
                maxProperties = jsonValue.getObjectValue("maxProperties")?.numberValue?.toInt(),
                additionalProperties = jsonValue.getObjectValue("additionalProperties")?.let { parse(it) },
                title = jsonValue.getObjectValue("title")?.stringValue,
                description = jsonValue.getObjectValue("description")?.stringValue,
                default = jsonValue.getObjectValue("default")?.stringValue,
                examples = jsonValue.getObjectValue("examples")?.let { parseStringArray(it) }
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
