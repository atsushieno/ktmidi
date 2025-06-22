package dev.atsushieno.ktmidi.ci.jsonschema

import dev.atsushieno.ktmidi.ci.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class JsonSchemaClassGeneratorTest {
    
    @Test
    fun testSimpleObjectGeneration() {
        val schema = """{
            "type": "object",
            "properties": {
                "name": {"type": "string"},
                "age": {"type": "integer"}
            },
            "required": ["name"]
        }"""
        
        val generator = JsonSchemaClassGenerator()
        val classes = generator.generateClasses(schema, "Person")
        
        assertEquals(1, classes.size)
        val personClass = classes[0]
        assertEquals("Person", personClass.className)
        assertTrue(personClass.sourceCode.contains("data class Person"))
        assertTrue(personClass.sourceCode.contains("val name: String"))
        assertTrue(personClass.sourceCode.contains("val age: Int? = null"))
        assertTrue(personClass.sourceCode.contains("fun fromString(json: String)"))
        assertTrue(personClass.sourceCode.contains("fun toJsonString()"))
    }
    
    @Test
    fun testEnumGeneration() {
        val schema = """{
            "type": "object",
            "properties": {
                "status": {
                    "type": "string",
                    "enum": ["active", "inactive", "pending"]
                }
            },
            "required": ["status"]
        }"""
        
        val generator = JsonSchemaClassGenerator()
        val classes = generator.generateClasses(schema, "StatusObject")
        
        assertEquals(2, classes.size)
        val mainClass = classes.find { it.className == "StatusObject" }
        val enumClass = classes.find { it.className.contains("Enum") }
        
        assertNotNull(mainClass)
        assertNotNull(enumClass)
        assertTrue(enumClass.sourceCode.contains("enum class"))
        assertTrue(enumClass.sourceCode.contains("ACTIVE(\"active\")"))
        assertTrue(enumClass.sourceCode.contains("INACTIVE(\"inactive\")"))
        assertTrue(enumClass.sourceCode.contains("PENDING(\"pending\")"))
    }
    
    @Test
    fun testNestedObjectGeneration() {
        val schema = """{
            "type": "object",
            "properties": {
                "user": {
                    "type": "object",
                    "properties": {
                        "name": {"type": "string"},
                        "email": {"type": "string"}
                    },
                    "required": ["name"]
                },
                "count": {"type": "integer"}
            }
        }"""
        
        val generator = JsonSchemaClassGenerator()
        val classes = generator.generateClasses(schema, "Container")
        
        assertEquals(2, classes.size)
        val containerClass = classes.find { it.className == "Container" }
        val userClass = classes.find { it.className == "User" }
        
        assertNotNull(containerClass)
        assertNotNull(userClass)
        assertTrue(containerClass.sourceCode.contains("val user: User?"))
        assertTrue(userClass.sourceCode.contains("val name: String"))
        assertTrue(userClass.sourceCode.contains("val email: String? = null"))
    }
    
    @Test
    fun testArrayGeneration() {
        val schema = """{
            "type": "object",
            "properties": {
                "tags": {
                    "type": "array",
                    "items": {"type": "string"}
                },
                "numbers": {
                    "type": "array",
                    "items": {"type": "integer"}
                }
            }
        }"""
        
        val generator = JsonSchemaClassGenerator()
        val classes = generator.generateClasses(schema, "ArrayContainer")
        
        assertEquals(1, classes.size)
        val arrayClass = classes[0]
        assertTrue(arrayClass.sourceCode.contains("val tags: List<String>? = null"))
        assertTrue(arrayClass.sourceCode.contains("val numbers: List<Int>? = null"))
    }
    
    @Test
    fun testMidiSchemaStructure() {
        val midiSchema = """{
            "type": "object",
            "properties": {
                "resourceList": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "resource": {"type": "string"},
                            "canGet": {"type": "boolean"},
                            "canSet": {
                                "type": "string",
                                "enum": ["none", "full", "partial"]
                            }
                        },
                        "required": ["resource"]
                    }
                }
            }
        }"""
        
        val generator = JsonSchemaClassGenerator()
        val classes = generator.generateClasses(midiSchema, "ResourceListSchema")
        
        assertTrue(classes.size >= 2)
        val mainClass = classes.find { it.className == "ResourceListSchema" }
        assertNotNull(mainClass)
        assertTrue(mainClass.sourceCode.contains("val resourceList: List<"))
    }
    
    @Test
    fun testGenerationOptions() {
        val schema = """{
            "type": "object",
            "properties": {
                "name": {"type": "string"}
            }
        }"""
        
        val generator = JsonSchemaClassGenerator()
        val options = JsonSchemaClassGenerator.GenerationOptions(
            packageName = "com.example.test",
            classNamePrefix = "Generated",
            classNameSuffix = "Data"
        )
        val classes = generator.generateClasses(schema, "Test", options)
        
        assertEquals(1, classes.size)
        val testClass = classes[0]
        assertEquals("GeneratedTestData", testClass.className)
        assertEquals("com.example.test", testClass.packageName)
        assertTrue(testClass.sourceCode.contains("package com.example.test"))
    }
    
    @Test
    fun testSerializationRoundTrip() {
        val schema = """{
            "type": "object",
            "properties": {
                "name": {"type": "string"},
                "age": {"type": "integer"},
                "active": {"type": "boolean"}
            },
            "required": ["name"]
        }"""
        
        val generator = JsonSchemaClassGenerator()
        val classes = generator.generateClasses(schema, "TestPerson")
        
        assertEquals(1, classes.size)
        val personClass = classes[0]
        
        assertTrue(personClass.sourceCode.contains("fun fromString(json: String)"))
        assertTrue(personClass.sourceCode.contains("fun toJsonString()"))
        assertTrue(personClass.sourceCode.contains("fun fromJsonValue(jsonValue: Json.JsonValue)"))
        assertTrue(personClass.sourceCode.contains("fun toJsonValue()"))
    }
}
