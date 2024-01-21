package dev.atsushieno.ktmidi.citool.view

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.ci.LogEntry
import dev.atsushieno.ktmidi.citool.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

object ViewHelper {
    private val uiScope = CoroutineScope(Dispatchers.Main)

    fun runInUIContext(function: () -> Unit) {
        // FIXME: It is not very ideal, but we run some non-UI code in UI context...
        // Compose Compiler cannot seem to track JNI invocations.
        // We use RtMidi input callbacks that runs JVM code through its C(++) callbacks.
        // As a consequence, the Compose Compiler does not wrap any access to the composable code with
        // UI thread dispatcher, and Awt threading error occurs on desktop.
        // Any MidiAccess implementation could involve JNI invocations (e.g. AlsaMidiAccess) and
        // the same kind of problem would occur.
        // (You can remove `uiScope.launch {...}` wrapping part to replicate the issue.
        try {
            uiScope.launch { function() }
        } catch(ex: Exception) {
            ex.printStackTrace() // try to give full information, not wrapped by javacpp_Exception (C++, that hides everything)
            throw ex
        }
    }
}

val ViewModel = RootViewModel(AppModel)

class RootViewModel(private val repository: CIToolRepository) {
    val logs = mutableStateListOf<LogEntry>()
    fun clearLogs() {
        logs.clear()
    }

    val initiator = InitiatorViewModel(repository.ciDeviceManager.device)

    val responder = ResponderViewModel(repository.ciDeviceManager.device)

    val settings = ApplicationSettingsViewModel(repository, repository.device)

    init {
        repository.logRecorded += { logs.add(it) }
    }
}

class MidiCIProfileState(
    var group: MutableState<Byte>,
    var address: MutableState<Byte>,
    val profile: MidiCIProfileId,
    val enabled: MutableState<Boolean>,
    val numChannelsRequested: MutableState<Short>
)

class InitiatorViewModel(val device: CIDeviceModel) {
    private val ciDeviceManager: CIDeviceManager
        get() = device.parent.owner.ciDeviceManager

    fun sendDiscovery() {
        ciDeviceManager.device.sendDiscovery()
    }

    var selectedRemoteDeviceMUID = mutableStateOf(0)
    val selectedRemoteDevice = derivedStateOf {
        val conn = ciDeviceManager.initiator.connections.firstOrNull { it.conn.targetMUID == selectedRemoteDeviceMUID.value }
        if (conn != null) ConnectionViewModel(conn) else null
    }

    val connections = mutableStateListOf<ConnectionViewModel>()

    init {
        device.device.connectionsChanged.add { change, item ->
            when (change) {
                ConnectionChange.Added -> {
                    val conn = device.initiator.connections.first { it.conn == item }
                    connections.add(ConnectionViewModel(conn))
                }
                ConnectionChange.Removed -> {
                    connections.removeAll { it.conn.conn == item }
                }
            }
        }
        // When a new entry is appeared and nothing was selected, move to the new entry.
        ciDeviceManager.initiator.initiator.connectionsChanged.add { change, conn ->
            if (selectedRemoteDeviceMUID.value == 0 && change == ConnectionChange.Added)
                selectedRemoteDeviceMUID.value = conn.targetMUID
        }
    }
}

class ConnectionViewModel(val conn: ClientConnectionModel) {
    private val ciDeviceManager: CIDeviceManager
        get() = conn.parent.parent.owner.ciDeviceManager

    fun selectProfile(profile: MidiCIProfileId) {
        Snapshot.withMutableSnapshot { selectedProfile.value = profile }
    }

    var selectedProfile = mutableStateOf<MidiCIProfileId?>(null)

    fun sendProfileDetailsInquiry(profile: MidiCIProfileId, address: Byte, target: Byte) {
        ciDeviceManager.initiator.sendProfileDetailsInquiry(address, conn.conn.targetMUID, profile, target)
    }

    fun selectProperty(propertyId: String) {
        Snapshot.withMutableSnapshot { selectedProperty.value = propertyId }
        ciDeviceManager.initiator.sendGetPropertyDataRequest(conn.conn.targetMUID, propertyId, encoding = null, paginateOffset = 0, paginateLimit = 10)
    }

    var selectedProperty = mutableStateOf<String?>(null)

    fun refreshPropertyValue(targetMUID: Int, propertyId: String, encoding: String?, paginateOffset: Int?, paginateLimit: Int?) {
        ciDeviceManager.initiator.sendGetPropertyDataRequest(targetMUID, propertyId, encoding, paginateOffset, paginateLimit)
    }

    fun sendSubscribeProperty(targetMUID: Int, propertyId: String, mutualEncoding: String?) {
        ciDeviceManager.initiator.sendSubscribeProperty(targetMUID, propertyId, mutualEncoding)
    }

    fun sendUnsubscribeProperty(targetMUID: Int, propertyId: String, mutualEncoding: String?) {
        ciDeviceManager.initiator.sendUnsubscribeProperty(targetMUID, propertyId, mutualEncoding)
    }

    fun sendSetPropertyDataRequest(targetMUID: Int, propertyId: String, bytes: List<Byte>, encoding: String?, isPartial: Boolean) {
        ciDeviceManager.initiator.sendSetPropertyDataRequest(targetMUID, propertyId, bytes, encoding, isPartial)
    }

    fun setProfile(targetMUID: Int, address: Byte, profile: MidiCIProfileId, newEnabled: Boolean, newNumChannelsRequested: Short) {
        ciDeviceManager.initiator.setProfile(targetMUID, address, profile, newEnabled, newNumChannelsRequested)
    }

    fun requestMidiMessageReport(address: Byte, targetMUID: Int) {
        ciDeviceManager.initiator.requestMidiMessageReport(address, targetMUID)
    }
}

class PropertyValueState(val id: MutableState<String>, val mediaType: MutableState<String>, val data: MutableState<List<Byte>>) {
    constructor(id: String, mediaType: String, data: List<Byte>) : this(
        mutableStateOf(id),
        mutableStateOf(mediaType),
        mutableStateOf(data)
    )

    constructor(source: PropertyValue) : this(
        mutableStateOf(source.id),
        mutableStateOf(source.mediaType),
        mutableStateOf(source.body)
    )
}

class ResponderViewModel(val model: CIDeviceModel) {
    private val ciDeviceManager by model::parent
    private val device by model::device
    private val responder by device::responder

    // Profile Configuration
    fun selectProfile(profile: MidiCIProfileId) {
        Snapshot.withMutableSnapshot { selectedProfile.value = profile }
    }

    var selectedProfile = mutableStateOf<MidiCIProfileId?>(null)
    var isSelectedProfileIdEditing = mutableStateOf(false)

    fun selectProperty(propertyId: String) {
        Snapshot.withMutableSnapshot { selectedProperty.value = propertyId }
    }

    fun updatePropertyMetadata(oldPropertyId: String, property: PropertyMetadata) {
        // With the current implementation, we reuse the same PropertyMetadata instance
        // which means the id is already updated.
        // Since it depends on the implementation, we try both `oldPropertyId` and new id here... (there must not be collision in older id)
        val index = properties.indexOfFirst { it.id.value == property.resource || it.id.value == oldPropertyId }
        val existing = properties[index]

        // update definition
        ciDeviceManager.device.device.responder.properties.updateMetadata(oldPropertyId, property)

        // We need to update the property value list state, as the property ID might have changed.
        if (oldPropertyId != property.resource)
            existing.id.value = property.resource
        selectedProperty.value = property.resource
    }

    fun updatePropertyValue(propertyId: String, data: List<Byte>, isPartial: Boolean) {
        responder.updatePropertyValue(model.defaultSenderGroup, propertyId, data, isPartial)
        // It might be partial update, in that case we have to retrieve
        // the partial application result from MidiCIPropertyService processing.
        properties.first { it.id.value == propertyId }.data.value =
            responder.properties.values.first { it.id == propertyId }.body
    }

    fun createNewProperty() {
        val property = PropertyMetadata().apply { resource = "X-${Random.nextInt(9999)}" }
        model.addLocalProperty(property)
        selectedProperty.value = property.resource
    }

    fun removeSelectedProperty() {
        val p = selectedProperty.value ?: return
        selectedProperty.value = null
        model.removeLocalProperty(p)
    }

    var selectedProperty = mutableStateOf<String?>(null)
    val properties by lazy { mutableStateListOf<PropertyValueState>().apply { addAll(responder.properties.values.map { PropertyValueState(it) }) } }
    fun getPropertyMetadata(propertyId: String) =
        responder.propertyService.getMetadataList().firstOrNull { it.resource == propertyId }

    fun addNewProfile(state: MidiCIProfile) {
        model.addLocalProfile(state)
        selectedProfile.value = state.profile
        isSelectedProfileIdEditing.value = true
    }

    fun updateProfileName(oldProfileId: MidiCIProfileId, newProfileId: MidiCIProfileId) {
        model.updateLocalProfileName(oldProfileId, newProfileId)
        isSelectedProfileIdEditing.value = false
        selectedProfile.value = newProfileId
    }

    fun updateProfileTarget(profile: MidiCIProfileState, newAddress: Byte, numChannelsRequested: Short) {
        model.updateLocalProfileTarget(profile, newAddress, profile.enabled.value, numChannelsRequested)
    }

    fun removeProfileTarget(group: Byte, address: Byte, profile: MidiCIProfileId) {
        model.removeLocalProfile(group, address, profile)
        // if the profile ID is gone, then deselect it
        if (model.localProfileStates.all { it.profile != profile })
            selectedProfile.value = null
    }

    fun addNewProfileTarget(state: MidiCIProfile) {
        model.addLocalProfile(state)
    }

    init {
        responder.properties.propertiesCatalogUpdated.add {
            properties.clear()
            properties.addAll(responder.properties.values.map { PropertyValueState(it) })
        }
    }
}

class DeviceConfigurationViewModel(val repository: CIToolRepository, private val config: MidiCIDeviceConfiguration) {
    private val deviceInfo: MidiCIDeviceInfo = config.device

    val maxSimultaneousPropertyRequests =
        mutableStateOf(config.maxSimultaneousPropertyRequests)

    fun updateMaxSimultaneousPropertyRequests(newValue: Byte) {
        config.maxSimultaneousPropertyRequests = newValue
    }

    var manufacturerId = mutableStateOf(deviceInfo.manufacturerId)
    var familyId = mutableStateOf(deviceInfo.familyId)
    var modelId = mutableStateOf(deviceInfo.modelId)
    var versionId = mutableStateOf(deviceInfo.versionId)
    var manufacturer = mutableStateOf(deviceInfo.manufacturer)
    var family = mutableStateOf(deviceInfo.family)
    var model = mutableStateOf(deviceInfo.model)
    var version = mutableStateOf(deviceInfo.version)
    var serialNumber = mutableStateOf(deviceInfo.serialNumber)

    fun updateDeviceInfo(deviceInfo: MidiCIDeviceInfo) {
        config.device = deviceInfo
        repository.ciDeviceManager.device.device.responder.propertyService.deviceInfo = deviceInfo
        this.manufacturerId.value = deviceInfo.manufacturerId
        this.familyId.value = deviceInfo.familyId
        this.modelId.value = deviceInfo.modelId
        this.versionId.value = deviceInfo.versionId
        this.manufacturer.value = deviceInfo.manufacturer
        this.family.value = deviceInfo.family
        this.model.value = deviceInfo.model
        this.version.value = deviceInfo.version
        this.serialNumber.value = deviceInfo.serialNumber
    }
}

class ApplicationSettingsViewModel(val repository: CIToolRepository, config: MidiCIDeviceConfiguration) {
    val defaultConfigFile = CIToolRepository.defaultConfigFile
    val device = DeviceConfigurationViewModel(repository, config)

    val workaroundJUCEMissingSubscriptionIdIssue = mutableStateOf(
        ImplementationSettings.workaroundJUCEMissingSubscriptionIdIssue)
    fun workaroundJUCEMissingSubscriptionIdIssue(value: Boolean) {
        ImplementationSettings.workaroundJUCEMissingSubscriptionIdIssue = value
        workaroundJUCEMissingSubscriptionIdIssue.value = value
    }

    val workaroundJUCEProfileNumChannelsIssue = mutableStateOf(
        ImplementationSettings.workaroundJUCEProfileNumChannelsIssue)
    fun workaroundJUCEProfileNumChannelsIssue(value: Boolean) {
        ImplementationSettings.workaroundJUCEProfileNumChannelsIssue = value
        workaroundJUCEProfileNumChannelsIssue.value = value
    }

    fun loadSettingsFile(file: String) {
        repository.loadConfig(file)
    }

    fun saveSettingsFile(file: String) {
        repository.saveConfig(file)
    }

    fun loadSettingsFromDefaultFile() {
        repository.loadConfigDefault()
    }

    fun saveSettingsFromDefaultFile() {
        repository.saveConfigDefault()
    }
}
