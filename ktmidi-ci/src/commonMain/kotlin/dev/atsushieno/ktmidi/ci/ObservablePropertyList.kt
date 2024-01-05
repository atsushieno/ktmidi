package dev.atsushieno.ktmidi.ci

/**
 * Observable list of MIDI-CI Properties. Note that each entry is NOT observable.
 */

data class PropertyValue(val id: String, val replyHeader: List<Byte>, val body: List<Byte>)

abstract class ObservablePropertyList {

    protected val internalValues = mutableListOf<PropertyValue>()

    val values: List<PropertyValue>
        get() = internalValues

    abstract fun getPropertyIdFor(header: List<Byte>): String
    abstract fun getReplyStatusFor(header: List<Byte>): Int?
    abstract fun getMediaTypeFor(replyHeader: List<Byte>): String
    abstract fun getMetadataList(): List<PropertyMetadata>?

    fun getProperty(header: List<Byte>): List<Byte>? = getProperty(getPropertyIdFor(header))
    fun getProperty(propertyId: String): List<Byte>? = getPropertyValue(propertyId)?.body
    fun getPropertyValue(header: List<Byte>): PropertyValue? = getPropertyValue(getPropertyIdFor(header))
    fun getPropertyValue(propertyId: String): PropertyValue? =
        internalValues.firstOrNull { it.id == propertyId }

    fun set(request: Message.GetPropertyData, reply: Message.GetPropertyDataReply) {
        val id = getPropertyIdFor(request.header)
        internalValues.removeAll { it.id == id }
        val propertyValue = PropertyValue(id, reply.header, reply.body)
        internalValues.add(propertyValue)
        valueUpdated.forEach { it(propertyValue) }
    }

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
                    newEntries.add(PropertyValue(entry.resource, listOf(), listOf()))
            }
            internalValues.clear()
            internalValues.addAll(newEntries)

            propertiesCatalogUpdated.forEach { it() }
        }
    }
}

class ClientObservablePropertyList(private val propertyClient: MidiCIPropertyClient)
    : ObservablePropertyList() {
    override fun getPropertyIdFor(header: List<Byte>) = propertyClient.getPropertyIdForHeader(header)
    override fun getReplyStatusFor(header: List<Byte>) = propertyClient.getReplyStatusFor(header)
    override fun getMediaTypeFor(replyHeader: List<Byte>) = propertyClient.getMediaTypeFor(replyHeader)

    override fun getMetadataList(): List<PropertyMetadata>? = propertyClient.getMetadataList()

    override val internalCatalogUpdated: MutableList<() -> Unit>
        get() = propertyClient.propertyCatalogUpdated

    init {
        initializeCatalogUpdatedEvent()
    }
}

class ServiceObservablePropertyList(private val propertyService: MidiCIPropertyService)
    : ObservablePropertyList() {
    override fun getPropertyIdFor(header: List<Byte>) = propertyService.getPropertyIdForHeader(header)
    override fun getReplyStatusFor(header: List<Byte>) = propertyService.getReplyStatusFor(header)
    override fun getMediaTypeFor(replyHeader: List<Byte>) = propertyService.getMediaTypeFor(replyHeader)

    override fun getMetadataList(): List<PropertyMetadata>? = propertyService.getMetadataList()

    override val internalCatalogUpdated: MutableList<() -> Unit>
        get() = propertyService.propertyCatalogUpdated

    fun addMetadata(property: PropertyMetadata) {
        propertyService.addMetadata(property)
        propertiesCatalogUpdated.forEach { it() }
    }

    init {
        initializeCatalogUpdatedEvent()
    }
}
