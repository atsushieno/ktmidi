package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType

@Composable
fun SettingsScreen() {
    Row {
        Checkbox(ViewModel.settings.workaroundJUCEProfileNumChannelsIssue.value,
            { ViewModel.settings.workaroundJUCEProfileNumChannelsIssue.value = it })
        Column {
            Text("Workaround JUCE issue on Profile Configuration Addressing")
            Text("JUCE has a bug that it fills `1` for 'numChannelsRequested' field even for 0x7E (group) and 0x7F (function block) that are supposed to be `0` by MIDI-CI v1.2 specification (section 7.8)",
                fontSize = TextUnit(0.8f, TextUnitType.Em)
            )
        }
    }
}