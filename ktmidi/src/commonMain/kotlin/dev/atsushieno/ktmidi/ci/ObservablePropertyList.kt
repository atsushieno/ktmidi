package dev.atsushieno.ktmidi.ci

/**
 * Observable list of MIDI-CI Properties
 */
class ObservablePropertyList(private val propertyClient: MidiCIPropertyClient) {
    data class Entry(val id: String, val replyHeader: List<Byte>, val body: List<Byte>)

    private val properties = mutableListOf<Entry>()

    val entries: List<Entry>
        get() = properties

    fun getPropertyIdFor(header: List<Byte>) = propertyClient.getPropertyIdForHeader(header)
    fun getReplyStatusFor(header: List<Byte>) = propertyClient.getReplyStatusFor(header)
    fun getMediaTypeFor(replyHeader: List<Byte>) = propertyClient.getMediaTypeFor(replyHeader)

    fun getPropertyList(): List<PropertyResource>? = propertyClient.getPropertyList()
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