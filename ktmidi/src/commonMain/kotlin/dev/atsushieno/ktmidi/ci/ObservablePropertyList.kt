package dev.atsushieno.ktmidi.ci

/**
 * Observable list of MIDI-CI Properties
 */
class ObservablePropertyList(private val propertyClient: MidiCIPropertyClient) {
    data class Entry(val id: String, val header: List<Byte>, val body: List<Byte>)

    private val properties = mutableListOf<Entry>()

    val entries: List<Entry>
        get() = properties

    fun getPropertyIdFor(header: List<Byte>) = propertyClient.getPropertyIdForHeader(header)
    fun getReplyStatusFor(header: List<Byte>) = propertyClient.getReplyStatusFor(header)

    fun getPropertyIds(): List<String> = properties.map { it.id }
    fun getProperty(header: List<Byte>): List<Byte>? = getProperty(getPropertyIdFor(header))
    fun getProperty(propertyId: String): List<Byte>? = getPropertyEntry(propertyId)?.body
    fun getPropertyEntry(header: List<Byte>): Entry? = getPropertyEntry(getPropertyIdFor(header))
    fun getPropertyEntry(propertyId: String): Entry? =
        properties.firstOrNull { it.id == propertyId }

    fun set(request: Message.GetPropertyData, reply: Message.GetPropertyDataReply) {
        val id = getPropertyIdFor(request.header)
        properties.removeAll { it.id == id }
        val entry = Entry(id, request.header, reply.body)
        properties.add(entry)
        propertyChanged.forEach { it(entry) }
    }

    val propertyChanged = mutableListOf<(entry: Entry) -> Unit>()

    init {
        propertyClient.propertyCatalogUpdated.add {
            val newEntries = mutableListOf<Entry>()
            getPropertyIds().forEach {  id ->
                val existing = properties.firstOrNull { it.id == id }
                if (existing != null)
                    newEntries.add(existing)
                else
                    newEntries.add(Entry(id, listOf(), listOf()))
            }
            properties.clear()
            properties.addAll(newEntries)
        }
    }
}