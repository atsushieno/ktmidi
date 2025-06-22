package dev.atsushieno.ktmidi.ci.jsonschema

import dev.atsushieno.ktmidi.ci.json.Json
import kotlin.math.floor

class JsonSchemaValidator {
    fun validate(schema: JsonSchema, instance: Json.JsonValue): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        validateRecursive(schema, instance, "", "", errors)
        return ValidationResult(errors.isEmpty(), errors)
    }
    
    private fun validateRecursive(
        schema: JsonSchema, 
        instance: Json.JsonValue, 
        schemaPath: String, 
        instancePath: String, 
        errors: MutableList<ValidationError>
    ) {
        validateType(schema, instance, schemaPath, instancePath, errors)
        validateProperties(schema, instance, schemaPath, instancePath, errors)
        validateRequired(schema, instance, schemaPath, instancePath, errors)
        validateItems(schema, instance, schemaPath, instancePath, errors)
        validateEnum(schema, instance, schemaPath, instancePath, errors)
        validateConst(schema, instance, schemaPath, instancePath, errors)
        validateNumericConstraints(schema, instance, schemaPath, instancePath, errors)
        validateStringConstraints(schema, instance, schemaPath, instancePath, errors)
        validateArrayConstraints(schema, instance, schemaPath, instancePath, errors)
        validateObjectConstraints(schema, instance, schemaPath, instancePath, errors)
    }

    private fun validateType(
        schema: JsonSchema,
        instance: Json.JsonValue,
        schemaPath: String,
        instancePath: String,
        errors: MutableList<ValidationError>
    ) {
        val expectedType = schema.type ?: return
        val actualType = getJsonValueType(instance)
        
        if (expectedType == JsonSchemaType.INTEGER && actualType == JsonSchemaType.NUMBER) {
            val num = instance.numberValue.toDouble()
            if (num != floor(num)) {
                errors.add(ValidationError("Expected integer, got non-integer number", "$schemaPath/type", instancePath))
            }
            return
        }
        
        if (expectedType != actualType) {
            errors.add(ValidationError("Expected $expectedType, got $actualType", "$schemaPath/type", instancePath))
        }
    }

    private fun validateProperties(
        schema: JsonSchema,
        instance: Json.JsonValue,
        schemaPath: String,
        instancePath: String,
        errors: MutableList<ValidationError>
    ) {
        val properties = schema.properties ?: return
        if (instance.token.type != Json.TokenType.Object) return

        val instanceObj = instance.objectValue
        for ((propName, propSchema) in properties) {
            val propValue = instanceObj[Json.JsonValue(propName)]
            if (propValue != null) {
                validateRecursive(propSchema, propValue, "$schemaPath/properties/$propName", "$instancePath/$propName", errors)
            }
        }
    }

    private fun validateRequired(
        schema: JsonSchema,
        instance: Json.JsonValue,
        schemaPath: String,
        instancePath: String,
        errors: MutableList<ValidationError>
    ) {
        val required = schema.required ?: return
        if (instance.token.type != Json.TokenType.Object) return

        val instanceObj = instance.objectValue
        val instanceKeys = instanceObj.keys.map { it.stringValue }.toSet()
        
        for (requiredProp in required) {
            if (!instanceKeys.contains(requiredProp)) {
                errors.add(ValidationError("Missing required property '$requiredProp'", "$schemaPath/required", instancePath))
            }
        }
    }

    private fun validateItems(
        schema: JsonSchema,
        instance: Json.JsonValue,
        schemaPath: String,
        instancePath: String,
        errors: MutableList<ValidationError>
    ) {
        val itemsSchema = schema.items ?: return
        if (instance.token.type != Json.TokenType.Array) return

        instance.arrayValue.forEachIndexed { index, item ->
            validateRecursive(itemsSchema, item, "$schemaPath/items", "$instancePath/$index", errors)
        }
    }

    private fun validateEnum(
        schema: JsonSchema,
        instance: Json.JsonValue,
        schemaPath: String,
        instancePath: String,
        errors: MutableList<ValidationError>
    ) {
        val enumValues = schema.enum ?: return
        val instanceValue = getInstanceValueAsString(instance)
        
        if (!enumValues.contains(instanceValue)) {
            errors.add(ValidationError("Value '$instanceValue' is not in enum: ${enumValues.joinToString(", ")}", "$schemaPath/enum", instancePath))
        }
    }

    private fun validateConst(
        schema: JsonSchema,
        instance: Json.JsonValue,
        schemaPath: String,
        instancePath: String,
        errors: MutableList<ValidationError>
    ) {
        val constValue = schema.const ?: return
        val instanceValue = getInstanceValueAsString(instance)
        
        if (constValue != instanceValue) {
            errors.add(ValidationError("Value '$instanceValue' does not match const '$constValue'", "$schemaPath/const", instancePath))
        }
    }

    private fun validateNumericConstraints(
        schema: JsonSchema,
        instance: Json.JsonValue,
        schemaPath: String,
        instancePath: String,
        errors: MutableList<ValidationError>
    ) {
        if (instance.token.type != Json.TokenType.Number) return
        val value = instance.numberValue.toDouble()

        schema.minimum?.let { min ->
            if (value < min) {
                errors.add(ValidationError("Value $value is less than minimum $min", "$schemaPath/minimum", instancePath))
            }
        }

        schema.maximum?.let { max ->
            if (value > max) {
                errors.add(ValidationError("Value $value is greater than maximum $max", "$schemaPath/maximum", instancePath))
            }
        }

        schema.exclusiveMinimum?.let { min ->
            if (value <= min) {
                errors.add(ValidationError("Value $value is not greater than exclusive minimum $min", "$schemaPath/exclusiveMinimum", instancePath))
            }
        }

        schema.exclusiveMaximum?.let { max ->
            if (value >= max) {
                errors.add(ValidationError("Value $value is not less than exclusive maximum $max", "$schemaPath/exclusiveMaximum", instancePath))
            }
        }
    }

    private fun validateStringConstraints(
        schema: JsonSchema,
        instance: Json.JsonValue,
        schemaPath: String,
        instancePath: String,
        errors: MutableList<ValidationError>
    ) {
        if (instance.token.type != Json.TokenType.String) return
        val value = instance.stringValue

        schema.minLength?.let { min ->
            if (value.length < min) {
                errors.add(ValidationError("String length ${value.length} is less than minimum $min", "$schemaPath/minLength", instancePath))
            }
        }

        schema.maxLength?.let { max ->
            if (value.length > max) {
                errors.add(ValidationError("String length ${value.length} is greater than maximum $max", "$schemaPath/maxLength", instancePath))
            }
        }

        schema.pattern?.let { pattern ->
            val regex = Regex(pattern)
            if (!regex.matches(value)) {
                errors.add(ValidationError("String '$value' does not match pattern '$pattern'", "$schemaPath/pattern", instancePath))
            }
        }
    }

    private fun validateArrayConstraints(
        schema: JsonSchema,
        instance: Json.JsonValue,
        schemaPath: String,
        instancePath: String,
        errors: MutableList<ValidationError>
    ) {
        if (instance.token.type != Json.TokenType.Array) return
        val items = instance.arrayValue.toList()

        schema.minItems?.let { min ->
            if (items.size < min) {
                errors.add(ValidationError("Array length ${items.size} is less than minimum $min", "$schemaPath/minItems", instancePath))
            }
        }

        schema.maxItems?.let { max ->
            if (items.size > max) {
                errors.add(ValidationError("Array length ${items.size} is greater than maximum $max", "$schemaPath/maxItems", instancePath))
            }
        }

        if (schema.uniqueItems == true) {
            val uniqueItems = items.map { getInstanceValueAsString(it) }.toSet()
            if (uniqueItems.size != items.size) {
                errors.add(ValidationError("Array contains duplicate items", "$schemaPath/uniqueItems", instancePath))
            }
        }
    }

    private fun validateObjectConstraints(
        schema: JsonSchema,
        instance: Json.JsonValue,
        schemaPath: String,
        instancePath: String,
        errors: MutableList<ValidationError>
    ) {
        if (instance.token.type != Json.TokenType.Object) return
        val properties = instance.objectValue

        schema.minProperties?.let { min ->
            if (properties.size < min) {
                errors.add(ValidationError("Object has ${properties.size} properties, minimum is $min", "$schemaPath/minProperties", instancePath))
            }
        }

        schema.maxProperties?.let { max ->
            if (properties.size > max) {
                errors.add(ValidationError("Object has ${properties.size} properties, maximum is $max", "$schemaPath/maxProperties", instancePath))
            }
        }
    }

    private fun getJsonValueType(value: Json.JsonValue): JsonSchemaType {
        return when (value.token.type) {
            Json.TokenType.Null -> JsonSchemaType.NULL
            Json.TokenType.True, Json.TokenType.False -> JsonSchemaType.BOOLEAN
            Json.TokenType.Number -> JsonSchemaType.NUMBER
            Json.TokenType.String -> JsonSchemaType.STRING
            Json.TokenType.Array -> JsonSchemaType.ARRAY
            Json.TokenType.Object -> JsonSchemaType.OBJECT
        }
    }

    private fun getInstanceValueAsString(value: Json.JsonValue): String {
        return when (value.token.type) {
            Json.TokenType.String -> value.stringValue
            Json.TokenType.Number -> value.numberValue.toString()
            Json.TokenType.True -> "true"
            Json.TokenType.False -> "false"
            Json.TokenType.Null -> "null"
            else -> Json.serialize(value)
        }
    }
}

data class ValidationResult(val isValid: Boolean, val errors: List<ValidationError>)
data class ValidationError(val message: String, val schemaPath: String, val instancePath: String)
