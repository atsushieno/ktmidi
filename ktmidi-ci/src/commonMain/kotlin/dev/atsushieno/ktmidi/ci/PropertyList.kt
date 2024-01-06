package dev.atsushieno.ktmidi.ci

/**
 * Observable list of MIDI-CI Properties. Note that each entry is NOT observable.
 */

data class PropertyValue(val id: String, val replyHeader: List<Byte>, val body: List<Byte>)

abstract class PropertyList {

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
    }
}

class ClientPropertyList(private val propertyClient: MidiCIPropertyClient)
    : PropertyList() {
    override fun getPropertyIdFor(header: List<Byte>) = propertyClient.getPropertyIdForHeader(header)
    override fun getReplyStatusFor(header: List<Byte>) = propertyClient.getReplyStatusFor(header)
    override fun getMediaTypeFor(replyHeader: List<Byte>) = propertyClient.getMediaTypeFor(replyHeader)

    override fun getMetadataList(): List<PropertyMetadata>? = propertyClient.getMetadataList()
}

class ServicePropertyList(private val propertyService: MidiCIPropertyService)
    : PropertyList() {
    override fun getPropertyIdFor(header: List<Byte>) = propertyService.getPropertyIdForHeader(header)
    override fun getReplyStatusFor(header: List<Byte>) = propertyService.getReplyStatusFor(header)
    override fun getMediaTypeFor(replyHeader: List<Byte>) = propertyService.getMediaTypeFor(replyHeader)

    override fun getMetadataList(): List<PropertyMetadata>? = propertyService.getMetadataList()

    fun addMetadata(property: PropertyMetadata) {
        propertyService.addMetadata(property)
    }
}
