package dev.atsushieno.ktmidi.ci.jsonschema

import dev.atsushieno.ktmidi.ci.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class JsonSchemaTest {
    @Test
    fun testBasicTypeValidation() {
        val schema = JsonSchema(type = JsonSchemaType.STRING)
        val validator = JsonSchemaValidator()
        
        val validResult = validator.validate(schema, Json.JsonValue("test"))
        assertTrue(validResult.isValid, "String should be valid")
        
        val invalidResult = validator.validate(schema, Json.JsonValue(123.0))
        assertFalse(invalidResult.isValid, "Number should be invalid for string type")
        assertEquals(1, invalidResult.errors.size)
    }
    
    @Test
    fun testObjectPropertiesValidation() {
        val schema = JsonSchema(
            type = JsonSchemaType.OBJECT,
            properties = mapOf(
                "name" to JsonSchema(type = JsonSchemaType.STRING),
                "age" to JsonSchema(type = JsonSchemaType.NUMBER)
            )
        )
        val validator = JsonSchemaValidator()
        
        val validObj = Json.parse("""{"name": "John", "age": 30}""")
        val validResult = validator.validate(schema, validObj)
        assertTrue(validResult.isValid, "Valid object should pass validation")
        
        val invalidObj = Json.parse("""{"name": 123, "age": "thirty"}""")
        val invalidResult = validator.validate(schema, invalidObj)
        assertFalse(invalidResult.isValid, "Invalid object should fail validation")
        assertEquals(2, invalidResult.errors.size)
    }
    
    @Test
    fun testArrayItemsValidation() {
        val schema = JsonSchema(
            type = JsonSchemaType.ARRAY,
            items = JsonSchema(type = JsonSchemaType.STRING)
        )
        val validator = JsonSchemaValidator()
        
        val validArray = Json.parse("""["hello", "world"]""")
        val validResult = validator.validate(schema, validArray)
        assertTrue(validResult.isValid, "Valid array should pass validation")
        
        val invalidArray = Json.parse("""["hello", 123]""")
        val invalidResult = validator.validate(schema, invalidArray)
        assertFalse(invalidResult.isValid, "Invalid array should fail validation")
        assertEquals(1, invalidResult.errors.size)
    }
    
    @Test
    fun testNumericConstraints() {
        val schema = JsonSchema(
            type = JsonSchemaType.NUMBER,
            minimum = 0.0,
            maximum = 100.0
        )
        val validator = JsonSchemaValidator()
        
        val validNumber = Json.JsonValue(50.0)
        val validResult = validator.validate(schema, validNumber)
        assertTrue(validResult.isValid, "Number within range should be valid")
        
        val tooSmall = Json.JsonValue(-10.0)
        val tooSmallResult = validator.validate(schema, tooSmall)
        assertFalse(tooSmallResult.isValid, "Number below minimum should be invalid")
        
        val tooBig = Json.JsonValue(150.0)
        val tooBigResult = validator.validate(schema, tooBig)
        assertFalse(tooBigResult.isValid, "Number above maximum should be invalid")
    }
    
    @Test
    fun testStringConstraints() {
        val schema = JsonSchema(
            type = JsonSchemaType.STRING,
            minLength = 3,
            maxLength = 10,
            pattern = "^[a-zA-Z]+$"
        )
        val validator = JsonSchemaValidator()
        
        val validString = Json.JsonValue("hello")
        val validResult = validator.validate(schema, validString)
        assertTrue(validResult.isValid, "Valid string should pass validation")
        
        val tooShort = Json.JsonValue("hi")
        val tooShortResult = validator.validate(schema, tooShort)
        assertFalse(tooShortResult.isValid, "String too short should be invalid")
        
        val tooLong = Json.JsonValue("verylongstring")
        val tooLongResult = validator.validate(schema, tooLong)
        assertFalse(tooLongResult.isValid, "String too long should be invalid")
        
        val invalidPattern = Json.JsonValue("hello123")
        val invalidPatternResult = validator.validate(schema, invalidPattern)
        assertFalse(invalidPatternResult.isValid, "String not matching pattern should be invalid")
    }
    
    @Test
    fun testEnumValidation() {
        val schema = JsonSchema(
            type = JsonSchemaType.STRING,
            enum = listOf("red", "green", "blue")
        )
        val validator = JsonSchemaValidator()
        
        val validEnum = Json.JsonValue("red")
        val validResult = validator.validate(schema, validEnum)
        assertTrue(validResult.isValid, "Valid enum value should pass validation")
        
        val invalidEnum = Json.JsonValue("yellow")
        val invalidResult = validator.validate(schema, invalidEnum)
        assertFalse(invalidResult.isValid, "Invalid enum value should fail validation")
    }
    
    @Test
    fun testRequiredProperties() {
        val schema = JsonSchema(
            type = JsonSchemaType.OBJECT,
            properties = mapOf(
                "name" to JsonSchema(type = JsonSchemaType.STRING),
                "email" to JsonSchema(type = JsonSchemaType.STRING)
            ),
            required = listOf("name")
        )
        val validator = JsonSchemaValidator()
        
        val validObj = Json.parse("""{"name": "John"}""")
        val validResult = validator.validate(schema, validObj)
        assertTrue(validResult.isValid, "Object with required property should be valid")
        
        val invalidObj = Json.parse("""{"email": "john@example.com"}""")
        val invalidResult = validator.validate(schema, invalidObj)
        assertFalse(invalidResult.isValid, "Object missing required property should be invalid")
        assertEquals(1, invalidResult.errors.size)
    }

    @Test
    fun testSchemaParsingFromJson() {
        val schemaJson = """{
            "type": "object",
            "properties": {
                "name": {"type": "string"},
                "age": {"type": "number", "minimum": 0}
            },
            "required": ["name"]
        }"""
        
        val schema = JsonSchema.parse(schemaJson)
        assertEquals(JsonSchemaType.OBJECT, schema.type)
        assertEquals(2, schema.properties?.size)
        assertEquals(listOf("name"), schema.required)
        assertEquals(JsonSchemaType.STRING, schema.properties?.get("name")?.type)
        assertEquals(JsonSchemaType.NUMBER, schema.properties?.get("age")?.type)
        assertEquals(0.0, schema.properties?.get("age")?.minimum)
    }

    @Test
    fun testIntegerTypeValidation() {
        val schema = JsonSchema(type = JsonSchemaType.INTEGER)
        val validator = JsonSchemaValidator()
        
        val validInteger = Json.JsonValue(42.0)
        val validResult = validator.validate(schema, validInteger)
        assertTrue(validResult.isValid, "Integer should be valid")
        
        val invalidInteger = Json.JsonValue(42.5)
        val invalidResult = validator.validate(schema, invalidInteger)
        assertFalse(invalidResult.isValid, "Non-integer number should be invalid for integer type")
    }
}
