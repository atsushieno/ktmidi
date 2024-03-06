package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.atsushieno.ktmidi.ci.MidiCIConverter
import dev.atsushieno.ktmidi.ci.MidiCIDeviceInfo
import getPlatform

@Composable
fun DeviceInfoItem(label: String) {
    Card(Modifier.padding(12.dp, 0.dp)) {
        Text(label, modifier= Modifier.padding(6.dp, 0.dp).width(150.dp))
    }
}

@Composable
private fun NumericTextField(num: Int, onUpdate: (Int) -> Unit) {
    TextField(num.toString(16),
        {val v = it.toIntOrNull(16); if (v != null) onUpdate(v) },
        modifier = Modifier.padding(12.dp, 0.dp).width(120.dp))
}

@Composable
fun LocalDeviceConfiguration(vm: DeviceConfigurationViewModel) {
    val manufacturerId by remember { vm.manufacturerId }
    val familyId by remember { vm.familyId }
    val modelId by remember { vm.modelId }
    val versionId by remember { vm.versionId }
    val manufacturer by remember { vm.manufacturer }
    val family by remember { vm.family }
    val model by remember { vm.model }
    val version by remember { vm.version }
    val serialNumber by remember { vm.serialNumber }
    val maxSimultaneousPropertyRequests by remember { vm.maxSimultaneousPropertyRequests }
    val jsonSchemaString by remember { vm.jsonSchemaString }
    val update: ((info: MidiCIDeviceInfo) -> Unit) -> Unit = {
        val dev = MidiCIDeviceInfo(
            manufacturerId, familyId, modelId, versionId,
            manufacturer, family, model, version, serialNumber
        )
        it(dev)
        vm.updateDeviceInfo(dev)
    }
    Column(Modifier.padding(10.dp)) {
        Text("Local Device Configuration", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Note that each ID byte is in 7 bits. Hex more than 80h is invalid.", fontSize = 12.sp)
        Row {
            Column {
                Row {
                    DeviceInfoItem("Manufacturer")
                    Column {
                        Text("ID:")
                        Text("(3bytes)")
                    }
                    NumericTextField(manufacturerId) { value -> update { it.manufacturerId = value } }
                }
                Row {
                    DeviceInfoItem("Family")
                    Column {
                        Text("ID:")
                        Text("(2bytes)")
                    }
                    NumericTextField(familyId.toInt()) { value -> update { it.familyId = value.toShort() } }
                }
                Row {
                    DeviceInfoItem("Model")
                    Column {
                        Text("ID:")
                        Text("(2bytes)")
                    }
                    NumericTextField(modelId.toInt()) { value -> update { it.modelId = value.toShort() } }
                }
                Row {
                    DeviceInfoItem("Revision")
                    Column {
                        Text("ID:")
                        Text("(4bytes)")
                    }
                    NumericTextField(versionId) { value -> update { it.versionId = value } }
                }
                Row {
                    DeviceInfoItem("SerialNumber")
                }
            }
            Column {
                Row {
                    Text("Text:")
                    TextField(manufacturer, { value: String -> update { it.manufacturer = value } })
                }
                Row {
                    Text("Text:")
                    TextField(family, { value: String -> update { it.family = value } })
                }
                Row {
                    Text("Text:")
                    TextField(model, { value: String -> update { it.model = value } })
                }
                Row {
                    Text("Text:")
                    TextField(version, { value: String -> update { it.version = value } })
                }
                Row {
                    Text("Text:")
                    TextField(serialNumber ?: "", { value: String -> update { it.serialNumber = value } })
                }
            }
        }
        Divider()
        Row {
            DeviceInfoItem("max connections")
            TextField(
                "$maxSimultaneousPropertyRequests",
                { vm.updateMaxSimultaneousPropertyRequests(it.toByteOrNull() ?: maxSimultaneousPropertyRequests) },
                modifier = Modifier.padding(12.dp, 0.dp).width(120.dp))
        }
        Divider()
        Row {
            DeviceInfoItem("JSON Schema")
            Column {
                var schemaText by remember { mutableStateOf(jsonSchemaString) }
                TextField(
                    schemaText, { text: String -> schemaText = text }, minLines = 2,
                    modifier = Modifier.padding(12.dp, 0.dp).fillMaxWidth()
                )
                PropertyValueUploadButton(false, null) { bytes, _, _ ->
                    schemaText = bytes.toByteArray().decodeToString()
                    vm.updateJsonSchemaString(schemaText)
                }
                Button(onClick = { vm.updateJsonSchemaString(schemaText) }) {
                    Text("Update")
                }
            }
        }
    }
}
