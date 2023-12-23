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

    var serial = mutableStateOf(0)

    val enabledProfiles = mutableStateListOf<MidiCIProfileId>().apply { addAll(conn.profiles.enabledProfiles) }
    val disabledProfiles = mutableStateListOf<MidiCIProfileId>().apply { addAll(conn.profiles.disabledProfiles) }

    val properties = mutableStateListOf<ObservablePropertyList.Entry>().apply { addAll(conn.properties.entries)}

    init {
        conn.profiles.profilesChanged.add { change, profile, enabled ->
            val target = if (enabled) enabledProfiles else disabledProfiles
            when (change) {
                ObservableProfileList.ProfilesChange.Added -> target.add(profile)
                ObservableProfileList.ProfilesChange.Removed -> target.remove(profile)
            }
        }
        conn.properties.propertiesCatalogUpdated.add {
            properties.clear()
            properties.addAll(conn.properties.entries)
        }
    }
}