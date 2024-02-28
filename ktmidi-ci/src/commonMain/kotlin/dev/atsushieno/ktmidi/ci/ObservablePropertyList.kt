package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.propertycommonrules.CommonRulesKnownMimeTypes
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyCommonHeaderKeys
import kotlinx.serialization.Serializable

/**
 * Observable list of MIDI-CI Properties. Note that each entry is NOT observable.
 */

@Serializable
data class PropertyValue(val id: String, val mediaType: String, var body: List<Byte>)

abstract class ObservablePropertyList(protected val internalValues: MutableList<PropertyValue>) {

    val values: List<PropertyValue>
        get() = internalValues

    abstract fun getMetadataList(): List<PropertyMetadata>?

    fun getProperty(propertyId: String): List<Byte>? = getPropertyValue(propertyId)?.body
    fun getPropertyValue(propertyId: String): PropertyValue? =
        internalValues.firstOrNull { it.id == propertyId }

    val valueUpdated = mutableListOf<(propertyValue: PropertyValue) -> Unit>()
    val propertiesCatalogUpdated = mutableListOf<() -> Unit>()
    abstract val internalCatalogUpdated: MutableList<() -> Unit>

    fun initializeCatalogUpdatedEvent() {
        internalCatalogUpdated.add {
            val newEntries = mutableListOf<PropertyValue>()
            val list = getMetadataList()
            list?.forEach { entry ->
                val existing = internalValues.firstOrNull { it.id == entry.resource }
                if (existing != null)
                    newEntries.add(existing)
                else
                    newEntries.add(PropertyValue(entry.resource, entry.mediaTypes.firstOrNull() ?: "", listOf()))
            }
            internalValues.clear()
            internalValues.addAll(newEntries)

            propertiesCatalogUpdated.forEach { it() }
        }
    }
}

class ClientObservablePropertyList(private val logger: Logger, private val propertyClient: MidiCIClientPropertyRules)
    : ObservablePropertyList(mutableListOf()) {
    override fun getMetadataList(): List<PropertyMetadata>? = propertyClient.getMetadataList()

    override val internalCatalogUpdated: MutableList<() -> Unit>
        get() = propertyClient.propertyCatalogUpdated

    private fun updateValue(propertyId: String, isPartial: Boolean, newValueMediaType: String, body: List<Byte>) {
        val existing = internalValues.firstOrNull { it.id == propertyId }
        if (existing != null)
            internalValues.remove(existing)
        val updateResult = propertyClient.getUpdatedValue(existing, isPartial, newValueMediaType, body)
        if (!updateResult.first) {
            logger.logError("Partial property update failed for $propertyId")
            return
        }
        val propertyValue = PropertyValue(propertyId, newValueMediaType, updateResult.second)
        internalValues.add(propertyValue)
        valueUpdated.forEach { it(propertyValue) }
    }

    fun updateValue(propertyId: String, reply: Message.GetPropertyDataReply) {
        // FIXME: cosmetic but unnecessary Common Rules for PE exposure
        val mediaType = propertyClient.getHeaderFieldString(reply.header, PropertyCommonHeaderKeys.MEDIA_TYPE) ?: CommonRulesKnownMimeTypes.APPLICATION_JSON
        val decodedBody = propertyClient.decodeBody(reply.header, reply.body)
        // there is no partial updates in Reply to Get Property Data
        updateValue(propertyId, false, mediaType, decodedBody)
    }

    fun updateValue(msg: Message.SubscribeProperty): String? {
        val id = propertyClient.getSubscribedProperty(msg)
        if (id == null) {
            logger.logError("Updating property value failed as the specified subscription property is not found.")
            return null
        }
        val command = propertyClient.getHeaderFieldString(msg.header, PropertyCommonHeaderKeys.COMMAND)
        if (command == MidiCISubscriptionCommand.NOTIFY)
            return command
        val isPartial = command == MidiCISubscriptionCommand.PARTIAL
        // FIXME: cosmetic but unnecessary exposure of Common Rules for PE.
        val mediaType = propertyClient.getHeaderFieldString(msg.header, PropertyCommonHeaderKeys.MEDIA_TYPE) ?: CommonRulesKnownMimeTypes.APPLICATION_JSON
        val decodedBody = propertyClient.decodeBody(msg.header, msg.body)
        updateValue(id, isPartial, mediaType, decodedBody)
        return command
    }

    init {
        initializeCatalogUpdatedEvent()
    }
}

class ServiceObservablePropertyList(values: MutableList<PropertyValue>, private val propertyService: MidiCIServicePropertyRules)
    : ObservablePropertyList(values) {

    override fun getMetadataList(): List<PropertyMetadata>? = propertyService.getMetadataList()

    override val internalCatalogUpdated: MutableList<() -> Unit>
        get() = propertyService.propertyCatalogUpdated

    fun addMetadata(property: PropertyMetadata) {
        propertyService.addMetadata(property)
        propertiesCatalogUpdated.forEach { it() }
    }

    fun removeMetadata(propertyId: String) {
        propertyService.removeMetadata(propertyId)
        propertiesCatalogUpdated.forEach { it() }
    }

    fun updateMetadata(oldPropertyId: String, property: PropertyMetadata) {
        // remove property first, then add a new property
        propertyService.removeMetadata(oldPropertyId)
        propertyService.addMetadata(property)
    }

    init {
        initializeCatalogUpdatedEvent()
        internalValues.addAll(propertyService.getMetadataList() ?.map {
            PropertyValue(it.resource, it.mediaTypes.firstOrNull() ?: "", listOf())
        } ?: listOf())
    }
}
