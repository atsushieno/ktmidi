package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.atsushieno.ktmidi.citool.AppModel
import kotlinx.coroutines.launch

@Composable
fun App() {
    rememberCoroutineScope().launch {
        AppModel.midiDeviceManager.setupVirtualPorts()
    }
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Scaffold {
            MainContent()
        }
    }
}

@Composable
fun MainContent() {
    Column {
        var tabIndex by remember { mutableStateOf(0) }

        val tabs = listOf("Initiator", "Responder", "Logs", "Settings")

        Column(modifier = Modifier.fillMaxWidth()) {
            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(text = { Text(title) },
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        // FIXME: they are gone around Compose Multiplatform 1.9.0
                        /*icon = {
                            when (index) {
                                0 -> Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Go to Initiator Screen")
                                1 -> Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Go to Responder Screen")
                                2 -> Icon(imageVector = Icons.Default.List, contentDescription = "Go to Log Screen")
                                3 -> Icon(imageVector = Icons.Default.Settings, contentDescription = "Go to Settings Screen")
                            }
                        }*/
                    )
                }
            }
            when (tabIndex) {
                0 -> InitiatorScreen(ViewModel.initiator)
                1 -> ResponderScreen(ViewModel.responder)
                2 -> LogScreen()
                3 -> SettingsScreen(ViewModel.settings)
            }
        }
    }
}

