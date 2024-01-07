package dev.atsushieno.ktmidi.citool.view

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.citool.AppModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ViewModel {
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

    private var logText = mutableStateOf("")

    val log: MutableState<String>
        get() = logText
    fun log(msg: String) {
        Snapshot.withMutableSnapshot { logText.value += msg + (if (msg.endsWith('\n')) "" else "\n") }
    }

    var selectedRemoteDeviceMUID = mutableStateOf(0)
    val selectedRemoteDevice = derivedStateOf {
        val conn = AppModel.ciDeviceManager.initiator.initiator.connections[selectedRemoteDeviceMUID.value]
        if (conn != null) ConnectionViewModel(conn) else null
    }

    val localDeviceConfiguration = LocalConfigurationViewModel(AppModel.ciDeviceManager.responder.responder)

    val settings = ApplicationSetingsViewModel()

    init {
        // When a new entry is appeared and nothing was selected, move to the new entry.
        AppModel.ciDeviceManager.initiator.initiator.connectionsChanged.add { change, conn ->
            if (selectedRemoteDeviceMUID.value == 0 && change == MidiCIInitiator.ConnectionChange.Added)
                Snapshot.withMutableSnapshot { selectedRemoteDeviceMUID.value = conn.muid }
        }
    }
}

class MidiCIProfileState(
    var address: MutableState<Byte>, val profile: MidiCIProfileId, val enabled: MutableState<Boolean> = mutableStateOf(false))

class ConnectionViewModel(val conn: MidiCIInitiator.Connection) {
    fun selectProfile(profile: MidiCIProfileId) {
        Snapshot.withMutableSnapshot { selectedProfile.value = profile }
    }

    var selectedProfile = mutableStateOf<MidiCIProfileId?>(null)

    val profiles = mutableStateListOf<MidiCIProfileState>().apply {
        addAll(conn.profiles.profiles.map { MidiCIProfileState(mutableStateOf(it.address), it.profile, mutableStateOf(it.enabled)) })
    }

    fun selectProperty(propertyId: String) {
        Snapshot.withMutableSnapshot { selectedProperty.value = propertyId }
    }

    var selectedProperty = mutableStateOf<String?>(null)

    val properties = mutableStateListOf<PropertyValue>().apply { addAll(conn.properties.values)}

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

class LocalConfigurationViewModel(val responder: MidiCIResponder) {
    val device = DeviceConfigurationViewModel(responder.device)
    val maxSimultaneousPropertyRequests =
        mutableStateOf(responder.maxSimultaneousPropertyRequests)

    fun updateMaxSimultaneousPropertyRequests(newValue: Byte) {
        responder.maxSimultaneousPropertyRequests = newValue
    }

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
        // update definition
        responder.properties.updateMetadata(oldPropertyId, property)
        // then update property value storage
        //
        // With the current implementation, we reuse the same PropertyMetadata instance
        // which means the id is already updated. So we do not use `oldPropertyId` here...
        val index = properties.indexOfFirst { it.id == property.resource }
        val existing = properties[index]
        properties[index] = PropertyValue(property.resource, existing.replyHeader, existing.body)
        selectedProperty.value = property.resource
    }

    var selectedProperty = mutableStateOf<String?>(null)
    val properties by lazy { mutableStateListOf<PropertyValue>().apply { addAll(responder.properties.values) } }

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
            properties.addAll(responder.properties.values)
        }
    }
}

class DeviceConfigurationViewModel(deviceInfo: MidiCIDeviceInfo) {

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
        AppModel.ciDeviceManager.responder.responder.device = deviceInfo
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

class ApplicationSetingsViewModel {
    var workaroundJUCEProfileNumChannelsIssue = mutableStateOf(false)
}
