package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.atsushieno.ktmidi.citool.AppModel

@Composable
fun App() {
    MaterialTheme {
        Scaffold {
            MainContent()
        }
    }
}

object FeatureDescription {
    const val responderScreen = "It receives MIDI-CI requests on the Virtual In port and sends replies back from the Virtual Out port"
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
                                1 -> Icon(imageVector = Icons.Default.ArrowBack, contentDescription = FeatureDescription.responderScreen)
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
fun LogScreen() {
    val logText = remember { ViewModel.log }

    TextField(logText.value, onValueChange = { _: String -> }, readOnly = true)
}

