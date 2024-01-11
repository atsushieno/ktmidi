package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.json.Json

class PropertyMetadata() {
    var resource: String = ""
    var canGet: Boolean = true
    var canSet: String = PropertySetAccess.NONE
    var canSubscribe: Boolean = false
    var requireResId: Boolean = false
    var mediaTypes: List<String> = listOf("application/json")
    var encodings: List<String> = listOf("ASCII")
    var schema: Json.JsonValue? = null
    // additional properties for List resources
    var canPaginate: Boolean = false
    var columns: List<PropertyResourceColumn> = listOf()

    constructor(resource: String) : this() { this.resource = resource }
}