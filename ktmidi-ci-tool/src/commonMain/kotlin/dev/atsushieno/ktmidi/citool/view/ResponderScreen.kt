package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.ci.propertycommonrules.CommonRulesPropertyMetadata

@Composable
fun ResponderScreen(vm: ResponderViewModel) {
    Column(Modifier.verticalScroll(rememberScrollState())
        .padding(10.dp)) {
        // Profile Configuration
        Text("Profiles", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Row {
            LocalProfileList(vm)
            val selectedProfile by remember { vm.selectedProfile }
            val sp = selectedProfile
            if (sp != null)
                LocalProfileDetails(vm, sp)
        }
        LocalPropertyConfiguration(vm)
    }
}

@Composable
fun LocalPropertyConfiguration(vm: ResponderViewModel) {
    Text("Properties", fontSize = 24.sp, fontWeight = FontWeight.Bold)

    Row {
        LocalPropertyList(
            vm.model.properties.map { it.id }.distinct(),
            vm.selectedProperty.value,
            selectProperty = { vm.selectProperty(it) },
            createNewProperty = { vm.createNewProperty() },
            removeSelectedProperty = { vm.removeSelectedProperty() }
        )

        // So far we only support full updates...
        val selectedProperty by remember { vm.selectedProperty }
        val sp = selectedProperty
        if (sp != null) {
            val value = vm.model.properties.first { it.id == sp }
            val def = vm.getPropertyMetadata(sp) as CommonRulesPropertyMetadata
            val subscribedClients = vm.model.connections.filter { conn -> conn.subscriptions.any { sub -> sub.propertyId == sp } }
            LocalPropertyDetails(def, value,
                updatePropertyValue = { id, data -> vm.updatePropertyValue(id, data, false) },
                metadataUpdateCommitted = { vm.updatePropertyMetadata(sp, it) },
                subscribedClients = subscribedClients.map { it.conn.targetMUID.toString() },
                unsubscribeRequestedByIndex = { idx -> vm.shutdownSubscription(subscribedClients[idx].conn.targetMUID, sp) }
            )
        }
    }
}


// Profile Configuration

@Composable
fun LocalProfileList(vm: ResponderViewModel) {
    Column {
        val profileIds = vm.model.localProfileStates.map { it.profile }.distinct()
        var editedProfileName by remember { mutableStateOf("") }
        Snapshot.withMutableSnapshot {
            profileIds.forEach {
                LocalProfileListEntry(it, editedProfileName,
                    { s -> editedProfileName = s },
                    vm.selectedProfile.value == it, vm.isSelectedProfileIdEditing.value) {
                    profileId -> vm.selectProfile(profileId)
                }
            }
        }
        Row {
            val b0 = 0.toByte()
            val emptyId = MidiCIProfileId(listOf(b0, b0, b0, b0, b0))
            Button(onClick = {
                val state = MidiCIProfile(emptyId, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, 0, false, 0)
                vm.addNewProfile(state)
                editedProfileName = state.profile.toString()
            }, enabled = !vm.isSelectedProfileIdEditing.value && profileIds.all { it != emptyId }) {
                Image(Icons.Default.Add, "Add")
            }
            if (vm.isSelectedProfileIdEditing.value) {
                Button(onClick = {
                    val splitN = editedProfileName.split(':').map { it.toByteOrNull(16) }
                    if (splitN.size == 5 && splitN.all { it != null }) {
                        val split = splitN.map { it!! }
                        val newProfileId = MidiCIProfileId(listOf(split[0], split[1], split[2], split[3], split[4]))
                        vm.updateProfileName(vm.selectedProfile.value!!, newProfileId)
                    }
                }) {
                    Image(Icons.Default.Done, "Commit changes")
                }
                Button(onClick = {
                    vm.isSelectedProfileIdEditing.value = false
                }) {
                    Image(Icons.Default.Close, "Discard changes")
                }
            } else {
                Button(onClick = {
                    editedProfileName = vm.selectedProfile.value?.toString() ?: ""
                    vm.isSelectedProfileIdEditing.value = true
                }, enabled = vm.selectedProfile.value != null) {
                    Image(Icons.Default.Edit, "Edit")
                }

            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Add test items")
            Button(onClick = {
                vm.addTestProfileItems()
            }, enabled = !vm.isSelectedProfileIdEditing.value) {
                Image(Icons.Default.Add, "Add test items")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalProfileListEntry(profileId: MidiCIProfileId, currentProfileNameEdit: String, onProfileNameEdit: (String) -> Unit, isSelected: Boolean, isSelectedProfileEditing: Boolean, selectedProfileChanged: (profile: MidiCIProfileId) -> Unit) {
    val border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isSelected && isSelectedProfileEditing)
            TextField(currentProfileNameEdit,
                { it: String -> onProfileNameEdit(it) },
                modifier = Modifier.padding(12.dp, 0.dp).width(150.dp))
        else {
            Card(border = border, onClick = {
                selectedProfileChanged(profileId)
            }) {
                Text(profileId.toString(), modifier = Modifier.padding(12.dp, 0.dp).width(150.dp))
            }
        }
    }
}

@Composable
fun LocalProfileDetails(vm: ResponderViewModel, profile: MidiCIProfileId) {
    Column {
        val entries = vm.model.localProfileStates.filter { it.profile.toString() == profile.toString() }

        Row {
            Text("Off/On", Modifier.width(80.dp))
            Text("Ch./Grp.", Modifier.width(200.dp))
            Text("NumChannels", Modifier.width(120.dp))
        }

        entries.forEach {
            var numChannelsRequested by remember { mutableStateOf(1.toShort()) }
            Row {
                Switch(checked = it.enabled.value, onCheckedChange = {}, Modifier.width(80.dp), enabled = false)
                AddressSelector(it.address.value) { newAddress ->
                    vm.updateProfileTarget(it, newAddress, numChannelsRequested)
                }
                TextField(numChannelsRequested.toString(), { s:String ->
                    val v = s.toShortOrNull()
                    if (v != null)
                        numChannelsRequested = v
                }, Modifier.width(80.dp))
                Button(
                    onClick = { vm.removeProfileTarget(it.group.value, it.address.value, it.profile) },
                    enabled = !vm.isSelectedProfileIdEditing.value,
                    modifier = Modifier.padding(12.dp, 0.dp)) {
                    Image(Icons.Default.Delete, "Delete")
                }
            }
        }
        Row {
            Button(onClick = {
                val state = MidiCIProfile(profile, MidiCIConstants.ADDRESS_FUNCTION_BLOCK,  0,false, 0)
                vm.addNewProfileTarget(state)
                vm.selectedProfile.value = state.profile
            }, enabled = vm.selectedProfile.value != null && vm.model.localProfileStates.all { it.profile != profile || it.address.value != MidiCIConstants.ADDRESS_FUNCTION_BLOCK }) {
                Image(Icons.Default.Add, "Add")
            }
        }
    }
}

private val channelAddressMapping = (0 until 0x10).associate {
    Pair(it.toByte(), it.toString())
}.toMutableMap().apply {
    put(0x7E, "Group")
    put(0x7F, "Function Block")
}

@Composable
fun AddressSelector(address: Byte, valueChange: (Byte) -> Unit) {
    Row(Modifier.width(180.dp)) {
        var index by remember { mutableStateOf(address) }
        var menuExpanded by remember { mutableStateOf(false) }
        DropdownMenu(menuExpanded, { menuExpanded = false }) {
            channelAddressMapping.forEach {
                DropdownMenuItem(
                    text = { Text(it.value) },
                    onClick = {
                        valueChange(it.key)
                        index = it.key
                        menuExpanded = false
                    }
                )
            }
        }
        Card(
            modifier = Modifier.clickable(onClick = { menuExpanded = true }).padding(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Text(
                modifier = Modifier.padding(12.dp, 0.dp),
                text = channelAddressMapping[address] ?: "(unrecognized value)"
            )
        }
    }
}


@Composable
fun LocalPropertyList(properties: List<String>,
                      selectedProperty: String?,
                      selectProperty: (id: String)->Unit,
                      createNewProperty: ()->Unit,
                      removeSelectedProperty: ()->Unit) {
    Column {
        properties.forEach {
            PropertyListEntry(it, selectedProperty == it) {
                propertyId -> selectProperty(propertyId)
            }
        }
        Row {
            Button(onClick = { createNewProperty() }) {
                Image(Icons.Default.Add, "Add")
            }
            Button(onClick = { removeSelectedProperty() }) {
                Image(Icons.Default.Delete, "Delete")
            }
        }
    }
}

@Composable
fun LocalPropertyDetails(def: CommonRulesPropertyMetadata?, property: PropertyValue,
                         updatePropertyValue: (propertyId: String, bytes: List<Byte>) -> Unit,
                         metadataUpdateCommitted: (property: CommonRulesPropertyMetadata) -> Unit,
                         subscribedClients: List<String>,
                         unsubscribeRequestedByIndex: (Int) -> Unit) {
    Column(Modifier.padding(12.dp)) {
        if (def != null) {
            PropertyValueEditor(true, property.mediaType, def, property.body,
                refreshValueClicked = { _,_,_ -> }, // local editor does not support value refresh
                isSubscribing = false,
                subscriptionChanged = { _,_ -> }, // local editor does not support value subscription
                { bytes, _, _ -> updatePropertyValue(property.id, bytes) } // local editor does not involve encoding and partial updates
            )
            PropertyMetadataEditor(
                def,
                metadataUpdateCommitted,
                def.originator == CommonRulesPropertyMetadata.Originator.SYSTEM
            )
            LocalPropertySubscriptions(subscribedClients, unsubscribeRequestedByIndex)
        }
        else
            Text("(Metadata not available - not in ResourceList)")
    }
}

@Composable
fun LocalPropertySubscriptions(clientNames: List<String>,
                               unsubscribeRequestedByIndex: (Int) -> Unit) {
    var dialogState by remember { mutableStateOf(false) }
    var current by remember { mutableIntStateOf(-1) }

    Text("Subscribed Clients", fontWeight = FontWeight.Bold, fontSize = 20.sp)

    Row {
        DropdownMenu(expanded = dialogState, onDismissRequest = { dialogState = false }) {
            val onClick: (Int) -> Unit = {
                if (it >= 0)
                    current = it
                dialogState = false
            }
            if (clientNames.any())
                clientNames.forEachIndexed { index, s ->
                    DropdownMenuItem(onClick = { onClick(index) }, text = { Text(s) })
                }
            else
                DropdownMenuItem(onClick = { onClick(-1) }, text = { Text("(No subscribed client)") })
            DropdownMenuItem(onClick = { onClick(-1) }, text = { Text("(Cancel)") })
        }
        Card(
            modifier = Modifier.clickable(onClick = {
                dialogState = true
            }).padding(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Text(
                modifier = Modifier.padding(12.dp, 0.dp),
                text = if (current < 0) "-- Select subscribed client --" else clientNames[current]
            )
        }
        Button(onClick = {
            if (current >= 0) {
                val index = current
                current = -1 // do this first, otherwise it could result in index out of bound
                unsubscribeRequestedByIndex(index)
            }
        }) {
            Text("Unsubscribe")
        }
    }
}
