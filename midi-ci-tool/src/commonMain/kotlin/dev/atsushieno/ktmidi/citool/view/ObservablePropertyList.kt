package dev.atsushieno.ktmidi.citool.view

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import dev.atsushieno.ktmidi.ci.MidiCIPropertyClient
import dev.atsushieno.ktmidi.ci.MidiCIPropertyService
import dev.atsushieno.ktmidi.ci.PropertyMetadata

class PropertyValueState(var id: MutableState<String>, val replyHeader: List<Byte>, val body: List<Byte>)

abstract class ObservablePropertyList {

    protected val internalValues = mutableStateListOf<PropertyValueState>()

    val values: List<PropertyValueState>
        get() = internalValues

    abstract fun getPropertyIdFor(header: List<Byte>): String
    abstract fun getMetadataList(): List<PropertyMetadata>?

    fun getProperty(propertyId: String): List<Byte>? = getPropertyValue(propertyId)?.body
    fun getPropertyValue(propertyId: String): PropertyValueState? =
        internalValues.firstOrNull { it.id.value == propertyId }

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
    fun updateLocalValue(id: String, data: List<Byte>, isPartial: Boolean) {
        val idx = internalValues.indexOfFirst { it.id.value == id }
        internalValues.removeAt(idx)
        if (isPartial)
            TODO("FIXME: implement")
        val pvs = PropertyValueState(mutableStateOf(id), listOf(), data)
        internalValues.add(idx, pvs)

        valueUpdated.forEach { it(pvs) }
        // resource name could change too
        propertiesCatalogUpdated.forEach { it() }
    }

    fun refreshList() {
        val existing = internalValues.toList()
        internalValues.clear()
        internalValues.addAll(existing)
    }
}

class ClientObservablePropertyList(private val propertyClient: MidiCIPropertyClient)
    : ObservablePropertyList() {
    override fun getPropertyIdFor(header: List<Byte>) = propertyClient.getPropertyIdForHeader(header)

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

    override fun getMetadataList(): List<PropertyMetadata>? = propertyService.getMetadataList()

    override val internalCatalogUpdated: MutableList<() -> Unit>
        get() = propertyService.propertyCatalogUpdated

    fun addMetadata(property: PropertyMetadata) {
        propertyService.addMetadata(property)
        propertiesCatalogUpdated.forEach { it() }
    }

    fun updateMetadata(oldPropertyId: String, property: PropertyMetadata) {
        propertyService.getMetadataList()!!.first { it.resource == oldPropertyId }.updateFrom(property)
    }

    init {
        internalValues.addAll(propertyService.getMetadataList()?.map {
            PropertyValueState(mutableStateOf(it.resource), listOf(), listOf())
        } ?: listOf())
        initializeCatalogUpdatedEvent()
    }
}
