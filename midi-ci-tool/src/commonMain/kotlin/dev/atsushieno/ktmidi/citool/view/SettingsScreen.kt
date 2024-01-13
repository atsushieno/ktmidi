package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import getPlatform

@Composable
fun SettingsScreen(vm: ApplicationSettingsViewModel) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        MidiDeviceSelector()
        LocalDeviceConfiguration(vm.device)

        Row {
            Checkbox(vm.workaroundJUCEProfileNumChannelsIssue.value,
                { vm.workaroundJUCEProfileNumChannelsIssue(it) })
            Column {
                Text("Workaround JUCE issue on Profile Configuration Addressing")
                Text(
                    "JUCE 7.0.9 has a bug that it fills `1` for 'numChannelsRequested' field even for 0x7E (group) and 0x7F (function block) that are supposed to be `0` by MIDI-CI v1.2 specification (section 7.8). It should be already fixed in the next JUCE release.",
                    fontSize = 12.sp
                )
            }
        }

        // State loader/saver
        Button(onClick = { vm.loadSettingsFromDefaultFile() }) {
            Text("Load configuration from midi-ci-tool.settings.json")
        }
        Button(onClick = { vm.saveSettingsFromDefaultFile() }) {
            Text("Save configuration to midi-ci-tool.settings.json")
        }
        var showFilePicker by remember { mutableStateOf(false) }
        if (getPlatform().canReadLocalFile) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { showFilePicker = !showFilePicker }) {
                    Text("Load configuration from file")
                }
                getPlatform().BinaryFilePicker(showFilePicker) { file ->
                    showFilePicker = false
                    if (file != null) {
                        vm.loadSettingsFile(file)
                    }
                }
            }
        }
    }
}