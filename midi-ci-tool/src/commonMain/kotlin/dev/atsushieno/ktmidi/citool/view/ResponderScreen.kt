package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import dev.atsushieno.ktmidi.ci.MidiCIDeviceInfo

@Composable
fun ResponderScreen() {
    Text(FeatureDescription.responderScreen)
    LocalDeviceConfiguration(ViewModel.localDeviceConfiguration)
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
        DeviceItemCard("max connections")
        TextField("${vm.maxSimultaneousPropertyRequests}", { value: String -> vm.updateMaxSimultaneousPropertyRequests(value.toByteOrNull() ?: maxSimultaneousPropertyRequests) })
    }
}
