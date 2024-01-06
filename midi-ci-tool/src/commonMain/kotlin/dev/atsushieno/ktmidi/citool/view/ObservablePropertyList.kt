package dev.atsushieno.ktmidi.citool.view

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import dev.atsushieno.ktmidi.ci.Message
import dev.atsushieno.ktmidi.ci.MidiCIPropertyClient
import dev.atsushieno.ktmidi.ci.MidiCIPropertyService
import dev.atsushieno.ktmidi.ci.PropertyMetadata

class PropertyValueState(var id: MutableState<String>, val replyHeader: List<Byte>, val body: List<Byte>)

abstract class ObservablePropertyList {

    protected val internalValues = mutableStateListOf<PropertyValueState>()

    val values: List<PropertyValueState>
        get() = internalValues

    abstract fun getPropertyIdFor(header: List<Byte>): String
    abstract fun getReplyStatusFor(header: List<Byte>): Int?
    abstract fun getMediaTypeFor(replyHeader: List<Byte>): String
    abstract fun getMetadataList(): List<PropertyMetadata>?

    fun getProperty(header: List<Byte>): List<Byte>? = getProperty(getPropertyIdFor(header))
    fun getProperty(propertyId: String): List<Byte>? = getPropertyValue(propertyId)?.body
    fun getPropertyValue(header: List<Byte>): PropertyValueState? = getPropertyValue(getPropertyIdFor(header))
    fun getPropertyValue(propertyId: String): PropertyValueState? =
        internalValues.firstOrNull { it.id.value == propertyId }

    fun set(request: Message.GetPropertyData, reply: Message.GetPropertyDataReply) {
        val id = getPropertyIdFor(request.header)
        internalValues.removeAll { it.id.value == id }
        val propertyValue = PropertyValueState(mutableStateOf(id), reply.header, reply.body)
        internalValues.add(propertyValue)
        valueUpdated.forEach { it(propertyValue) }
    }

    val valueUpdated = mutableListOf<(propertyValue: PropertyValueState) -> Unit>()
    val propertiesCatalogUpdated = mutableListOf<() -> Unit>()
    abstract val internalCatalogUpdated: MutableList<() -> Unit>

    fun initializeCatalogUpdatedEvent() {
        internalCatalogUpdated.add {
            val newEntries = mutableListOf<PropertyValueState>()
            val list = getMetadataList()
            list?.forEach { entry ->
                val existing = internalValues.firstOrNull { it.id.value == entry.resource }
                if (existing != null)
                    newEntries.add(existing)
                else
                    newEntries.add(PropertyValueState(mutableStateOf(entry.resource), listOf(), listOf()))
            }
            internalValues.clear()
            internalValues.addAll(newEntries)

            propertiesCatalogUpdated.forEach { it() }
        }
    }

    fun updateValue(entry: PropertyValueState) {
        val existing = internalValues.indexOfFirst { entry.id.value == it.id.value }
        internalValues.removeAt(existing)
        internalValues.add(existing, entry)
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

    fun updateMetadata(it: PropertyMetadata) {
        propertyService.getMetadataList()!!.first { it.resource == it.resource }.updateFrom(it)
    }

    init {
        internalValues.addAll(propertyService.getMetadataList()?.map {
            PropertyValueState(mutableStateOf(it.resource), listOf(), listOf())
        } ?: listOf())
        initializeCatalogUpdatedEvent()
    }
}
