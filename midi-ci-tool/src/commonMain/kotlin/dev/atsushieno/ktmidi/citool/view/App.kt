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
        Text("TODO")

        MidiDeviceSelector()
    }
}

@Composable
fun ResponderScreen() {
    Text("TODO")

    val logText = remember { ViewModel.logText }

    TextField(logText.value, onValueChange = { _: String -> }, readOnly = false)
}

@Composable
fun SettingsScreen() {
    Text("TODO")
}