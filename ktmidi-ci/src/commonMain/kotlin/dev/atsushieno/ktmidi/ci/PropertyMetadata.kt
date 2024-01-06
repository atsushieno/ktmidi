package dev.atsushieno.ktmidi.ci

class PropertyMetadata() {
    fun updateFrom(it: PropertyMetadata) {
        resource = it.resource
        canGet = it.canGet
        canSet = it.canSet
        canSubscribe = it.canSubscribe
        requireResId = it.requireResId
        mediaTypes = it.mediaTypes
        encodings = it.encodings
        schema = it.schema
        canPaginate = it.canPaginate
        columns = it.columns
    }

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