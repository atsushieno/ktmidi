package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import dev.atsushieno.ktmidi.citool.AppModel

@Composable
fun App() {
    MaterialTheme {
        Scaffold {
            MainContent()
        }
    }
}

@Composable
fun MainContent() {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        var tabIndex by remember { mutableStateOf(0) }

        val tabs = listOf("Initiator", "Responder", "Logs", "Settings")

        Column(modifier = Modifier.fillMaxWidth()) {
            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(text = { Text(title) },
                        selected = tabIndex == index,
                        onClick = {
                            when (index) {
                                0 -> AppModel.ciDeviceManager.isResponder = false
                                1 -> AppModel.ciDeviceManager.isResponder = true
                                else -> {}
                            }
                            tabIndex = index },
                        icon = {
                            when (index) {
                                0 -> Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null)
                                1 -> Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null)
                                2 -> Icon(imageVector = Icons.Default.List, contentDescription = null)
                                3 -> Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                            }
                        }
                    )
                }
            }
            when (tabIndex) {
                0 -> InitiatorScreen()
                1 -> ResponderScreen()
                2 -> LogScreen()
                3 -> SettingsScreen()
            }
        }
    }
}

@Composable
fun ResponderScreen() {
    Text("It receives MIDI-CI requests on the Virtual In port and sends replies back from the Virtual Out port")
}

@Composable
fun LogScreen() {
    val logText = remember { ViewModel.log }

    TextField(logText.value, onValueChange = { _: String -> }, readOnly = true)
}

@Composable
fun SettingsScreen() {
    Text("TODO")
}