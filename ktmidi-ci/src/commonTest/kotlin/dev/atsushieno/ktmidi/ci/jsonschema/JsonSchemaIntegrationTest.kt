package dev.atsushieno.ktmidi.ci.jsonschema

import dev.atsushieno.ktmidi.ci.json.Json
import dev.atsushieno.ktmidi.ci.MidiCIDeviceConfiguration
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class JsonSchemaIntegrationTest {
    @Test
    fun testDeviceConfigurationValidation() {
        val config = MidiCIDeviceConfiguration()
        config.jsonSchemaString = """{
            "type": "object",
            "properties": {
                "deviceName": {"type": "string"},
                "version": {"type": "number"}
            },
            "required": ["deviceName"]
        }"""
        
        val validData = Json.parse("""{"deviceName": "TestDevice", "version": 1.0}""")
        val validResult = JsonSchemaIntegration.validateDeviceConfiguration(config, validData)
        assertTrue(validResult.isValid, "Valid data should pass validation")
        
        val invalidData = Json.parse("""{"version": 1.0}""")
        val invalidResult = JsonSchemaIntegration.validateDeviceConfiguration(config, invalidData)
        assertFalse(invalidResult.isValid, "Invalid data should fail validation")
    }

    @Test
    fun testEmptySchemaValidation() {
        val config = MidiCIDeviceConfiguration()
        config.jsonSchemaString = ""
        
        val anyData = Json.parse("""{"anything": "goes"}""")
        val result = JsonSchemaIntegration.validateDeviceConfiguration(config, anyData)
        assertTrue(result.isValid, "Empty schema should allow any data")
    }

    @Test
    fun testPropertyDataValidation() {
        val schemaString = """{
            "type": "array",
            "items": {"type": "string"}
        }"""
        
        val validData = Json.parse("""["item1", "item2", "item3"]""")
        val validResult = JsonSchemaIntegration.validatePropertyData(schemaString, validData)
        assertTrue(validResult.isValid, "Valid array should pass validation")
        
        val invalidData = Json.parse("""["item1", 123, "item3"]""")
        val invalidResult = JsonSchemaIntegration.validatePropertyData(schemaString, invalidData)
        assertFalse(invalidResult.isValid, "Invalid array should fail validation")
    }
}
