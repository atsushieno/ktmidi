package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.atsushieno.ktmidi.ci.MidiCIDeviceInfo

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
    val update: ((info: MidiCIDeviceInfo) -> Unit) -> Unit = {
        val dev = MidiCIDeviceInfo(
            manufacturerId, familyId, modelId, versionId,
            manufacturer, family, model, version, serialNumber
        )
        it(dev)
        vm.updateDeviceInfo(dev)
    }
    Column {
        Text("Local Device Configuration", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Note that each ID byte is in 7 bits. Hex more than 80h is invalid.", fontSize = 12.sp)
        Row {
            Column(Modifier.border(1.dp, MaterialTheme.colorScheme.primaryContainer)) {
                Row {
                    DeviceInfoItem("Manufacturer")
                    Text("ID (3bytes):")
                    NumericTextField(manufacturerId) { value -> update { it.manufacturerId = value } }
                }
                Row {
                    DeviceInfoItem("Family")
                    Text("ID (2bytes):")
                    NumericTextField(familyId.toInt()) { value -> update { it.familyId = value.toShort() } }
                }
                Row {
                    DeviceInfoItem("Model")
                    Text("ID (2bytes):")
                    NumericTextField(modelId.toInt()) { value -> update { it.modelId = value.toShort() } }
                }
                Row {
                    DeviceInfoItem("Revision")
                    Text("ID (4bytes):")
                    NumericTextField(versionId) { value -> update { it.versionId = value } }
                }
                Row {
                    DeviceInfoItem("SerialNumber")
                }
            }
            Column(Modifier.border(1.dp, MaterialTheme.colorScheme.primaryContainer)) {
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
            DeviceItemCard("max connections")
            TextField(
                "$maxSimultaneousPropertyRequests",
                { value: String ->
                    vm.updateMaxSimultaneousPropertyRequests(
                        value.toByteOrNull() ?: maxSimultaneousPropertyRequests
                    )
                })
        }
    }
}