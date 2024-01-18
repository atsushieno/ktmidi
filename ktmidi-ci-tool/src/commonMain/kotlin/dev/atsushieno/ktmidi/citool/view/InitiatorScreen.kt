package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.atsushieno.ktmidi.ci.MidiCIConstants
import dev.atsushieno.ktmidi.ci.MidiCIInitiator
import dev.atsushieno.ktmidi.ci.MidiCIProfileId
import dev.atsushieno.ktmidi.ci.MidiCIInitiator.SubscriptionActionState

@Composable
fun InitiatorScreen(vm: InitiatorViewModel) {
    Column(Modifier.verticalScroll(rememberScrollState())
        .padding(10.dp)) {
        Row {
            Button(onClick = { vm.sendDiscovery() }) {
                Text("Send Discovery")
            }
        }
        val conn = vm.selectedRemoteDevice.value
        Row {
            val destinationMUID by remember { vm.selectedRemoteDeviceMUID }
            InitiatorDestinationSelector(
                vm.device.connections.map { it.conn.targetMUID to it.conn }.toMap(),
                destinationMUID,
                onChange = { Snapshot.withMutableSnapshot { vm.selectedRemoteDeviceMUID.value = it } })

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
        Text("Profiles", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Row {
            ClientProfileList(vm)
            val selectedProfile by remember { vm.selectedProfile }
            val sp = selectedProfile
            if (sp != null)
                ClientProfileDetails(vm, sp)
        }

        Text("Properties", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Row {
            ClientPropertyList(vm)
            val selectedProperty by remember { vm.selectedProperty }
            val sp = selectedProperty
            if (sp != null)
                ClientPropertyDetails(vm, sp,
                    refreshValueClicked = { encoding -> vm.refreshPropertyValue(vm.conn.conn.targetMUID, sp, encoding) },
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
private fun InitiatorDestinationSelector(connections: Map<Int, MidiCIInitiator.ClientConnection>,
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
fun ClientProfileDetails(vm: ConnectionViewModel, profile: MidiCIProfileId) {
    Column {
        Text("Details Inquiry")
        var profileDetailsInquiryAddress by remember { mutableStateOf("0") }
        var profileDetailsInquiryTarget by remember { mutableStateOf("0") }
        Row {
            Text("Address:")
            TextField(
                profileDetailsInquiryAddress,
                { profileDetailsInquiryAddress = it },
                modifier = Modifier.width(60.dp)
            )
            Text("Target:")
            TextField(
                profileDetailsInquiryTarget,
                { profileDetailsInquiryTarget = it },
                modifier = Modifier.width(60.dp)
            )
            Button(onClick = {
                val address = profileDetailsInquiryAddress.toByteOrNull()
                val target = profileDetailsInquiryTarget.toByteOrNull()
                if (address != null && target != null)
                    vm.sendProfileDetailsInquiry(profile, address, target)
            }) {
                Text("Send Details Inquiry")
            }
        }
        Text("Set Profile On Ch./Grp.")
        val entries = vm.conn.profiles.filter { it.profile.toString() == profile.toString() }
        entries.forEach {
            Row {
                Switch(checked = it.enabled.value, onCheckedChange = { newEnabled ->
                    vm.setProfile(vm.conn.conn.targetMUID, it.address.value, it.profile, newEnabled)
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
                          refreshValueClicked: (requestedEncoding: String?) -> Unit,
                          subscribeClicked: (newValue: Boolean, requestedEncoding: String?) -> Unit,
                          commitChangeClicked: (id: String, bytes: List<Byte>, encoding: String?, isPartial: Boolean) -> Unit) {
    Column(Modifier.padding(12.dp)) {
        val entry = vm.conn.properties.first { it.id == propertyId }
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
