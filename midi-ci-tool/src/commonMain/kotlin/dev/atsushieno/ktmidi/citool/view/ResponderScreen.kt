package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import dev.atsushieno.ktmidi.ci.MidiCIConstants
import dev.atsushieno.ktmidi.ci.MidiCIProfile
import dev.atsushieno.ktmidi.ci.MidiCIProfileId
import dev.atsushieno.ktmidi.ci.PropertyMetadata
import dev.atsushieno.ktmidi.citool.AppModel

@Composable
fun ResponderScreen(vm: ResponderViewModel) {
    Text(FeatureDescription.responderScreen)

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

@Composable
fun LocalPropertyConfiguration(vm: ResponderViewModel) {
    Text("Properties", fontSize = 24.sp, fontWeight = FontWeight.Bold)

    Row {
        LocalPropertyList(
            vm.properties.map { it.id.value }.distinct(),
            vm.selectedProperty.value,
            selectProperty = { vm.selectProperty(it) },
            createNewProperty = { vm.createNewProperty() },
            removeSelectedProperty = { vm.removeSelectedProperty() }
        )

        // So far we only support full updates...
        val selectedProperty by remember { vm.selectedProperty }
        val sp = selectedProperty
        if (sp != null) {
            val value = vm.properties.first { it.id.value == sp }
            val def = vm.getPropertyMetadata(sp)
            LocalPropertyDetails(def, value,
                updatePropertyValue = { id, data -> vm.updatePropertyValue(id, data, false) },
                metadataUpdateCommitted = { vm.updatePropertyMetadata(sp, it) }
            )
        }
    }
}


// Profile Configuration

@Composable
fun LocalProfileList(vm: ResponderViewModel) {
    Column {
        var isSelectedProfileEditable by remember { vm.isSelectedProfileIdEditing }
        val profileIds = vm.profiles.map { it.profile }.distinct()
        var editedProfileName by remember { mutableStateOf("") }
        Snapshot.withMutableSnapshot {
            profileIds.forEach {
                LocalProfileListEntry(it, editedProfileName,
                    { s -> editedProfileName = s },
                    vm.selectedProfile.value == it, isSelectedProfileEditable) {
                    profileId -> vm.selectProfile(profileId)
                }
            }
        }
        Row {
            val b0 = 0.toByte()
            val emptyId = MidiCIProfileId(b0, b0, b0, b0, b0)
            Button(onClick = {
                val state = MidiCIProfile(emptyId, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, false)
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
                        val newProfileId = MidiCIProfileId(split[0], split[1], split[2], split[3], split[4])
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
        val entries = vm.profiles.filter { it.profile.toString() == profile.toString() }

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
                    onClick = { vm.removeProfileTarget(it.address.value, it.profile) },
                    enabled = !vm.isSelectedProfileIdEditing.value,
                    modifier = Modifier.padding(12.dp, 0.dp)) {
                    Image(Icons.Default.Delete, "Delete")
                }
            }
        }
        Row {
            Button(onClick = {
                val state = MidiCIProfile(profile, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, false)
                vm.addNewProfileTarget(state)
                vm.selectedProfile.value = state.profile
            }, enabled = vm.selectedProfile.value != null && vm.profiles.all { it.profile != profile || it.address.value != MidiCIConstants.ADDRESS_FUNCTION_BLOCK }) {
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
    Row(Modifier.width(200.dp)) {
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
fun LocalPropertyDetails(def: PropertyMetadata?, property: PropertyValueState,
                         updatePropertyValue: (propertyId: String, bytes: List<Byte>) -> Unit,
                         metadataUpdateCommitted: (property: PropertyMetadata) -> Unit) {
    Column(Modifier.padding(12.dp)) {
        if (def != null) {
            PropertyValueEditor(true, property.mediaType.value, def, property.data.value,
                {}, // local editor does not support value refresh
                {}, // local editor does not support value subscription
                { bytes, _ -> updatePropertyValue(property.id.value, bytes) }
            )
            PropertyMetadataEditor(
                def,
                metadataUpdateCommitted,
                false
            )
        }
        else
            Text("(Metadata not available - not in ResourceList)")
    }
}
