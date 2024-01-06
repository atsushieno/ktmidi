package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import dev.atsushieno.ktmidi.ci.*
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
        val conn = ViewModel.selectedRemoteDevice.value
        Row {
            val destinationMUID by remember { ViewModel.selectedRemoteDeviceMUID }
            InitiatorDestinationSelector(destinationMUID,
                onChange = { Snapshot.withMutableSnapshot { ViewModel.selectedRemoteDeviceMUID.value = it } })

            if (conn != null)
                ClientConnectionInfo(conn)
        }
        if (conn != null)
            ClientConnection(conn)
    }
}

@Composable
fun ClientConnection(vm: ConnectionViewModel) {
    Column(Modifier.padding(12.dp, 0.dp)) {
        Text("Profiles", fontSize = TextUnit(1.5f, TextUnitType.Em), fontWeight = FontWeight.Bold)

        Row {
            ClientProfileList(vm)
            val selectedProfile by remember { vm.selectedProfile }
            val sp = selectedProfile
            if (sp != null)
                ClientProfileDetails(vm, sp)
        }

        Text("Properties", fontSize = TextUnit(1.5f, TextUnitType.Em), fontWeight = FontWeight.Bold)

        Row {
            ClientPropertyList(vm)
            val selectedProperty by remember { vm.selectedProperty }
            val sp = selectedProperty
            if (sp != null)
                ClientPropertyDetails(vm, sp)
        }
    }
}

@Composable
fun DeviceItemCard(label: String) {
    Card { Text(label, modifier= Modifier.padding(6.dp, 0.dp)) }
}

@Composable
private fun ClientConnectionInfo(vm: ConnectionViewModel) {
    val conn = vm.conn
    Column {
        Text("Device", fontSize = TextUnit(1.5f, TextUnitType.Em), fontWeight = FontWeight.Bold)
        val small = TextUnit(0.8f, TextUnitType.Em)
        Row {
            DeviceItemCard("Manufacturer")
            Text(conn.device.manufacturer.toString(16), fontSize = small)
            DeviceItemCard("Family")
            Text(conn.device.family.toString(16), fontSize = small)
            DeviceItemCard("Model")
            Text(conn.device.modelNumber.toString(16), fontSize = small)
            DeviceItemCard("Revision")
            Text(conn.device.softwareRevisionLevel.toString(16), fontSize = small)
        }
        Row {
            DeviceItemCard("instance ID")
            Text(conn.productInstanceId, fontSize = small)
            DeviceItemCard("max connections")
            Text("${conn.maxSimultaneousPropertyRequests}")
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
        val profileIds = vm.profiles.map { it.profile }.distinct()
        Snapshot.withMutableSnapshot {
            profileIds.forEach {
                ClientProfileListEntry(it, vm.selectedProfile.value == it) { profileId -> vm.selectProfile(profileId) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientProfileListEntry(profileId: MidiCIProfileId, isSelected: Boolean, selectedProfileChanged: (profile: MidiCIProfileId) -> Unit) {
    val border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
    Card(border = border, onClick = {
        selectedProfileChanged(profileId)
    }) {
        Text(modifier = Modifier.padding(12.dp, 0.dp), text = profileId.toString())
    }
}

@Composable
fun ClientProfileDetails(vm: ConnectionViewModel, profile: MidiCIProfileId) {
    Column {
        Text("Set Profile On Ch./Grp.")
        val entries = vm.profiles.filter { it.profile.toString() == profile.toString() }
        entries.forEach {
            Row {
                Switch(checked = it.enabled.value, onCheckedChange = { newEnabled ->
                    AppModel.ciDeviceManager.initiator.setProfile(vm.conn.muid, it.address.value, it.profile, newEnabled)
                })
                Text(when (it.address.value.toInt()) {
                    0x7F -> "Function Block"
                    0x7E -> "Group"
                    else -> it.address.value.toString()
                })
            }
        }
    }
}


@Composable
fun ClientPropertyList(vm: ConnectionViewModel) {
    Column {
        val properties = vm.properties.values.map { it.id }.distinct()
        Snapshot.withMutableSnapshot {
            properties.forEach {
                val id by remember { it }
                PropertyListEntry(id, vm.selectedProperty.value == id) {
                    propertyId -> vm.selectProperty(propertyId)
                    AppModel.ciDeviceManager.initiator.sendGetPropertyDataRequest(vm.conn.muid, propertyId)
                }
            }
        }
    }
}

@Composable
fun ClientPropertyDetails(vm: ConnectionViewModel, propertyId: String) {
    Column(Modifier.padding(12.dp)) {
        val entry = vm.properties.values.first { it.id.value == propertyId }
        val def = vm.conn.propertyClient.getMetadataList()?.firstOrNull { it.resource == entry.id.value }
        ClientPropertyValueEditor(vm, def, entry)
        if (def != null)
            PropertyMetadataEditor(def, {}, true)
        else
            Text("(Metadata not available - not in ResourceList)")
    }
}

@Composable
fun ClientPropertyValueEditor(vm: ConnectionViewModel, def: PropertyMetadata?, property: PropertyValueState) {
    val id by remember { property.id }
    val mediaType: String = vm.conn.propertyClient.getMediaTypeFor(property.replyHeader)
    PropertyValueEditor(false, mediaType, def, property,
        { AppModel.ciDeviceManager.initiator.sendGetPropertyDataRequest(vm.conn.muid, id) },
        { bytes, isPartial -> AppModel.ciDeviceManager.initiator.sendSetPropertyDataRequest(vm.conn.muid, id, bytes, isPartial) }
    )
}
