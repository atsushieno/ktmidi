package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.atsushieno.ktmidi.ci.ClientConnection
import dev.atsushieno.ktmidi.ci.Message.Companion.muidString
import dev.atsushieno.ktmidi.ci.MidiCIConstants
import dev.atsushieno.ktmidi.ci.MidiCIProfileId
import dev.atsushieno.ktmidi.ci.PropertyExchangeInitiator.SubscriptionActionState
import dev.atsushieno.ktmidi.citool.MidiCIProfileState

@Composable
fun InitiatorScreen(vm: InitiatorViewModel) {
    Column(Modifier.verticalScroll(rememberScrollState())
        .padding(10.dp)) {
        Row {
            Button(onClick = { vm.sendDiscovery() }) {
                Text("Send Discovery")
            }
        }
        Row {
            val destinationMUID by remember { vm.selectedRemoteDeviceMUID }
            InitiatorDestinationSelector(
                vm.device.connections.associate { it.conn.targetMUID to it.conn },
                destinationMUID,
                onChange = { Snapshot.withMutableSnapshot { vm.selectedRemoteDeviceMUID.value = it } })
        }

        vm.connections.forEach {
            ClientConnection(it)
        }
    }
}

@Composable
fun ClientConnection(vm: ConnectionViewModel) {
    Column(Modifier.padding(12.dp, 0.dp)) {
        Divider()
        ClientConnectionInfo(vm)

        Text("Profiles", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Row {
            ClientProfileList(vm)
            val selectedProfile by remember { vm.selectedProfile }
            val sp = selectedProfile
            if (sp != null)
                ClientProfileDetails(vm, vm.conn.profiles.filter { it.profile == sp }, sp)
        }

        Text("Properties", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Row {
            ClientPropertyList(vm)
            val selectedProperty by remember { vm.selectedProperty }
            val sp = selectedProperty
            if (sp != null)
                ClientPropertyDetails(vm, sp,
                    refreshValueClicked = { encoding, paginateOffset, paginateLimit -> vm.refreshPropertyValue(vm.conn.conn.targetMUID, sp, encoding, paginateOffset, paginateLimit) },
                    subscribeClicked = { newState, encoding ->
                        if (newState)
                            vm.sendSubscribeProperty(vm.conn.conn.targetMUID, sp, encoding)
                        else
                            vm.sendUnsubscribeProperty(vm.conn.conn.targetMUID, sp, encoding)
                    },
                    commitChangeClicked = { id, bytes, encoding, isPartial -> vm.sendSetPropertyDataRequest(vm.conn.conn.targetMUID, id, bytes, encoding, isPartial) }
                )
        }

        Text("Process Inquiry", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row {
            var midiMessageReportAddress by remember { mutableStateOf(MidiCIConstants.ADDRESS_FUNCTION_BLOCK) }
            Text("Channel: ")
            AddressSelector(midiMessageReportAddress, valueChange = { midiMessageReportAddress = it })
            Button(onClick = { vm.requestMidiMessageReport(midiMessageReportAddress, vm.conn.conn.targetMUID) }) {
                Text("Request MIDI Message Report")
            }
        }
    }
}

@Composable
fun DeviceItemCard(label: String) {
    Card { Text(label, modifier= Modifier.padding(6.dp, 0.dp)) }
}

@Composable
private fun ClientConnectionInfo(vm: ConnectionViewModel) {
    val conn = vm.conn.conn
    Column {
        Text("Device", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        val small = 12.sp
        Row {
            DeviceItemCard("product instance ID")
            Text(conn.productInstanceId, fontSize = small)
            DeviceItemCard("MUID")
            Text(vm.conn.conn.targetMUID.muidString, fontSize = small)
            DeviceItemCard("max connections")
            Text(conn.maxSimultaneousPropertyRequests.toString(), fontSize = small)
        }
        val deviceInfo = vm.conn.deviceInfo.value
        Row {
            DeviceItemCard("Manufacturer")
            Text(deviceInfo.manufacturer.ifEmpty { deviceInfo.manufacturerId.toString(16) }, fontSize = small)
            DeviceItemCard("Family")
            Text(deviceInfo.family.ifEmpty { deviceInfo.familyId.toString(16) }, fontSize = small)
        }
        Row {
            DeviceItemCard("Model")
            Text(deviceInfo.model.ifEmpty { deviceInfo.modelId.toString(16) }, fontSize = small)
            DeviceItemCard("Revision")
            Text(deviceInfo.version.ifEmpty { deviceInfo.versionId.toString(16) }, fontSize = small)
            DeviceItemCard("Serial #")
            Text(deviceInfo.serialNumber ?: "", fontSize = small)
        }
    }
}

@Composable
private fun InitiatorDestinationSelector(connections: Map<Int, ClientConnection>,
                                         destinationMUID: Int,
                                         onChange: (Int) -> Unit) {
    var dialogState by remember { mutableStateOf(false) }

    DropdownMenu(expanded = dialogState, onDismissRequest = { dialogState = false}) {
        val onClick: (Int) -> Unit = { muid ->
            if (muid != 0)
                onChange(muid)
            dialogState = false
        }
        if (connections.any())
            connections.toList().forEach { conn ->
                DropdownMenuItem(onClick = { onClick(conn.first) }, text = {
                    Text(modifier = Modifier.padding(12.dp, 0.dp), text = conn.first.muidString)
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
            text = if (destinationMUID != 0) destinationMUID.muidString else "-- Select CI Device --")
    }
}



@Composable
fun ClientProfileList(vm: ConnectionViewModel) {
    Column {
        val profileIds = vm.conn.profiles.map { it.profile }.distinct()
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
fun ClientProfileDetails(vm: ConnectionViewModel, profiles: List<MidiCIProfileState>, profile: MidiCIProfileId) {
    Column {
        Text("Details Inquiry")
        var profileDetailsInquiryAddress by remember { mutableStateOf(MidiCIConstants.ADDRESS_FUNCTION_BLOCK) }
        var profileDetailsInquiryTarget by remember { mutableStateOf("0") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("channel:")
            AddressSelector(profileDetailsInquiryAddress, valueChange = { profileDetailsInquiryAddress = it} )
            Text("target byte:")
            TextField(
                profileDetailsInquiryTarget,
                { profileDetailsInquiryTarget = it },
                modifier = Modifier.width(60.dp)
            )
            Button(onClick = {
                val address = profileDetailsInquiryAddress
                val target = profileDetailsInquiryTarget.toByteOrNull()
                if (target != null)
                    vm.sendProfileDetailsInquiry(profile, address, target)
            }) {
                Text("Send Details Inquiry")
            }
        }
        Text("Set Profile [Grp. / Ch.] ")
        profiles.forEach {
            var numChannelsRequestedString by remember { mutableStateOf(it.numChannelsRequested.value.toString()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("[${it.group.value} / ")
                Text(
                    when (it.address.value.toInt()) {
                        0x7F -> "Function Block"
                        0x7E -> "Group"
                        else -> it.address.value.toString()
                    },
                    Modifier.width(120.dp)
                )
                Text("]", Modifier.padding(10.dp))
                Switch(checked = it.enabled.value, onCheckedChange = { newEnabled ->
                    try {
                        vm.setProfile(it.address.value, it.profile, newEnabled, numChannelsRequestedString.toShort())
                    } catch (_: NumberFormatException) {
                    }
                }, Modifier.padding(10.dp))
                Text("number of channels requested:", fontSize = 12.sp)
                TextField(numChannelsRequestedString,
                    onValueChange = { v:String -> numChannelsRequestedString = v },
                    Modifier.width(50.dp),
                    enabled = it.address.value < 0x7E)
            }
        }
    }
}


@Composable
fun ClientPropertyList(vm: ConnectionViewModel) {
    Column {
        val properties = vm.conn.properties.map { it.id }.distinct()
        Snapshot.withMutableSnapshot {
            properties.forEach {
                PropertyListEntry(it, vm.selectedProperty.value == it) {
                    propertyId -> vm.selectProperty(propertyId)
                }
            }
        }
    }
}

@Composable
fun ClientPropertyDetails(vm: ConnectionViewModel, propertyId: String,
                          refreshValueClicked: (requestedEncoding: String?, paginateOffset: Int?, paginateLimit: Int?) -> Unit,
                          subscribeClicked: (newValue: Boolean, requestedEncoding: String?) -> Unit,
                          commitChangeClicked: (id: String, bytes: List<Byte>, encoding: String?, isPartial: Boolean) -> Unit) {
    Column(Modifier.padding(12.dp)) {
        val entry = vm.conn.properties.firstOrNull { it.id == propertyId } ?: return
        val def = vm.conn.getMetadataList()?.firstOrNull { it.resource == entry.id }
        PropertyValueEditor(false, entry.mediaType, def, entry.body,
            refreshValueClicked,
            isSubscribing = vm.conn.subscriptions.firstOrNull { it.propertyId == propertyId }?.state?.value == SubscriptionActionState.Subscribed,
            subscribeClicked,
            commitChangeClicked = { bytes, encoding, isPartial -> commitChangeClicked(entry.id, bytes, encoding, isPartial) })
        if (def != null)
            PropertyMetadataEditor(def,
                {}, // client does not support metadata editing
                true)
        else
            Text("(Metadata not available - not in ResourceList)")
    }
}
