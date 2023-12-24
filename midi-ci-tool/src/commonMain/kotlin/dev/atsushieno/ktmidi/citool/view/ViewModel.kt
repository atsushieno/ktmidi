package dev.atsushieno.ktmidi.citool.view

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.citool.AppModel

object ViewModel {
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

    init {
        // When a new entry is appeared and nothing was selected, move to the new entry.
        AppModel.ciDeviceManager.initiator.initiator.connectionsChanged.add { change, conn ->
            if (selectedRemoteDeviceMUID.value == 0 && change == MidiCIInitiator.ConnectionChange.Added)
                Snapshot.withMutableSnapshot { selectedRemoteDeviceMUID.value = conn.muid }
        }
    }
}

class ConnectionViewModel(val conn: MidiCIInitiator.Connection) {
    fun selectProfile(profile: MidiCIProfileId) {
        Snapshot.withMutableSnapshot { selectedProfile.value = profile }
    }

    var selectedProfile = mutableStateOf<MidiCIProfileId?>(null)

    val profiles = mutableStateListOf<MidiCIProfile>().apply {
        addAll(conn.profiles.profiles)
    }

    val properties = mutableStateListOf<ObservablePropertyList.Entry>().apply { addAll(conn.properties.entries)}

    init {
        conn.profiles.profilesChanged.add { change, profile ->
            when (change) {
                ObservableProfileList.ProfilesChange.Added -> profiles.add(profile)
                ObservableProfileList.ProfilesChange.Removed -> profiles.remove(profile)
            }
        }
        conn.properties.propertiesCatalogUpdated.add {
            properties.clear()
            properties.addAll(conn.properties.entries)
        }
    }
}
