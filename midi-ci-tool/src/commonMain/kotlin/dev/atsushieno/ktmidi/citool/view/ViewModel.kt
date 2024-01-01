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

    init {
        // When a new entry is appeared and nothing was selected, move to the new entry.
        AppModel.ciDeviceManager.initiator.initiator.connectionsChanged.add { change, conn ->
            if (selectedRemoteDeviceMUID.value == 0 && change == MidiCIInitiator.ConnectionChange.Added)
                Snapshot.withMutableSnapshot { selectedRemoteDeviceMUID.value = conn.muid }
        }
    }
}

class MidiCIProfileState(
    val address: Byte, val profile: MidiCIProfileId, val enabled: MutableState<Boolean> = mutableStateOf(false))

class ConnectionViewModel(val conn: MidiCIInitiator.Connection) {
    fun selectProfile(profile: MidiCIProfileId) {
        Snapshot.withMutableSnapshot { selectedProfile.value = profile }
    }

    var selectedProfile = mutableStateOf<MidiCIProfileId?>(null)

    val profiles = mutableStateListOf<MidiCIProfileState>().apply {
        addAll(conn.profiles.profiles.map { MidiCIProfileState(it.address, it.profile, mutableStateOf(it.enabled)) })
    }

    fun selectProperty(propertyId: String) {
        Snapshot.withMutableSnapshot { selectedProperty.value = propertyId }
    }

    var selectedProperty = mutableStateOf<String?>(null)

    val properties = mutableStateListOf<ObservablePropertyList.Entry>().apply { addAll(conn.properties.entries)}

    init {
        conn.profiles.profilesChanged.add { change, profile ->
            when (change) {
                ObservableProfileList.ProfilesChange.Added ->
                    profiles.add(MidiCIProfileState(profile.address, profile.profile, mutableStateOf(profile.enabled)))
                ObservableProfileList.ProfilesChange.Updated -> throw IllegalStateException("should not happen")
                ObservableProfileList.ProfilesChange.Removed ->
                    profiles.removeAll {it.profile == profile.profile && it.address == profile.address }
            }
        }
        conn.profiles.profileEnabledChanged.add { profile, numChannelsRequested ->
            if (numChannelsRequested > 1)
                TODO("FIXME: implement")
            profiles.filter { it.profile == profile.profile && it.address == profile.address }
                .forEach { Snapshot.withMutableSnapshot { it.enabled.value = profile.enabled } }
        }

        conn.properties.propertyChanged.add { entry ->
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
            properties.addAll(conn.properties.entries)
        }
    }
}

class LocalConfigurationViewModel(private val responder: MidiCIResponder) {
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
                it.address,
                it.profile,
                mutableStateOf(it.enabled)
            )
        })
    }

    init {
        responder.profiles.profilesChanged.add { change, profile ->
            when (change) {
                ObservableProfileList.ProfilesChange.Added ->
                    profiles.add(MidiCIProfileState(profile.address, profile.profile, mutableStateOf(profile.enabled)))
                ObservableProfileList.ProfilesChange.Updated -> {

                } // FIXME: implement?
                ObservableProfileList.ProfilesChange.Removed ->
                    profiles.removeAll { it.profile == profile.profile && it.address == profile.address }
            }
        }
        responder.profiles.profileEnabledChanged.add { profile, numChannelsRequested ->
            if (numChannelsRequested > 1)
                TODO("FIXME: implement")
            profiles.filter { it.profile == profile.profile && it.address == profile.address }
                .forEach { Snapshot.withMutableSnapshot { it.enabled.value = profile.enabled } }
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