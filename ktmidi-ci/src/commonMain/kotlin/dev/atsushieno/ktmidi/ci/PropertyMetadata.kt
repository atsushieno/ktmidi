package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.propertycommonrules.CommonRulesKnownMimeTypes
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyDataEncoding
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
    var mediaTypes: List<String> = listOf(CommonRulesKnownMimeTypes.APPLICATION_JSON)
    var encodings: List<String> = listOf(PropertyDataEncoding.ASCII)
    var schema: String? = null
    // additional properties for List resources
    var canPaginate: Boolean = false
    var columns: List<PropertyResourceColumn> = listOf()
    // indicates whether it is user created or this MIDI-CI system (ktmidi) created e.g. ResourceList
    var originator: Originator = Originator.USER

    constructor(resource: String) : this() { this.resource = resource }
}
