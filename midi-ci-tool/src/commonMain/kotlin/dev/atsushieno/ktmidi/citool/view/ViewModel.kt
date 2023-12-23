package dev.atsushieno.ktmidi.citool.view

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import dev.atsushieno.ktmidi.ci.MidiCIInitiator
import dev.atsushieno.ktmidi.ci.MidiCIProfileId
import dev.atsushieno.ktmidi.ci.ObservableProfileList
import dev.atsushieno.ktmidi.ci.ObservablePropertyList
import dev.atsushieno.ktmidi.citool.AppModel

object ViewModel {
    var logText = mutableStateOf("")

    var selectedRemoteDeviceMUID = mutableStateOf(0)
    val selectedRemoteDevice = derivedStateOf {
        val conn = AppModel.ciDeviceManager.initiator.initiator.connections[selectedRemoteDeviceMUID.value]
        if (conn != null) ConnectionViewModel(conn) else null
    }

    init {
        // When a new entry is appeared and nothing was selected, move to the new entry.
        AppModel.ciDeviceManager.initiator.initiator.connectionsChanged.add { change, conn ->
            if (selectedRemoteDeviceMUID.value == 0 && change == MidiCIInitiator.ConnectionChange.Added)
                selectedRemoteDeviceMUID.value = conn.muid
        }
    }
}

class ConnectionViewModel(val conn: MidiCIInitiator.Connection) {
    fun selectProfile(profile: MidiCIProfileId) {
        selectedProfile.value = profiles.firstOrNull { it.profileId.toString() == profile.toString() }
    }

    var selectedProfile = mutableStateOf<MidiClientProfileViewModel?>(null)

    val profiles = mutableStateListOf<MidiClientProfileViewModel>().apply {
        addAll(conn.profiles.profiles.map {
            MidiClientProfileViewModel(this@ConnectionViewModel, it.first, it.second)
        })
    }

    val properties = mutableStateListOf<ObservablePropertyList.Entry>().apply { addAll(conn.properties.entries)}

    init {
        conn.profiles.profilesChanged.add { change, profile, enabled ->
            when (change) {
                ObservableProfileList.ProfilesChange.Added -> profiles.add(MidiClientProfileViewModel(this, profile, enabled))
                ObservableProfileList.ProfilesChange.Removed -> profiles.removeAll { it.profileId.toString() == profile.toString() }
            }
        }
        conn.properties.propertiesCatalogUpdated.add {
            properties.clear()
            properties.addAll(conn.properties.entries)
        }
    }
}

class MidiClientProfileViewModel(private val parent: ConnectionViewModel, val profileId: MidiCIProfileId, enabled: Boolean) {
    val enabled = mutableStateOf(enabled)
}
