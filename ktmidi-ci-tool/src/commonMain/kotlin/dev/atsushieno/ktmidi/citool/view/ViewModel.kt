package dev.atsushieno.ktmidi.citool.view

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.ci.LogEntry
import dev.atsushieno.ktmidi.ci.propertycommonrules.CommonRulesPropertyMetadata
import dev.atsushieno.ktmidi.citool.*
import kotlin.random.Random

val ViewModel by lazy { RootViewModel(AppModel) }

class RootViewModel(repository: CIToolRepository) {
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

class InitiatorViewModel(val device: CIDeviceModel) {
    private val ciDeviceManager: CIDeviceManager
        get() = device.parent.owner.ciDeviceManager

    fun sendDiscovery() {
        ciDeviceManager.device.sendDiscovery()
    }

    var selectedRemoteDeviceMUID = mutableStateOf(0)
    val selectedRemoteDevice = derivedStateOf {
        val conn = ciDeviceManager.device.connections.firstOrNull { it.conn.targetMUID == selectedRemoteDeviceMUID.value }
        if (conn != null) ConnectionViewModel(conn) else null
    }

    val connections = mutableStateListOf<ConnectionViewModel>()

    init {
        device.device.connectionsChanged.add { change, item ->
            when (change) {
                ConnectionChange.Added -> {
                    val conn = device.connections.first { it.conn == item }
                    connections.add(ConnectionViewModel(conn))

                    // When a new entry is appeared and nothing was selected, move to the new entry.
                    if (selectedRemoteDeviceMUID.value == 0)
                        selectedRemoteDeviceMUID.value = conn.conn.targetMUID
                }
                ConnectionChange.Removed -> {
                    connections.removeAll { it.conn.conn == item }
                }
            }
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
        ciDeviceManager.device.sendProfileDetailsInquiry(address, conn.conn.targetMUID, profile, target)
    }

    fun selectProperty(propertyId: String) {
        Snapshot.withMutableSnapshot { selectedProperty.value = propertyId }
        val metadata = conn.conn.propertyClient.getMetadataList()?.firstOrNull { it.propertyId == propertyId }
                as CommonRulesPropertyMetadata
        conn.getPropertyData(conn.conn.targetMUID, propertyId,
            encoding = metadata.encodings.firstOrNull(), paginateOffset = 0, paginateLimit = 10)
    }

    var selectedProperty = mutableStateOf<String?>(null)

    fun refreshPropertyValue(targetMUID: Int, propertyId: String, encoding: String?, paginateOffset: Int?, paginateLimit: Int?) {
        conn.getPropertyData(targetMUID, propertyId, encoding, paginateOffset, paginateLimit)
    }

    fun subscribeProperty(targetMUID: Int, propertyId: String, mutualEncoding: String?) {
        conn.subscribeProperty(targetMUID, propertyId, mutualEncoding)
    }

    fun unsubscribeProperty(targetMUID: Int, propertyId: String) {
        conn.unsubscribeProperty(targetMUID, propertyId)
    }

    fun sendSetPropertyDataRequest(targetMUID: Int, propertyId: String, bytes: List<Byte>, encoding: String?, isPartial: Boolean) {
        conn.setPropertyData(targetMUID, propertyId, bytes, encoding, isPartial)
    }

    fun setProfile(group: Byte, address: Byte, profile: MidiCIProfileId, newEnabled: Boolean, newNumChannelsRequested: Short) {
        conn.setProfile(group, address, profile, newEnabled, newNumChannelsRequested)
    }

    fun requestMidiMessageReport(address: Byte, targetMUID: Int) {
        conn.requestMidiMessageReport(address, targetMUID)
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
    private val device by model::device

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
        val index = properties.indexOfFirst { it.id.value == property.propertyId || it.id.value == oldPropertyId }
        val existing = properties[index]

        // update definition
        model.updatePropertyMetadata(oldPropertyId, property)

        // We need to update the property value list state, as the property ID might have changed.
        if (oldPropertyId != property.propertyId)
            existing.id.value = property.propertyId
        selectedProperty.value = property.propertyId
    }

    fun updatePropertyValue(propertyId: String, data: List<Byte>, isPartial: Boolean) {
        model.updatePropertyValue(propertyId, data, isPartial)
        // It might be partial update, in that case we have to retrieve
        // the partial application result from MidiCIPropertyService processing.
        properties.first { it.id.value == propertyId }.data.value =
            model.localProperties.getPropertyValue(propertyId)?.body ?: listOf()
    }

    fun createNewProperty() {
        val property = CommonRulesPropertyMetadata().apply { resource = "X-${Random.nextInt(9999)}" }
        model.addLocalProperty(property)
        selectedProperty.value = property.resource
    }

    fun removeSelectedProperty() {
        val p = selectedProperty.value ?: return
        selectedProperty.value = null
        model.removeLocalProperty(p)
    }

    var selectedProperty = mutableStateOf<String?>(null)
    val properties by lazy { mutableStateListOf<PropertyValueState>().apply { addAll(device.propertyHost.properties.values.map { PropertyValueState(it) }) } }
    fun getPropertyMetadata(propertyId: String) =
        device.propertyHost.metadataList?.firstOrNull { it.propertyId == propertyId }

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

    fun addTestProfileItems() {
        model.addTestProfileItems()
    }

    fun shutdownSubscription(targetMUID: Int, sp: String) =
        model.shutdownSubscription(targetMUID, sp)

    init {
        device.propertyHost.properties.propertiesCatalogUpdated.add {
            properties.clear()
            properties.addAll(device.propertyHost.properties.values.map { PropertyValueState(it) })
        }
    }
}

class DeviceConfigurationViewModel(private val device: CIDeviceModel, private val config: MidiCIDeviceConfiguration) {
    private val deviceInfo: MidiCIDeviceInfo = config.deviceInfo

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
        config.deviceInfo = deviceInfo
        device.updateDeviceInfo(deviceInfo)
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
    val defaultConfigFile = CIToolRepository.DEFAULT_CONFIG_FILE
    val device = DeviceConfigurationViewModel(repository.ciDeviceManager.device, config)

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
