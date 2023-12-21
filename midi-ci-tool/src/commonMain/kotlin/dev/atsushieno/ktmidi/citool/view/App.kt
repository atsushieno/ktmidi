package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        Row {
            Button(onClick = { AppModel.ciDeviceManager.initiator.sendDiscovery()}) {
                Text("Send Discovery")
            }
            MidiDeviceSelector()
        }
        val destinationMUID by remember { ViewModel.selectedRemoteDevice }
        InitiatorDestinationSelector(destinationMUID,
            onChange = { ViewModel.selectedRemoteDevice.value = it })

        val conn = AppModel.ciDeviceManager.initiator.initiator.connections[destinationMUID]
        if (conn != null) {
            Text("Manufacturer: ${conn.device.manufacturer.toString(16)}")
            Text("Family: ${conn.device.family.toString(16)}")
            Text("ModelNumber: ${conn.device.modelNumber.toString(16)}")
            Text("RevisionLevel: ${conn.device.softwareRevisionLevel.toString(16)}")
            Text("ProductInstanceId: ${conn.productInstanceId}")
            Text("maxSimultaneousPropertyRequests: ${conn.maxSimultaneousPropertyRequests}")
        }
    }
}

@Composable
private fun InitiatorDestinationSelector(destinationMUID: Int,
                                         onChange: (Int) -> Unit) {
    var dialogState by remember { mutableStateOf(false) }

    DropdownMenu(expanded = dialogState, onDismissRequest = { dialogState = false}) {
        val onClick: (Int) -> Unit = { muid ->
            if (muid != 0)
                onChange(muid)
            dialogState = false
        }
        if (AppModel.ciDeviceManager.initiator.initiator.connections.any())
            AppModel.ciDeviceManager.initiator.initiator.connections.toList().forEachIndexed { index, conn ->
                DropdownMenuItem(onClick = { onClick(conn.first) }, text = {
                    Text(conn.first.toString())
                })
            }
        else
            DropdownMenuItem(onClick = { onClick(0) }, text = { Text("(no CI Device)") })
        DropdownMenuItem(onClick = { onClick(0) }, text = { Text("(Cancel)") })
    }
    Card(
        modifier = Modifier.clickable(onClick = {
            dialogState = true
        }).padding(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Text(if (destinationMUID != 0) destinationMUID.toString() else "-- Select CI Device --")
    }
}

@Composable
fun ResponderScreen() {
    Text("It receives MIDI-CI requests on the Virtual In port and sends replies back from the Virtual Out port")

    AppModel.ciDeviceManager.isResponder = true
    val logText = remember { ViewModel.logText }

    TextField(logText.value, onValueChange = { _: String -> }, readOnly = true)
}

@Composable
fun SettingsScreen() {
    Text("TODO")
}