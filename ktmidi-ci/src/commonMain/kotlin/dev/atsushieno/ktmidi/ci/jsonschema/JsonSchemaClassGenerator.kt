package dev.atsushieno.ktmidi.ci.jsonschema

import dev.atsushieno.ktmidi.ci.json.Json

class JsonSchemaClassGenerator {
    
    data class GeneratedClass(
        val className: String,
        val packageName: String,
        val sourceCode: String,
        val dependencies: List<String> = emptyList()
    )
    
    data class GenerationOptions(
        val packageName: String = "dev.atsushieno.ktmidi.ci.generated",
        val classNamePrefix: String = "",
        val classNameSuffix: String = "",
        val generateValidation: Boolean = true,
        val generateToString: Boolean = true,
        val generateEquals: Boolean = true
    )
    
    fun generateClasses(schema: JsonSchema, rootClassName: String, options: GenerationOptions = GenerationOptions()): List<GeneratedClass> {
        val context = GenerationContext(options)
        val rootClass = generateClass(schema, rootClassName, context)
        return listOf(rootClass) + context.nestedClasses
    }
    
    fun generateClasses(schemaJson: String, rootClassName: String, options: GenerationOptions = GenerationOptions()): List<GeneratedClass> {
        val schema = JsonSchema.parse(schemaJson)
        return generateClasses(schema, rootClassName, options)
    }
    
    private fun String.toPascalCase(): String {
        return this.split(Regex("[^a-zA-Z0-9]"))
            .filter { it.isNotEmpty() }
            .joinToString("") { it.replaceFirstChar { char -> char.uppercaseChar() } }
    }
    
    private fun String.toCamelCase(): String {
        val pascalCase = this.toPascalCase()
        return if (pascalCase.isNotEmpty()) {
            pascalCase.replaceFirstChar { it.lowercaseChar() }
        } else {
            pascalCase
        }
    }

    private inner class GenerationContext(val options: GenerationOptions) {
        val nestedClasses = mutableListOf<GeneratedClass>()
        val generatedClassNames = mutableSetOf<String>()
        
        fun addNestedClass(generatedClass: GeneratedClass) {
            if (!generatedClassNames.contains(generatedClass.className)) {
                nestedClasses.add(generatedClass)
                generatedClassNames.add(generatedClass.className)
            }
        }
        
        fun generateUniqueClassName(baseName: String): String {
            var className = "${options.classNamePrefix}${baseName.toPascalCase()}${options.classNameSuffix}"
            var counter = 1
            while (generatedClassNames.contains(className)) {
                className = "${options.classNamePrefix}${baseName.toPascalCase()}${counter}${options.classNameSuffix}"
                counter++
            }
            generatedClassNames.add(className)
            return className
        }
    }
    
    private fun generateClass(schema: JsonSchema, className: String, context: GenerationContext): GeneratedClass {
        val finalClassName = context.generateUniqueClassName(className)
        val sourceCode = buildString {
            appendLine("package ${context.options.packageName}")
            appendLine()
            appendLine("import dev.atsushieno.ktmidi.ci.json.Json")
            appendLine("import dev.atsushieno.ktmidi.ci.jsonschema.JsonSchemaException")
            appendLine()
            
            schema.title?.let { title ->
                appendLine("/**")
                appendLine(" * $title")
                schema.description?.let { desc ->
                    appendLine(" * ")
                    appendLine(" * $desc")
                }
                appendLine(" */")
            }
            
            append("data class $finalClassName(")
            
            val properties = generateProperties(schema, context)
            if (properties.isNotEmpty()) {
                appendLine()
                properties.forEachIndexed { index, property ->
                    append("    $property")
                    if (index < properties.size - 1) appendLine(",")
                    else appendLine()
                }
            }
            appendLine(") {")
            
            appendLine("    companion object {")
            appendLine("        fun fromString(json: String): $finalClassName {")
            appendLine("            return fromJsonValue(Json.parse(json))")
            appendLine("        }")
            appendLine()
            appendLine("        fun fromJsonValue(jsonValue: Json.JsonValue): $finalClassName {")
            appendLine("            if (jsonValue.token.type != Json.TokenType.Object) {")
            appendLine("                throw JsonSchemaException(\"Expected object, got \${jsonValue.token.type}\")")
            appendLine("            }")
            appendLine("            return $finalClassName(")
            
            generateFromJsonValueBody(schema, context).forEach { line ->
                appendLine("                $line")
            }
            
            appendLine("            )")
            appendLine("        }")
            appendLine("    }")
            appendLine()
            
            appendLine("    fun toJsonString(): String {")
            appendLine("        return Json.serialize(toJsonValue())")
            appendLine("    }")
            appendLine()
            appendLine("    fun toJsonValue(): Json.JsonValue {")
            appendLine("        val map = mutableMapOf<Json.JsonValue, Json.JsonValue>()")
            
            generateToJsonValueBody(schema, context).forEach { line ->
                appendLine("        $line")
            }
            
            appendLine("        return Json.JsonValue(map)")
            appendLine("    }")
            
            appendLine("}")
        }
        
        return GeneratedClass(finalClassName, context.options.packageName, sourceCode)
    }
    
    private fun generateProperties(schema: JsonSchema, context: GenerationContext): List<String> {
        val properties = mutableListOf<String>()
        
        schema.properties?.forEach { (propName, propSchema) ->
            val kotlinName = propName.toCamelCase()
            val kotlinType = getKotlinType(propSchema, propName, context)
            val isRequired = schema.required?.contains(propName) == true
            val nullableSuffix = if (isRequired) "" else "?"
            val defaultValue = if (isRequired) "" else " = null"
            
            val property = buildString {
                propSchema.description?.let { desc ->
                    append("/** $desc */ ")
                }
                append("val $kotlinName: $kotlinType$nullableSuffix$defaultValue")
            }
            properties.add(property)
        }
        
        return properties
    }
    
    private fun getKotlinType(schema: JsonSchema, propertyName: String, context: GenerationContext): String {
        return when (schema.type) {
            JsonSchemaType.STRING -> {
                if (schema.enum != null) {
                    generateEnumClass(schema, propertyName, context)
                } else {
                    "String"
                }
            }
            JsonSchemaType.NUMBER -> "Double"
            JsonSchemaType.INTEGER -> "Int"
            JsonSchemaType.BOOLEAN -> "Boolean"
            JsonSchemaType.ARRAY -> {
                val itemType = schema.items?.let { getKotlinType(it, "${propertyName}Item", context) } ?: "Json.JsonValue"
                "List<$itemType>"
            }
            JsonSchemaType.OBJECT -> {
                val nestedClassName = context.generateUniqueClassName(propertyName)
                val nestedClass = generateClass(schema, propertyName, context)
                context.addNestedClass(nestedClass)
                nestedClassName
            }
            JsonSchemaType.NULL -> "Json.JsonValue"
            null -> "Json.JsonValue"
        }
    }
    
    private fun generateEnumClass(schema: JsonSchema, propertyName: String, context: GenerationContext): String {
        val enumClassName = context.generateUniqueClassName("${propertyName}Enum")
        val enumValues = schema.enum ?: emptyList()
        
        val enumSourceCode = buildString {
            appendLine("package ${context.options.packageName}")
            appendLine()
            appendLine("enum class $enumClassName(val value: String) {")
            enumValues.forEachIndexed { index, value ->
                val enumConstName = value.uppercase().replace(Regex("[^A-Z0-9_]"), "_")
                append("    $enumConstName(\"$value\")")
                if (index < enumValues.size - 1) appendLine(",")
                else appendLine(";")
            }
            appendLine()
            appendLine("    companion object {")
            appendLine("        fun fromString(value: String): $enumClassName? {")
            appendLine("            return values().find { it.value == value }")
            appendLine("        }")
            appendLine("    }")
            appendLine("}")
        }
        
        context.addNestedClass(GeneratedClass(enumClassName, context.options.packageName, enumSourceCode))
        return enumClassName
    }
    
    private fun generateFromJsonValueBody(schema: JsonSchema, context: GenerationContext): List<String> {
        val lines = mutableListOf<String>()
        
        schema.properties?.forEach { (propName, propSchema) ->
            val kotlinName = propName.toCamelCase()
            val isRequired = schema.required?.contains(propName) == true
            
            when (propSchema.type) {
                JsonSchemaType.STRING -> {
                    if (propSchema.enum != null) {
                        val enumType = getKotlinType(propSchema, propName, context)
                        if (isRequired) {
                            lines.add("$kotlinName = $enumType.fromString(jsonValue.getObjectValue(\"$propName\")?.stringValue ?: \"\") ?: throw JsonSchemaException(\"Invalid enum value for $propName\"),")
                        } else {
                            lines.add("$kotlinName = jsonValue.getObjectValue(\"$propName\")?.stringValue?.let { $enumType.fromString(it) },")
                        }
                    } else {
                        if (isRequired) {
                            lines.add("$kotlinName = jsonValue.getObjectValue(\"$propName\")?.stringValue ?: throw JsonSchemaException(\"Missing required property: $propName\"),")
                        } else {
                            lines.add("$kotlinName = jsonValue.getObjectValue(\"$propName\")?.stringValue,")
                        }
                    }
                }
                JsonSchemaType.NUMBER -> {
                    if (isRequired) {
                        lines.add("$kotlinName = jsonValue.getObjectValue(\"$propName\")?.numberValue?.toDouble() ?: throw JsonSchemaException(\"Missing required property: $propName\"),")
                    } else {
                        lines.add("$kotlinName = jsonValue.getObjectValue(\"$propName\")?.numberValue?.toDouble(),")
                    }
                }
                JsonSchemaType.INTEGER -> {
                    if (isRequired) {
                        lines.add("$kotlinName = jsonValue.getObjectValue(\"$propName\")?.numberValue?.toInt() ?: throw JsonSchemaException(\"Missing required property: $propName\"),")
                    } else {
                        lines.add("$kotlinName = jsonValue.getObjectValue(\"$propName\")?.numberValue?.toInt(),")
                    }
                }
                JsonSchemaType.BOOLEAN -> {
                    if (isRequired) {
                        lines.add("$kotlinName = jsonValue.getObjectValue(\"$propName\")?.isBooleanTrue ?: throw JsonSchemaException(\"Missing required property: $propName\"),")
                    } else {
                        lines.add("$kotlinName = jsonValue.getObjectValue(\"$propName\")?.let { it.isBooleanTrue },")
                    }
                }
                JsonSchemaType.ARRAY -> {
                    val itemType = propSchema.items?.let { getKotlinType(it, "${propName}Item", context) } ?: "Json.JsonValue"
                    if (itemType == "Json.JsonValue") {
                        lines.add("$kotlinName = jsonValue.getObjectValue(\"$propName\")?.arrayValue?.toList() ?: ${if (isRequired) "emptyList()" else "null"},")
                    } else {
                        lines.add("$kotlinName = jsonValue.getObjectValue(\"$propName\")?.arrayValue?.map { $itemType.fromJsonValue(it) }?.toList() ?: ${if (isRequired) "emptyList()" else "null"},")
                    }
                }
                JsonSchemaType.OBJECT -> {
                    val objectType = getKotlinType(propSchema, propName, context)
                    if (isRequired) {
                        lines.add("$kotlinName = jsonValue.getObjectValue(\"$propName\")?.let { $objectType.fromJsonValue(it) } ?: throw JsonSchemaException(\"Missing required property: $propName\"),")
                    } else {
                        lines.add("$kotlinName = jsonValue.getObjectValue(\"$propName\")?.let { $objectType.fromJsonValue(it) },")
                    }
                }
                else -> {
                    lines.add("$kotlinName = jsonValue.getObjectValue(\"$propName\"),")
                }
            }
        }
        
        return lines
    }
    
    private fun generateToJsonValueBody(schema: JsonSchema, context: GenerationContext): List<String> {
        val lines = mutableListOf<String>()
        
        schema.properties?.forEach { (propName, propSchema) ->
            val kotlinName = propName.toCamelCase()
            val isRequired = schema.required?.contains(propName) == true
            
            when (propSchema.type) {
                JsonSchemaType.STRING -> {
                    if (propSchema.enum != null) {
                        if (isRequired) {
                            lines.add("map[Json.JsonValue(\"$propName\")] = Json.JsonValue($kotlinName.value)")
                        } else {
                            lines.add("$kotlinName?.let { map[Json.JsonValue(\"$propName\")] = Json.JsonValue(it.value) }")
                        }
                    } else {
                        if (isRequired) {
                            lines.add("map[Json.JsonValue(\"$propName\")] = Json.JsonValue($kotlinName)")
                        } else {
                            lines.add("$kotlinName?.let { map[Json.JsonValue(\"$propName\")] = Json.JsonValue(it) }")
                        }
                    }
                }
                JsonSchemaType.NUMBER -> {
                    if (isRequired) {
                        lines.add("map[Json.JsonValue(\"$propName\")] = Json.JsonValue($kotlinName)")
                    } else {
                        lines.add("$kotlinName?.let { map[Json.JsonValue(\"$propName\")] = Json.JsonValue(it) }")
                    }
                }
                JsonSchemaType.INTEGER -> {
                    if (isRequired) {
                        lines.add("map[Json.JsonValue(\"$propName\")] = Json.JsonValue($kotlinName.toDouble())")
                    } else {
                        lines.add("$kotlinName?.let { map[Json.JsonValue(\"$propName\")] = Json.JsonValue(it.toDouble()) }")
                    }
                }
                JsonSchemaType.BOOLEAN -> {
                    if (isRequired) {
                        lines.add("map[Json.JsonValue(\"$propName\")] = if ($kotlinName) Json.TrueValue else Json.FalseValue")
                    } else {
                        lines.add("$kotlinName?.let { map[Json.JsonValue(\"$propName\")] = if (it) Json.TrueValue else Json.FalseValue }")
                    }
                }
                JsonSchemaType.ARRAY -> {
                    val itemType = propSchema.items?.let { getKotlinType(it, "${propName}Item", context) } ?: "Json.JsonValue"
                    if (itemType == "Json.JsonValue") {
                        lines.add("map[Json.JsonValue(\"$propName\")] = Json.JsonValue($kotlinName)")
                    } else {
                        lines.add("map[Json.JsonValue(\"$propName\")] = Json.JsonValue($kotlinName.map { it.toJsonValue() })")
                    }
                }
                JsonSchemaType.OBJECT -> {
                    if (isRequired) {
                        lines.add("map[Json.JsonValue(\"$propName\")] = $kotlinName.toJsonValue()")
                    } else {
                        lines.add("$kotlinName?.let { map[Json.JsonValue(\"$propName\")] = it.toJsonValue() }")
                    }
                }
                else -> {
                    lines.add("$kotlinName?.let { map[Json.JsonValue(\"$propName\")] = it }")
                }
            }
        }
        
        return lines
    }
}
