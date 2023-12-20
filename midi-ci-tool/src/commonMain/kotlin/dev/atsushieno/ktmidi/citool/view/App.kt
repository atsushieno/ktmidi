package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.atsushieno.ktmidi.citool.AppModel

@Composable
fun App() {
    MaterialTheme {

        var tabIndex by remember { mutableStateOf(0) }

        val tabs = listOf("Initiator", "Responder", "Settings")

        Column(modifier = Modifier.fillMaxWidth()) {
            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(text = { Text(title) },
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        icon = {
                            when (index) {
                                0 -> Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null)
                                1 -> Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null)
                                2 -> Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                            }
                        }
                    )
                }
            }
            when (tabIndex) {
                0 -> InitiatorScreen()
                1 -> ResponderScreen()
                2 -> SettingsScreen()
            }
        }
    }
}

@Composable
fun InitiatorScreen() {
    Column {
        Button(onClick = { AppModel.midiDeviceManager.initiator.sendDiscovery()}) {
            Text("Send Discovery")
        }

        AppModel.midiDeviceManager.isResponder = false
        MidiDeviceSelector()
    }
}

@Composable
fun ResponderScreen() {
    Text("It receives MIDI-CI requests on the Virtual In port and sends replies back from the Virtual Out port")

    AppModel.midiDeviceManager.isResponder = true
    val logText = remember { ViewModel.logText }

    TextField(logText.value, onValueChange = { _: String -> }, readOnly = true)
}

@Composable
fun SettingsScreen() {
    Text("TODO")
}