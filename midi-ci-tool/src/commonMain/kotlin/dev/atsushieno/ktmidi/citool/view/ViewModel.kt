package dev.atsushieno.ktmidi.citool.view

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import dev.atsushieno.ktmidi.ci.MidiCIInitiator
import dev.atsushieno.ktmidi.ci.MidiCIProfileId
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

    init {
        conn.profiles.profilesChanged.add { change, profile, enabled ->
            val target = if (enabled) enabledProfiles else disabledProfiles
            when (change) {
                MidiCIInitiator.ProfileList.ProfilesChange.Added -> target.add(profile)
                MidiCIInitiator.ProfileList.ProfilesChange.Removed -> target.remove(profile)
            }
        }
    }
}