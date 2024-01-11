package dev.atsushieno.ktmidi.ci

/**
 * This interface abstracts how MIDI-CI Property Exchange Responder (host, service, whatever)
 * handles the incoming inquiries.
 *
 * M2-103-UM (Common Rules for Property Exchange) specification formalizes how PE header and body
 * content binaries are interpreted. PE header section is always JSON (of some restricted form),
 * and body content may differ, as per its mime type that the (JSON) schema indicates.
 *
 * It should be noted that Common Rules for PE is technically just a format option for
 * the PE content (header and body) and there may be other formats (especially in the future
 * set of specifications).
 * Therefore, it is technically important to extract this interface out from CommonPropertyService.
 */
interface MidiCIPropertyService {
    fun getPropertyIdForHeader(header: List<Byte>): String
    fun createUpdateNotificationHeader(propertyId: String, isUpdatePartial: Boolean): List<Byte>
    fun getMetadataList(): List<PropertyMetadata>?
    fun getPropertyData(msg: Message.GetPropertyData) : Result<Message.GetPropertyDataReply>
    fun setPropertyData(msg: Message.SetPropertyData) : Result<Message.SetPropertyDataReply>
    fun subscribeProperty(msg: Message.SubscribeProperty) : Result<Message.SubscribePropertyReply>
    fun getReplyStatusFor(header: List<Byte>): Int?
    fun getMediaTypeFor(replyHeader: List<Byte>): String
    fun addMetadata(property: PropertyMetadata)
    fun removeMetadata(propertyId: String)

    val propertyCatalogUpdated: MutableList<() -> Unit>
}