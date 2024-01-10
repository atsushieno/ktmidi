package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen() {
    Column {
        Row {
            Checkbox(ViewModel.settings.workaroundJUCEProfileNumChannelsIssue.value,
                { ViewModel.settings.workaroundJUCEProfileNumChannelsIssue(it) })
            Column {
                Text("Workaround JUCE issue on Profile Configuration Addressing")
                Text(
                    "JUCE 7.0.9 has a bug that it fills `1` for 'numChannelsRequested' field even for 0x7E (group) and 0x7F (function block) that are supposed to be `0` by MIDI-CI v1.2 specification (section 7.8). It should be already fixed in the next JUCE release.",
                    fontSize = 12.sp
                )
            }
        }
    }
}