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
interface MidiCIClientPropertyRules {
    fun createDataRequestHeader(propertyId: String, fields: Map<String, Any?>): List<Byte>

    fun createSubscriptionHeader(propertyId: String, fields: Map<String, Any?>): List<Byte>

    fun getPropertyIdForHeader(header: List<Byte>): String

    fun getMetadataList(): List<PropertyMetadata>?

    fun requestPropertyList(group: Byte)

    fun propertyValueUpdated(propertyId: String, data: List<Byte>)

    fun getHeaderFieldInteger(header: List<Byte>, field: String): Int?
    fun getHeaderFieldString(header: List<Byte>, field: String): String?

    // To avoid too much Common Rules for PE exposure, we need to cast this Any argument as Subscription in the implementation.
    fun processPropertySubscriptionResult(subscriptionContext: Any, msg: Message.SubscribePropertyReply)

    fun getSubscribedProperty(msg: Message.SubscribeProperty): String?

    fun createStatusHeader(status: Int): List<Byte>
    // FIXME: too much exposure of Common Rules for PE
    fun getUpdatedValue(existing: PropertyValue?, isPartial: Boolean, mediaType: String, body: List<Byte>): Pair<Boolean, List<Byte>>
    // FIXME: too much exposure of Common Rules for PE
    fun encodeBody(data: List<Byte>, encoding: String?): List<Byte>
    fun decodeBody(header: List<Byte>, body: List<Byte>): List<Byte>

    val propertyCatalogUpdated: MutableList<() -> Unit>
}