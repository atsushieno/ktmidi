package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyResourceColumn
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertySetAccess
import kotlinx.serialization.Serializable

@Serializable
class PropertyMetadata() {
    enum class Originator {
        SYSTEM,
        USER
    }

    var resource: String = ""
    var canGet: Boolean = true
    var canSet: String = PropertySetAccess.NONE
    var canSubscribe: Boolean = false
    var requireResId: Boolean = false
    var mediaTypes: List<String> = listOf("application/json")
    var encodings: List<String> = listOf("ASCII")
    var schema: String? = null
    // additional properties for List resources
    var canPaginate: Boolean = false
    var columns: List<PropertyResourceColumn> = listOf()
    var originator: Originator = Originator.USER

    constructor(resource: String) : this() { this.resource = resource }
}
