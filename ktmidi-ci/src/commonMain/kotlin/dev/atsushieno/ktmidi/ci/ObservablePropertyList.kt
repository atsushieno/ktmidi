package dev.atsushieno.ktmidi.ci

/**
 * Observable list of MIDI-CI Properties. Note that each entry is NOT observable.
 */

data class PropertyValue(val id: String, val mediaType: String, var body: List<Byte>)

abstract class ObservablePropertyList {

    protected val internalValues = mutableListOf<PropertyValue>()

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

class ClientObservablePropertyList(private val propertyClient: MidiCIPropertyClient)
    : ObservablePropertyList() {
    fun getPropertyIdFor(header: List<Byte>) = propertyClient.getPropertyIdForHeader(header)
    fun getReplyStatusFor(header: List<Byte>) = propertyClient.getReplyStatusFor(header)

    override fun getMetadataList(): List<PropertyMetadata>? = propertyClient.getMetadataList()

    override val internalCatalogUpdated: MutableList<() -> Unit>
        get() = propertyClient.propertyCatalogUpdated

    fun updateValue(request: Message.GetPropertyData, reply: Message.GetPropertyDataReply) {
        val id = getPropertyIdFor(request.header)
        internalValues.removeAll { it.id == id }
        val mediaType = propertyClient.getMediaTypeFor(reply.header)
        val propertyValue = PropertyValue(id, mediaType, reply.body)
        internalValues.add(propertyValue)
        valueUpdated.forEach { it(propertyValue) }
    }

    init {
        initializeCatalogUpdatedEvent()
    }
}

class ServiceObservablePropertyList(private val propertyService: MidiCIPropertyService)
    : ObservablePropertyList() {

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
    }
}
