package dev.atsushieno.ktmidi.ci.propertycommonrules

import dev.atsushieno.ktmidi.ci.PropertyMetadata
import kotlinx.serialization.Serializable

@Serializable
class CommonRulesPropertyMetadata() : PropertyMetadata {
    object Keys {
        const val ENCODINGS = "encodings"
        const val MEDIA_TYPES = "mediaTypes"
    }

    enum class Originator {
        SYSTEM,
        USER
    }

    override val propertyId: String
        get() = resource

    override fun getExtra(key: String) =
        when(key) {
            Keys.MEDIA_TYPES -> mediaTypes
            Keys.ENCODINGS -> encodings
            else -> TODO("Not in use yet")
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
