package dev.atsushieno.ktmidi.ci

/**
 * Observable list of MIDI-CI Properties
 */
class ObservablePropertyList(private val propertyService: MidiCIPropertyService) {
    data class Entry(val id: String, val header: List<Byte>, val body: List<Byte>)

    private val properties = mutableListOf<Entry>()

    val entries: List<Entry>
        get() = properties

    fun getPropertyIdFor(header: List<Byte>) = propertyService.getPropertyIdentifier(header)

    fun getPropertyIds(): List<String> = properties.map { it.id }
    fun getProperty(header: List<Byte>): List<Byte>? = getProperty(getPropertyIdFor(header))
    fun getProperty(propertyId: String): List<Byte>? = getPropertyEntry(propertyId)?.body
    fun getPropertyEntry(header: List<Byte>): Entry? = getPropertyEntry(getPropertyIdFor(header))
    fun getPropertyEntry(propertyId: String): Entry? =
        properties.firstOrNull { it.id == propertyId }

    fun set(header: List<Byte>, body: List<Byte>) {
        val id = getPropertyIdFor(header)
        properties.removeAll { it.id == id }
        val entry = Entry(id, header, body)
        properties.add(entry)
        propertiesChanged.forEach { it(entry) }
    }

    val propertiesChanged = mutableListOf<(entry: Entry) -> Unit>()
}