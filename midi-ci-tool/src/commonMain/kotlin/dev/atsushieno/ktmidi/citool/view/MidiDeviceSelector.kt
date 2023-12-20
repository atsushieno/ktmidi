package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.atsushieno.ktmidi.MidiPortDetails
import dev.atsushieno.ktmidi.citool.AppModel

@Composable
fun MidiDeviceSelector() {
    Row {
        MidiDeviceSelector(true, AppModel.midiDeviceManager.midiInput.details, AppModel.midiInputPorts)
        MidiDeviceSelector(false, AppModel.midiDeviceManager.midiOutput.details, AppModel.midiOutputPorts)
    }
}

@Composable
private fun MidiDeviceSelector(isInput: Boolean, currentPort: MidiPortDetails?, ports: List<MidiPortDetails>) {
    var dialogState by remember { mutableStateOf(false) }

    DropdownMenu(expanded = dialogState, onDismissRequest = { dialogState = false}) {
        val onClick: (String) -> Unit = { id ->
            if (id.isNotEmpty()) {
                if (isInput)
                    AppModel.setInputDevice(id)
                else
                    AppModel.setOutputDevice(id)
            }
            dialogState = false
        }
        if (ports.any())
            for (d in ports)
                DropdownMenuItem(onClick = { onClick(d.id) }, text = {
                    Text(d.name ?: "(unnamed)")
                })
        else
            DropdownMenuItem(onClick = { onClick("") }, text = { Text("(no MIDI port)") })
        DropdownMenuItem(onClick = { onClick("") }, text = { Text("(Cancel)") })
    }
    Card(
        modifier = Modifier.clickable(onClick = {
            AppModel.updateMidiDeviceList()
            dialogState = true
        }).padding(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Text(currentPort?.name ?: "-- Select MIDI ${if (isInput) "Input" else "Output"} --")
    }
}