package dev.atsushieno.ktmidi.ci

/**
 * To make sense implementing Property Exchange, but to not limit to Common Rules for PE,
 * we have extra set of requirements to support property meta system:
 *
 * - Every property header byte array must be able to provide one single property ID
 * - The system can provide a list of property IDs. It is asynchronous operation.
 * - When the list of property IDs are acquired, it must be notified as `propertyCatalogUpdated`
 *   - The list acquisition must be automatically detected.
 */
interface MidiCIPropertyClient {
    fun createRequestHeader(resourceIdentifier: String, isPartialSet: Boolean): List<Byte>

    fun getPropertyIdForHeader(header: List<Byte>): String

    fun getMetadataList(): List<PropertyMetadata>?

    suspend fun requestPropertyList(destinationMUID: Int, requestId: Byte)

    fun onGetPropertyDataReply(request: Message.GetPropertyData, reply: Message.GetPropertyDataReply)

    fun getReplyStatusFor(header: List<Byte>): Int?

    fun getMediaTypeFor(replyHeader: List<Byte>): String

    val propertyCatalogUpdated: MutableList<() -> Unit>
}