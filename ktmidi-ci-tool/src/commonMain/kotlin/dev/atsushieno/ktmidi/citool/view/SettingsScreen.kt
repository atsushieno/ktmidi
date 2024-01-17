package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import getPlatform

@Composable
fun SettingsScreen(vm: ApplicationSettingsViewModel) {
    Column(Modifier.verticalScroll(rememberScrollState())
        .padding(10.dp)) {
        LoadAndSave(vm)
        MidiDeviceSelector()
        LocalDeviceConfiguration(vm.device)
        BehavioralSettings(vm)
    }
}

@Composable
fun LoadAndSave(vm: ApplicationSettingsViewModel) {
    Column(Modifier.padding(10.dp)) {
        Text("Load and Save", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Configuration file is '${vm.defaultConfigFile}'")

        Row {
            Button(onClick = { vm.loadSettingsFromDefaultFile() }) {
                Text("Load configuration")
            }
            Button(onClick = { vm.saveSettingsFromDefaultFile() }) {
                Text("Save configuration")
            }
        }

        /*
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
        */
    }
}

@Composable
fun BehavioralSettings(vm: ApplicationSettingsViewModel) {
    Column(Modifier.padding(10.dp)) {
        Text("Miscellaneous Behavioral Configuration", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Row {
            Checkbox(vm.workaroundJUCEMissingSubscriptionIdIssue.value,
                { vm.workaroundJUCEMissingSubscriptionIdIssue(it) })
            Column {
                Text("Workaround JUCE issue on missing 'subscribeId' header field in SubscriptionReply")
                Text(
                    """
                    JUCE 7.0.9 has a bug that it does not assign "subscribeId" field in Reply to Subscription message,
                    which is required by Common Rules for MIDI-CI Property Extension specification (section 9.1).
                    Therefore, it is impossible for a subscriber to "end" Subscription because the client never knows
                    the corresponding subscribeId, unless it assigned it at start time.
                    """.trimIndent(),
                    fontSize = 12.sp
                )
            }
        }
        Row {
            Checkbox(vm.workaroundJUCEProfileNumChannelsIssue.value,
                { vm.workaroundJUCEProfileNumChannelsIssue(it) })
            Column {
                Text("Workaround JUCE issue on Profile Configuration Addressing")
                Text("""
                    JUCE 7.0.9 has a bug that it fills `1` for 'numChannelsRequested' field even for 0x7E (group)
                    and 0x7F (function block) that are supposed to be `0` by MIDI-CI v1.2 specification (section 7.8).
                    It should be already fixed in the next JUCE release.
                    """.trimIndent(),
                    fontSize = 12.sp
                )
            }
        }
    }
}