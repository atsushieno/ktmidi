package dev.atsushieno.ktmidi.ci

/**
 * Observable list of MIDI-CI Properties
 */

abstract class ObservablePropertyList {
    data class Entry(val id: String, val replyHeader: List<Byte>, val body: List<Byte>)

    protected val properties = mutableListOf<Entry>()

    val entries: List<Entry>
        get() = properties

    abstract fun getPropertyIdFor(header: List<Byte>): String
    abstract fun getReplyStatusFor(header: List<Byte>): Int?
    abstract fun getMediaTypeFor(replyHeader: List<Byte>): String
    abstract fun getPropertyList(): List<PropertyResource>?

    fun getProperty(header: List<Byte>): List<Byte>? = getProperty(getPropertyIdFor(header))
    fun getProperty(propertyId: String): List<Byte>? = getPropertyEntry(propertyId)?.body
    fun getPropertyEntry(header: List<Byte>): Entry? = getPropertyEntry(getPropertyIdFor(header))
    fun getPropertyEntry(propertyId: String): Entry? =
        properties.firstOrNull { it.id == propertyId }

    fun set(request: Message.GetPropertyData, reply: Message.GetPropertyDataReply) {
        val id = getPropertyIdFor(request.header)
        properties.removeAll { it.id == id }
        val entry = Entry(id, reply.header, reply.body)
        properties.add(entry)
        propertyChanged.forEach { it(entry) }
    }

    val propertyChanged = mutableListOf<(entry: Entry) -> Unit>()
    val propertiesCatalogUpdated = mutableListOf<() -> Unit>()
}

class ClientObservablePropertyList(private val propertyClient: MidiCIPropertyClient)
    : ObservablePropertyList() {
    override fun getPropertyIdFor(header: List<Byte>) = propertyClient.getPropertyIdForHeader(header)
    override fun getReplyStatusFor(header: List<Byte>) = propertyClient.getReplyStatusFor(header)
    override fun getMediaTypeFor(replyHeader: List<Byte>) = propertyClient.getMediaTypeFor(replyHeader)

    override fun getPropertyList(): List<PropertyResource>? = propertyClient.getPropertyList()

    init {
        propertyClient.propertyCatalogUpdated.add {
            val newEntries = mutableListOf<Entry>()
            val list = getPropertyList()
            list?.forEach { entry ->
                val existing = properties.firstOrNull { it.id == entry.resource }
                if (existing != null)
                    newEntries.add(existing)
                else
                    newEntries.add(Entry(entry.resource, listOf(), listOf()))
            }
            properties.clear()
            properties.addAll(newEntries)

            propertiesCatalogUpdated.forEach { it() }
        }
    }
}

class ServiceObservablePropertyList(private val propertyService: MidiCIPropertyService)
    : ObservablePropertyList() {
    override fun getPropertyIdFor(header: List<Byte>) = propertyService.getPropertyIdForHeader(header)
    override fun getReplyStatusFor(header: List<Byte>) = propertyService.getReplyStatusFor(header)
    override fun getMediaTypeFor(replyHeader: List<Byte>) = propertyService.getMediaTypeFor(replyHeader)

    override fun getPropertyList(): List<PropertyResource>? = propertyService.getPropertyList()

    init {
        val newEntries = mutableListOf<Entry>()
        val list = getPropertyList()
        list?.forEach { entry ->
            val existing = properties.firstOrNull { it.id == entry.resource }
            if (existing != null)
                newEntries.add(existing)
            else
                newEntries.add(Entry(entry.resource, listOf(), listOf()))
        }
        properties.clear()
        properties.addAll(newEntries)
    }
}
