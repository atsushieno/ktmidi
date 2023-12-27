package dev.atsushieno.ktmidi.citool.view

import Platform
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.citool.AppModel
import getPlatform

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
    val conn = vm.conn
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
        Text("by addressing:")
        val entries = vm.profiles.filter { it.profile.toString() == profile.toString() }
        entries.forEach {
            Row {
                val enabled by remember { it.enabled }
                Switch(checked = enabled, onCheckedChange = { newEnabled ->
                    AppModel.ciDeviceManager.initiator.setProfile(vm.conn.muid, it.address, it.profile, newEnabled)
                })
                Text("${it.address.toString(16)}: ${it.profile}")
            }
        }
    }
}


@Composable
fun ClientPropertyList(vm: ConnectionViewModel) {
    Column {
        val properties = vm.properties.map { it.id }.distinct()
        Snapshot.withMutableSnapshot {
            properties.forEach {
                ClientPropertyListEntry(it, vm.selectedProperty.value == it) {
                    propertyId -> vm.selectProperty(propertyId)
                    AppModel.ciDeviceManager.initiator.sendGetPropertyDataRequest(vm.conn.muid, propertyId)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientPropertyListEntry(propertyId: String, isSelected: Boolean, selectedPropertyChanged: (property: String) -> Unit) {
    val border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
    Card(border = border, onClick = {
        selectedPropertyChanged(propertyId)
    }) {
        Text(modifier = Modifier.padding(12.dp, 0.dp), text = propertyId)
    }
}

@Composable
fun ClientPropertyDetails(vm: ConnectionViewModel, propertyId: String) {
    Column(Modifier.padding(12.dp)) {
        val entry = vm.properties.first { it.id == propertyId }
        val def = vm.conn.propertyClient.getPropertyList()?.firstOrNull { it.resource == entry.id }
        ClientPropertyValueEditor(vm, def, entry)
        if (def != null)
            ClientPropertyMetadata(vm, def)
        else
            Text("(Metadata not available - not in ResourceList)")
    }
}

@Composable
fun PropertyColumn(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Card(modifier = Modifier.width(180.dp).padding(12.dp, 0.dp)) { Text(label, Modifier.padding(12.dp, 0.dp)) }
        content()
    }
}

@Composable
fun ClientPropertyMetadata(vm: ConnectionViewModel, def: PropertyResource) {
    Column {
        Text("Property Metadata", fontWeight = FontWeight.Bold, fontSize = TextUnit(1.2f, TextUnitType.Em))

        PropertyColumn("resource") { TextField(def.resource, {}, readOnly = true) }
        PropertyColumn("canGet") { Checkbox(def.canGet, {}, enabled = false) }
        PropertyColumn("canSet") { TextField(def.canSet, {}, readOnly = true) }
        PropertyColumn("canSubscribe") { Checkbox(def.canSubscribe, {}, enabled = false) }
        PropertyColumn("requireResId") { Checkbox(def.requireResId, {}, enabled = false) }
        PropertyColumn("mediaTypes") { TextField(def.mediaTypes.joinToString("\n"), {}, readOnly = true, minLines = 2) }
        PropertyColumn("encodings") { TextField(def.encodings.joinToString("\n"), {}, readOnly = true, minLines = 2) }
        PropertyColumn("schema") { TextField(if (def.schema == null) "" else Json.getUnescapedString(def.schema!!), {}, readOnly = true, minLines = 2) }
        PropertyColumn("canPaginate") { Checkbox(def.canPaginate, {}, enabled = false) }
        PropertyColumn("columns") {
            Column(Modifier.padding(12.dp)) {
                def.columns.forEach {
                    if (it.property != null)
                        Text("Property ${it.property}: ${it.title}")
                    if (it.link != null)
                        Text("Link ${it.link}: ${it.title}")
                }
            }
        }
    }
}

@Composable
fun ClientPropertyValueEditor(vm: ConnectionViewModel, def: PropertyResource?, property: ObservablePropertyList.Entry) {
    Column {
        Text("Property Value", fontWeight = FontWeight.Bold, fontSize = TextUnit(1.2f, TextUnitType.Em))

        val mediaType: String = vm.conn.propertyClient.getMediaTypeFor(property.replyHeader)
        val isEditableByMetadata = def?.canSet != null && def.canSet != PropertySetAccess.NONE
        val isTextRenderable = mediaType == CommonRulesKnownMimeTypes.APPLICATION_JSON
        val showGetButton = @Composable {
            Button(onClick = {
                AppModel.ciDeviceManager.initiator.sendGetPropertyDataRequest(vm.conn.muid, property.id)
            }) {
                Text("Refresh")
            }
        }
        val commitSetProperty: (List<Byte>, Boolean) -> Unit = { bytes, isPartial ->
            AppModel.ciDeviceManager.initiator.sendSetPropertyDataRequest(
                vm.conn.muid, property.id, bytes, isPartial
            )
        }
        var showFilePicker by remember { mutableStateOf(false) }
        val showUploadButton = @Composable {
            if (getPlatform().canReadLocalFile) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("... or ...")
                    Button(onClick = { showFilePicker = !showFilePicker }) {
                        Text("Commit value via binary File")
                    }
                    getPlatform().BinaryFilePicker(showFilePicker) { file ->
                        showFilePicker = false
                        if (file != null) {
                            val bytes = getPlatform().loadFileContent(file)
                            commitSetProperty(bytes, false)
                        }
                    }
                }
            }
        }

        if (isTextRenderable) {
            val bodyText = PropertyCommonConverter.decodeASCIIToString(property.body.toByteArray().decodeToString())
            if (isEditableByMetadata) {
                var editing by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(editing, { editing = !editing })
                    Text("edit")
                }
                if (editing) {
                    var text by remember { mutableStateOf(bodyText) }
                    var partial by remember { mutableStateOf("") }
                    Row {
                        if (isEditableByMetadata) {
                            if (def?.canSet == PropertySetAccess.PARTIAL) {
                                TextField(partial, { partial = it }, label = {
                                    Text("Partial? RFC6901 Pointer here then:")
                                })
                            }
                            showGetButton()
                            Button(onClick = {
                                val jsonString = Json.getEscapedString(partial.ifEmpty { text })
                                val bytes =
                                    PropertyCommonConverter.encodeStringToASCII(jsonString).encodeToByteArray().toList()
                                commitSetProperty(bytes, partial.isEmpty())
                            }) {
                                Text("Commit changes")
                            }
                        }
                    }
                    TextField(text, { text = it })
                    showUploadButton()
                } else {
                    showGetButton()
                    TextField(bodyText, {}, readOnly = true)
                }
            } else {
                Text("read-only")
                showGetButton()
                TextField(bodyText, {}, readOnly = true)
            }
        } else {
            Text("MIME type '$mediaType' not supported for editing")
            showGetButton()
            if (isEditableByMetadata)
                showUploadButton()
        }
    }
}
