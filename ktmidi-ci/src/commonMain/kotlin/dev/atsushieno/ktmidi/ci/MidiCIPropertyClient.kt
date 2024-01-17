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
    fun createRequestHeader(resourceIdentifier: String, encoding: String?, isPartialSet: Boolean): List<Byte>

    fun createSubscriptionHeader(resourceIdentifier: String, command: String, mutualEncoding: String?): List<Byte>

    fun getPropertyIdForHeader(header: List<Byte>): String

    fun getMetadataList(): List<PropertyMetadata>?

    suspend fun requestPropertyList(destinationMUID: Int, requestId: Byte)

    fun onGetPropertyDataReply(request: Message.GetPropertyData, reply: Message.GetPropertyDataReply)

    // FIXME: too much of infection by Common Rules for PE
    fun getReplyStatusFor(header: List<Byte>): Int?

    // FIXME: too much of infection by Common Rules for PE
    fun getMediaTypeFor(replyHeader: List<Byte>): String

    // FIXME: too much of infection by Common Rules for PE
    fun getEncodingFor(header: List<Byte>): String

    // FIXME: too much of infection by Common Rules for PE
    fun getIsPartialFor(header: List<Byte>): Boolean

    // FIXME: too much of infection by Common Rules for PE
    fun getCommandFieldFor(header: List<Byte>): String?

    // FIXME: too much of infection by Common Rules for PE
    fun getSubscribedProperty(msg: Message.SubscribeProperty): String?

    // FIXME: too much of infection by Common Rules for PE
    fun processPropertySubscriptionResult(sub: MidiCIInitiator.Subscription, reply: Message.SubscribePropertyReply)
    // FIXME: too much of infection by Common Rules for PE
    fun getSubscriptionId(header: List<Byte>): String?

    fun createStatusHeader(status: Int): List<Byte>
    fun getUpdatedValue(existing: PropertyValue?, isPartial: Boolean, mediaType: String, body: List<Byte>): Pair<Boolean, List<Byte>>
    fun encodeBody(data: List<Byte>, encoding: String?): List<Byte>
    fun decodeBody(data: List<Byte>, encoding: String?): List<Byte>

    val propertyCatalogUpdated: MutableList<() -> Unit>
}