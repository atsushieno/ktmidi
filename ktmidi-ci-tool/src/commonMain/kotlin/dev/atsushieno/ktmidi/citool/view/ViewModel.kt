package dev.atsushieno.ktmidi.citool.view

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import dev.atsushieno.ktmidi.ci.ImplementationSettings
import dev.atsushieno.ktmidi.ci.MidiCIDeviceConfiguration
import dev.atsushieno.ktmidi.ci.MidiCIDeviceInfo
import dev.atsushieno.ktmidi.ci.MidiCIInitiator
import dev.atsushieno.ktmidi.ci.MidiCIProfile
import dev.atsushieno.ktmidi.ci.MidiCIProfileId
import dev.atsushieno.ktmidi.ci.MidiCIResponder
import dev.atsushieno.ktmidi.ci.ObservableProfileList
import dev.atsushieno.ktmidi.ci.PropertyMetadata
import dev.atsushieno.ktmidi.ci.PropertyValue
import dev.atsushieno.ktmidi.citool.AppModel
import dev.atsushieno.ktmidi.citool.LogEntry
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

object ViewModel {
    val logs = mutableStateListOf<LogEntry>()
    fun clearLogs() {
        logs.clear()
    }

    val initiator = InitiatorViewModel()

    val responder = ResponderViewModel(AppModel.ciDeviceManager.responder.responder)

    val settings = ApplicationSettingsViewModel(AppModel.common)

    init {
        AppModel.logRecorded += { logs.add(it) }
    }
}

class MidiCIProfileState(
    var address: MutableState<Byte>, val profile: MidiCIProfileId, val enabled: MutableState<Boolean> = mutableStateOf(false))

class InitiatorViewModel {
    fun sendDiscovery() {
        AppModel.ciDeviceManager.initiator.sendDiscovery()
    }

    val connections by AppModel.ciDeviceManager.initiator.initiator::connections

    var selectedRemoteDeviceMUID = mutableStateOf(0)
    val selectedRemoteDevice = derivedStateOf {
        val conn = AppModel.ciDeviceManager.initiator.initiator.connections[selectedRemoteDeviceMUID.value]
        if (conn != null) ConnectionViewModel(conn) else null
    }

    init {
        // When a new entry is appeared and nothing was selected, move to the new entry.
        AppModel.ciDeviceManager.initiator.initiator.connectionsChanged.add { change, conn ->
            if (selectedRemoteDeviceMUID.value == 0 && change == MidiCIInitiator.ConnectionChange.Added)
                Snapshot.withMutableSnapshot { selectedRemoteDeviceMUID.value = conn.targetMUID }
        }
    }
}

class ConnectionViewModel(val conn: MidiCIInitiator.Connection) {
    fun selectProfile(profile: MidiCIProfileId) {
        Snapshot.withMutableSnapshot { selectedProfile.value = profile }
    }

    var selectedProfile = mutableStateOf<MidiCIProfileId?>(null)

    val profiles = mutableStateListOf<MidiCIProfileState>().apply {
        addAll(conn.profiles.profiles.map { MidiCIProfileState(mutableStateOf(it.address), it.profile, mutableStateOf(it.enabled)) })
    }

    fun sendProfileDetailsInquiry(profile: MidiCIProfileId, address: Byte, target: Byte) {
        AppModel.ciDeviceManager.initiator.sendProfileDetailsInquiry(address, conn.targetMUID, profile, target)
    }

    fun selectProperty(propertyId: String) {
        Snapshot.withMutableSnapshot { selectedProperty.value = propertyId }
        AppModel.ciDeviceManager.initiator.sendGetPropertyDataRequest(conn.targetMUID, propertyId, null)
    }

    var selectedProperty = mutableStateOf<String?>(null)

    val properties = mutableStateListOf<PropertyValue>().apply { addAll(conn.properties.values)}

    fun refreshPropertyValue(targetMUID: Int, propertyId: String, encoding: String?) {
        AppModel.ciDeviceManager.initiator.sendGetPropertyDataRequest(targetMUID, propertyId, encoding)
    }

    fun sendSubscribeProperty(targetMUID: Int, propertyId: String, mutualEncoding: String?) {
        AppModel.ciDeviceManager.initiator.sendSubscribeProperty(targetMUID, propertyId, mutualEncoding)
    }

    fun sendSetPropertyDataRequest(targetMUID: Int, propertyId: String, bytes: List<Byte>, encoding: String?, isPartial: Boolean) {
        AppModel.ciDeviceManager.initiator.sendSetPropertyDataRequest(targetMUID, propertyId, bytes, encoding, isPartial)
    }

    fun setProfile(targetMUID: Int, address: Byte, profile: MidiCIProfileId, newEnabled: Boolean) {
        AppModel.ciDeviceManager.initiator.setProfile(targetMUID, address, profile, newEnabled)
    }

    init {
        conn.profiles.profilesChanged.add { change, profile ->
            when (change) {
                ObservableProfileList.ProfilesChange.Added ->
                    profiles.add(MidiCIProfileState(mutableStateOf(profile.address), profile.profile, mutableStateOf(profile.enabled)))
                ObservableProfileList.ProfilesChange.Removed ->
                    profiles.removeAll {it.profile == profile.profile && it.address.value == profile.address }
            }
        }
        conn.profiles.profileEnabledChanged.add { profile, numChannelsRequested ->
            if (numChannelsRequested > 1)
                TODO("FIXME: implement")
            profiles.filter { it.profile == profile.profile && it.address.value == profile.address }
                .forEach { Snapshot.withMutableSnapshot { it.enabled.value = profile.enabled } }
        }

        conn.properties.valueUpdated.add { entry ->
            val index = properties.indexOfFirst { it.id == entry.id }
            if (index < 0)
                properties.add(entry)
            else {
                properties.removeAt(index)
                properties.add(index, entry)
            }
        }

        conn.properties.propertiesCatalogUpdated.add {
            properties.clear()
            properties.addAll(conn.properties.values)
        }
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

// FIXME: replace responder with CIResponderModel. MidiCIResponder should be accessed only via the model in this module.
class ResponderViewModel(private val responder: MidiCIResponder) {
    // Profile Configuration
    fun selectProfile(profile: MidiCIProfileId) {
        Snapshot.withMutableSnapshot { selectedProfile.value = profile }
    }

    var selectedProfile = mutableStateOf<MidiCIProfileId?>(null)
    var isSelectedProfileIdEditing = mutableStateOf(false)
    val profiles = mutableStateListOf<MidiCIProfileState>().apply {
        addAll(responder.profiles.profiles.map {
            MidiCIProfileState(
                mutableStateOf(it.address),
                it.profile,
                mutableStateOf(it.enabled)
            )
        })
    }

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
        responder.properties.updateMetadata(oldPropertyId, property)

        // We need to update the property value list state, as the property ID might have changed.
        val existingList = properties.toList()
        properties.clear()
        properties.addAll(existingList.mapIndexed { idx, it ->
            if (idx == index) PropertyValueState(property.resource, it.mediaType.value, existing.data.value) else it })

        selectedProperty.value = property.resource
    }

    fun updatePropertyValue(propertyId: String, data: List<Byte>, isPartial: Boolean) {
        responder.updatePropertyValue(propertyId, data, isPartial)
        // It might be partial update, in that case we have to retrieve
        // the partial application result from MidiCIPropertyService processing.
        properties.first { it.id.value == propertyId }.data.value =
            responder.properties.values.first { it.id == propertyId }.body
    }

    fun createNewProperty() {
        val property = PropertyMetadata().apply { resource = "Property${Random.nextInt()}" }
        AppModel.ciDeviceManager.responder.addProperty(property)
        selectedProperty.value = property.resource
    }

    fun removeSelectedProperty() {
        val p = selectedProperty.value ?: return
        selectedProperty.value = null
        AppModel.ciDeviceManager.responder.removeProperty(p)
    }

    var selectedProperty = mutableStateOf<String?>(null)
    val properties by lazy { mutableStateListOf<PropertyValueState>().apply { addAll(responder.properties.values.map { PropertyValueState(it) }) } }
    fun getPropertyMetadata(propertyId: String) =
        responder.propertyService.getMetadataList().firstOrNull { it.resource == propertyId }

    fun addNewProfile(state: MidiCIProfile) {
        AppModel.ciDeviceManager.responder.addProfile(state)
        selectedProfile.value = state.profile
        isSelectedProfileIdEditing.value = true
    }

    fun updateProfileName(oldProfileId: MidiCIProfileId, newProfileId: MidiCIProfileId) {
        AppModel.ciDeviceManager.responder.updateProfileName(oldProfileId, newProfileId)
        isSelectedProfileIdEditing.value = false
        selectedProfile.value = newProfileId
    }

    fun updateProfileTarget(profile: MidiCIProfileState, newAddress: Byte, numChannelsRequested: Short) {
        AppModel.ciDeviceManager.responder.updateProfileTarget(profile, newAddress, profile.enabled.value, numChannelsRequested)
    }

    fun removeProfileTarget(address: Byte, profile: MidiCIProfileId) {
        AppModel.ciDeviceManager.responder.removeProfile(address, profile)
        // if the profile ID is gone, then deselect it
        if (profiles.all { it.profile != profile })
            selectedProfile.value = null
    }

    fun addNewProfileTarget(state: MidiCIProfile) {
        AppModel.ciDeviceManager.responder.addProfile(state)
    }

    init {
        responder.profiles.profilesChanged.add { change, profile ->
            when (change) {
                ObservableProfileList.ProfilesChange.Added ->
                    profiles.add(MidiCIProfileState(mutableStateOf(profile.address), profile.profile, mutableStateOf(profile.enabled)))
                ObservableProfileList.ProfilesChange.Removed ->
                    profiles.removeAll { it.profile == profile.profile && it.address.value == profile.address }
            }
        }
        responder.profiles.profileUpdated.add { profileId: MidiCIProfileId, oldAddress: Byte, newEnabled: Boolean, newAddress: Byte, numChannelsRequested: Short ->
            val entry = profiles.first { it.profile == profileId && it.address.value == oldAddress }
            if (numChannelsRequested > 1)
                TODO("FIXME: implement")
            entry.address.value = newAddress
            entry.enabled.value = newEnabled
        }
        responder.profiles.profileEnabledChanged.add { profile, numChannelsRequested ->
            if (numChannelsRequested > 1)
                TODO("FIXME: implement")
            val dst = profiles.first { it.profile == profile.profile && it.address.value == profile.address }
            dst.enabled.value = profile.enabled
        }

        responder.properties.propertiesCatalogUpdated.add {
            properties.clear()
            properties.addAll(responder.properties.values.map { PropertyValueState(it) })
        }
    }
}

class DeviceConfigurationViewModel(private val config: MidiCIDeviceConfiguration) {
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
        AppModel.ciDeviceManager.responder.responder.propertyService.deviceInfo = deviceInfo
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

class ApplicationSettingsViewModel(config: MidiCIDeviceConfiguration) {
    val defaultConfigFile = AppModel.defaultConfigFile
    val device = DeviceConfigurationViewModel(config)

    val workaroundJUCEProfileNumChannelsIssue = mutableStateOf(false)
    fun workaroundJUCEProfileNumChannelsIssue(value: Boolean) {
        ImplementationSettings.workaroundJUCEProfileNumChannelsIssue = value
        workaroundJUCEProfileNumChannelsIssue.value = value
    }

    fun loadSettingsFile(file: String) {
        AppModel.loadConfig(file)
    }

    fun saveSettingsFile(file: String) {
        AppModel.saveConfig(file)
    }

    fun loadSettingsFromDefaultFile() {
        AppModel.loadConfigDefault()
    }

    fun saveSettingsFromDefaultFile() {
        AppModel.saveConfigDefault()
    }
}
