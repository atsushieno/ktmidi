package dev.atsushieno.ktmidi.citool.view

import androidx.compose.runtime.mutableStateOf
import dev.atsushieno.ktmidi.ci.MidiCIInitiator
import dev.atsushieno.ktmidi.citool.AppModel

object ViewModel {
    var logText = mutableStateOf("")

    var selectedRemoteDevice = mutableStateOf(0)

    init {
        // When a new entry is appeared and nothing was selected, move to the new entry.
        AppModel.ciDeviceManager.initiator.initiator.connectionsChanged.add { change, conn ->
            if (selectedRemoteDevice.value == 0 && change == MidiCIInitiator.ConnectionChange.Added)
                selectedRemoteDevice.value = conn.muid
        }
    }
}