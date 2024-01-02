package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import dev.atsushieno.ktmidi.ci.MidiCIConstants
import dev.atsushieno.ktmidi.ci.MidiCIDeviceInfo
import dev.atsushieno.ktmidi.ci.MidiCIProfile
import dev.atsushieno.ktmidi.ci.MidiCIProfileId
import dev.atsushieno.ktmidi.citool.AppModel

@Composable
fun ResponderScreen() {
    val vm = ViewModel.localDeviceConfiguration
    Text(FeatureDescription.responderScreen)
    LocalDeviceConfiguration(vm)

    // Profile Configuration
    Text("Profiles", fontSize = TextUnit(1.5f, TextUnitType.Em), fontWeight = FontWeight.Bold)

    Row {
        LocalProfileList(vm)
        val selectedProfile by remember { vm.selectedProfile }
        val sp = selectedProfile
        if (sp != null)
            LocalProfileDetails(vm, sp)
    }

    Text("Properties", fontSize = TextUnit(1.5f, TextUnitType.Em), fontWeight = FontWeight.Bold)
}

@Composable
fun DeviceInfoItem(label: String) {
    Card { Text(label, modifier= Modifier.padding(6.dp, 0.dp).width(150.dp)) }
}

@Composable
private fun NumericTextField(num: Int, onUpdate: (Int) -> Unit) {
    TextField(num.toString(16),
        {val v = it.toIntOrNull(16); if (v != null) onUpdate(v) },
        modifier = Modifier.width(150.dp))
}

@Composable
private fun LocalDeviceConfiguration(vm: LocalConfigurationViewModel) {
    val device = vm.device
    val manufacturerId by remember { device.manufacturerId }
    val familyId by remember { device.familyId }
    val modelId by remember { device.modelId }
    val versionId by remember { device.versionId }
    val manufacturer by remember { device.manufacturer }
    val family by remember { device.family }
    val model by remember { device.model }
    val version by remember { device.version }
    val serialNumber by remember { device.serialNumber }
    val maxSimultaneousPropertyRequests by remember { vm.maxSimultaneousPropertyRequests }
    val update: ((info: MidiCIDeviceInfo) -> Unit) -> Unit = {
        val dev = MidiCIDeviceInfo(manufacturerId, familyId, modelId, versionId,
                manufacturer, family, model, version, serialNumber)
        it(dev)
        device.updateDeviceInfo(dev)
    }
    Column {
        Text("Local Device Configuration", fontSize = TextUnit(1.5f, TextUnitType.Em), fontWeight = FontWeight.Bold)
        Text("Note that each ID byte is in 7 bits. Hex more than 80h is invalid.", fontSize = TextUnit(0.9f, TextUnitType.Em))
        Row {
            Column(Modifier.border(1.dp, MaterialTheme.colorScheme.primaryContainer)) {
                Row {
                    DeviceInfoItem("Manufacturer")
                    Text("ID (3bytes):")
                    NumericTextField(manufacturerId) { value -> update { it.manufacturerId = value }}
                }
                Row {
                    DeviceInfoItem("Family")
                    Text("ID (2bytes):")
                    NumericTextField(familyId.toInt()) { value -> update { it.familyId = value.toShort() }}
                }
                Row {
                    DeviceInfoItem("Model")
                    Text("ID (2bytes):")
                    NumericTextField(modelId.toInt()) { value -> update { it.modelId = value.toShort() }}
                }
                Row {
                    DeviceInfoItem("Revision")
                    Text("ID (4bytes):")
                    NumericTextField(versionId) { value -> update { it.versionId = value }}
                }
                Row {
                    DeviceInfoItem("SerialNumber")
                }
            }
            Column(Modifier.border(1.dp, MaterialTheme.colorScheme.primaryContainer)) {
                Row {
                    Text("Text:")
                    TextField(manufacturer, { value: String -> update { it.manufacturer = value }})
                }
                Row {
                    Text("Text:")
                    TextField(family, { value: String -> update { it.family = value }})
                }
                Row {
                    Text("Text:")
                    TextField(model, { value: String -> update { it.model = value }})
                }
                Row {
                    Text("Text:")
                    TextField(version, { value: String -> update { it.version = value }})
                }
                Row {
                    Text("Text:")
                    TextField(serialNumber ?: "", { value: String -> update { it.serialNumber = value }})
                }
            }
        }
        Divider()
        Row {
            DeviceItemCard("max connections")
            TextField("$maxSimultaneousPropertyRequests", { value: String -> vm.updateMaxSimultaneousPropertyRequests(value.toByteOrNull() ?: maxSimultaneousPropertyRequests) })
        }
    }
}

// Profile Configuration

@Composable
fun LocalProfileList(vm: LocalConfigurationViewModel) {
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
            Button(onClick = {
                val state = MidiCIProfile(MidiCIProfileId(b0, b0, b0, b0, b0), MidiCIConstants.ADDRESS_FUNCTION_BLOCK, false)
                AppModel.ciDeviceManager.responder.addProfile(state)
                vm.selectedProfile.value = state.profile
                vm.isSelectedProfileIdEditing.value = true
                editedProfileName = state.profile.toString()
            }) {
                Image(Icons.Default.Add, "Add")
            }
            if (vm.isSelectedProfileIdEditing.value) {
                Button(onClick = {
                    val splitN = editedProfileName.split(':').map { it.toByteOrNull(16) }
                    if (splitN.size == 5 && splitN.all { it != null }) {
                        val split = splitN.map { it!! }
                        val newProfileId = MidiCIProfileId(split[0], split[1], split[2], split[3], split[4])
                        AppModel.ciDeviceManager.responder.updateProfileName(vm.selectedProfile.value!!, newProfileId)
                        vm.isSelectedProfileIdEditing.value = false
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
fun LocalProfileDetails(vm: LocalConfigurationViewModel, profile: MidiCIProfileId) {
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
                    AppModel.ciDeviceManager.responder.updateProfileTarget(it, newAddress, it.enabled.value, numChannelsRequested)
                }
                TextField(numChannelsRequested.toString(), { s:String ->
                    val v = s.toShortOrNull()
                    if (v != null)
                        numChannelsRequested = v
                }, Modifier.width(80.dp))
                Button(onClick = {
                    AppModel.ciDeviceManager.responder.removeProfile(it.address.value, it.profile)
                }, Modifier.padding(12.dp, 0.dp)) {
                    Image(Icons.Default.Delete, "Delete")
                }
            }
        }
        Row {
            Button(onClick = {
                val state = MidiCIProfile(profile, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, false)
                AppModel.ciDeviceManager.responder.addProfile(state)
                vm.selectedProfile.value = state.profile
            }) {
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