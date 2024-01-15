package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.atsushieno.ktmidi.MidiPortDetails
import dev.atsushieno.ktmidi.citool.AppModel

@Composable
fun MidiDeviceSelector() {
    Column(Modifier.padding(10.dp)) {
        Text("MIDI Transport Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("By default, it receives MIDI-CI requests on the Virtual In port and sends replies back from the Virtual Out port.")
        Text("But you can also use system MIDI devices as the transports too. Select them here then.")
        Row {
            var inputDevice by remember { mutableStateOf(AppModel.midiDeviceManager.midiInput.details) }
            var outputDevice by remember { mutableStateOf(AppModel.midiDeviceManager.midiOutput.details) }
            MidiDeviceSelector(true, AppModel.midiDeviceManager.midiAccess.inputs.toList(), inputDevice, portChanged = { id ->
                val newDevice = AppModel.midiDeviceManager.midiAccess.inputs.first { it.id == id }
                AppModel.setInputDevice(id)
                inputDevice = newDevice
            })
            MidiDeviceSelector(false, AppModel.midiDeviceManager.midiAccess.outputs.toList(), outputDevice, portChanged = { id ->
                val newDevice = AppModel.midiDeviceManager.midiAccess.outputs.first { it.id == id }
                AppModel.setOutputDevice(id)
                outputDevice = newDevice
            })
        }
    }
}

@Composable
private fun MidiDeviceSelector(isInput: Boolean, ports: List<MidiPortDetails>, currentPort: MidiPortDetails?, portChanged: (id: String) -> Unit) {
    var dialogState by remember { mutableStateOf(false) }

    DropdownMenu(expanded = dialogState, onDismissRequest = { dialogState = false}) {
        val onClick: (String) -> Unit = { id ->
            portChanged(id)
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
            dialogState = true
        }).padding(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Text(modifier = Modifier.padding(12.dp, 0.dp), text = currentPort?.name ?: "-- Select MIDI ${if (isInput) "Input" else "Output"} --")
    }
}