package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import dev.atsushieno.ktmidi.ci.MidiCIProfileId
import dev.atsushieno.ktmidi.citool.AppModel

@Composable
fun InitiatorScreen() {
    Column {
        Row {
            Button(onClick = { AppModel.ciDeviceManager.initiator.sendDiscovery() }) {
                Text("Send Discovery")
            }
            MidiDeviceSelector()
        }
        val destinationMUID by remember { ViewModel.selectedRemoteDeviceMUID }
        InitiatorDestinationSelector(destinationMUID,
            onChange = { Snapshot.withMutableSnapshot { ViewModel.selectedRemoteDeviceMUID.value = it } })

        val conn = ViewModel.selectedRemoteDevice.value
        if (conn != null) {
            ClientConnection(conn)
        }
    }
}

@Composable
fun ClientConnection(vm: ConnectionViewModel) {
    val conn = vm.conn
    Column(Modifier.padding(12.dp, 0.dp)) {
        Text("Device", fontSize = TextUnit(1.5f, TextUnitType.Em), fontWeight = FontWeight.Bold)
        val small = TextUnit(0.8f, TextUnitType.Em)
        Text("Manufacturer: ${conn.device.manufacturer.toString(16)}", fontSize = small)
        Text("Family: ${conn.device.family.toString(16)}", fontSize = small)
        Text("Model: ${conn.device.modelNumber.toString(16)}", fontSize = small)
        Text("Revision: ${conn.device.softwareRevisionLevel.toString(16)}", fontSize = small)
        Text("instance ID: ${conn.productInstanceId}", fontSize = small)
        Text("max connections: ${conn.maxSimultaneousPropertyRequests}")

        Text("Profiles", fontSize = TextUnit(1.5f, TextUnitType.Em), fontWeight = FontWeight.Bold)

        Row {
            ClientProfileList(vm)
            val selectedProfile by remember { vm.selectedProfile }
            val sp = selectedProfile
            if (sp != null)
                ClientProfileDetails(sp)
        }

        Text("Properties", fontSize = TextUnit(1.5f, TextUnitType.Em), fontWeight = FontWeight.Bold)

        vm.properties.forEach {
            Row {
                Text(it.id)
            }
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
                    Text(modifier = Modifier.padding(12.dp, 0.dp), text = conn.first.toString())
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
        Text(modifier = Modifier.padding(12.dp, 0.dp),
            text = if (destinationMUID != 0) destinationMUID.toString() else "-- Select CI Device --")
    }
}

@Composable
fun ClientProfileList(vm: ConnectionViewModel) {
    Column {
        vm.profiles.forEach {
            ClientProfileListEntry(it, vm.selectedProfile.value == it) { profile -> vm.selectProfile(profile) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientProfileListEntry(vm: MidiClientProfileViewModel, isSelected: Boolean, selectedProfileChanged: (profile: MidiCIProfileId) -> Unit) {
    val border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
    Card(border = border, onClick = {
        selectedProfileChanged(vm.profileId)
    }) {
        Text(modifier = Modifier.padding(12.dp, 0.dp), text = vm.profileId.toString())
    }
}

@Composable
fun ClientProfileDetails(vm: MidiClientProfileViewModel) {
    val enabled by remember { vm.enabled }
    Switch(checked = enabled, onCheckedChange = {
        TODO("FIXME: implement")
    })
    Text(vm.profileId.toString())
}