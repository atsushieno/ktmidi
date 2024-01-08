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
import dev.atsushieno.ktmidi.ci.ImplementationSettings

@Composable
fun SettingsScreen() {
    Column {
        Row {
            Checkbox(ViewModel.settings.workaroundJUCEProfileNumChannelsIssue.value,
                { ViewModel.settings.workaroundJUCEProfileNumChannelsIssue(it) })
            Column {
                Text("Workaround JUCE issue on Profile Configuration Addressing")
                Text(
                    "JUCE has a bug that it fills `1` for 'numChannelsRequested' field even for 0x7E (group) and 0x7F (function block) that are supposed to be `0` by MIDI-CI v1.2 specification (section 7.8)",
                    fontSize = TextUnit(0.8f, TextUnitType.Em)
                )
            }
        }
        Row {
            Checkbox(ViewModel.settings.workaroundJUCEPropertySubscriptionReplyIssue.value,
                { ViewModel.settings.workaroundJUCEPropertySubscriptionReplyIssue(it) })
            Column {
                Text("Workaround JUCE issue on Property Subscription Reply")
                Text(
                    "JUCE Responder has a bug that it does not expect Property Subscription Reply from Initiator and results in Invalidate MUID",
                    fontSize = TextUnit(0.8f, TextUnitType.Em)
                )
            }
        }
    }
}