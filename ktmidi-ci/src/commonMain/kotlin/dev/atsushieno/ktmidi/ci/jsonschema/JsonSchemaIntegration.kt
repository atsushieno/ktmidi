package dev.atsushieno.ktmidi.ci.jsonschema

import dev.atsushieno.ktmidi.ci.json.Json
import dev.atsushieno.ktmidi.ci.MidiCIDeviceConfiguration

object JsonSchemaIntegration {
    fun validateDeviceConfiguration(config: MidiCIDeviceConfiguration, data: Json.JsonValue): ValidationResult {
        if (config.jsonSchemaString.isEmpty()) {
            return ValidationResult(true, emptyList())
        }
        
        return try {
            val schema = JsonSchema.parse(config.jsonSchemaString)
            val validator = JsonSchemaValidator()
            validator.validate(schema, data)
        } catch (e: Exception) {
            ValidationResult(false, listOf(ValidationError("Schema parsing failed: ${e.message}", "", "")))
        }
    }

    fun validatePropertyData(schemaString: String, data: Json.JsonValue): ValidationResult {
        if (schemaString.isEmpty()) {
            return ValidationResult(true, emptyList())
        }
        
        return try {
            val schema = JsonSchema.parse(schemaString)
            val validator = JsonSchemaValidator()
            validator.validate(schema, data)
        } catch (e: Exception) {
            ValidationResult(false, listOf(ValidationError("Schema parsing failed: ${e.message}", "", "")))
        }
    }
}
