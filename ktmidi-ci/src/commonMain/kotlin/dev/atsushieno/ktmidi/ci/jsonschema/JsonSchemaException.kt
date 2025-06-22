package dev.atsushieno.ktmidi.ci.jsonschema

class JsonSchemaException(message: String = "JSON Schema exception", innerException: Exception? = null) : Exception(message, innerException)

class JsonSchemaValidationException(message: String, val schemaPath: String, val instancePath: String, innerException: Exception? = null) : Exception(message, innerException)
