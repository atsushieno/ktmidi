package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import dev.atsushieno.ktmidi.ci.*
import getPlatform

@Composable
fun PropertyColumn(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Card(modifier = Modifier.width(180.dp).padding(12.dp, 0.dp)) { Text(label, Modifier.padding(12.dp, 0.dp)) }
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyListEntry(propertyId: String, isSelected: Boolean, selectedPropertyChanged: (property: String) -> Unit) {
    val border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
    Card(border = border, onClick = {
        selectedPropertyChanged(propertyId)
    }) {
        Text(modifier = Modifier.padding(12.dp, 0.dp), text = propertyId)
    }
}

@Composable
fun PropertyMetadataList(def: PropertyMetadata, readOnly: Boolean, schemaString: String? = null, updateSchemaString: (String)->Unit = {}) {
    Column {
        Text("Property Metadata", fontWeight = FontWeight.Bold, fontSize = TextUnit(1.2f, TextUnitType.Em))

        // FIXME: make them remembered mutableStateOf<T>.
        PropertyColumn("resource") { TextField(def.resource, { def.resource = it }, readOnly = readOnly) }
        PropertyColumn("canGet") { Checkbox(def.canGet, { def.canGet = it }, enabled = !readOnly) }
        PropertyColumn("canSet") { TextField(def.canSet, { def.canSet = it }, readOnly = readOnly) }
        PropertyColumn("canSubscribe") { Checkbox(def.canSubscribe, { def.canSubscribe = it }, enabled = !readOnly) }
        PropertyColumn("requireResId") { Checkbox(def.requireResId, { def.requireResId = it }, enabled = !readOnly) }
        PropertyColumn("mediaTypes") { TextField(def.mediaTypes.joinToString("\n"), { def.mediaTypes = it.split('\n') }, readOnly = readOnly, minLines = 2) }
        PropertyColumn("encodings") { TextField(def.encodings.joinToString("\n"), { def.encodings = it.split('\n') }, readOnly = readOnly, minLines = 2) }
        val schemaStringNullable by remember { mutableStateOf(schemaString ?: if (def.schema == null) "" else Json.getUnescapedString(def.schema!!)) }
        PropertyColumn("schema") { TextField(schemaStringNullable, { updateSchemaString(it) }, readOnly = readOnly, minLines = 2) }
        PropertyColumn("canPaginate") { Checkbox(def.canPaginate, {}, enabled = !readOnly) }
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
fun PropertyValueEditor(mediaType: String, def: PropertyMetadata?, property: PropertyValue,
                        onGetValueClick: () -> Unit,
                        onCommitSetPropertyClick: (List<Byte>, Boolean) -> Unit) {
    Column {
        Text("Property Value", fontWeight = FontWeight.Bold, fontSize = TextUnit(1.2f, TextUnitType.Em))

        val isEditableByMetadata = def?.canSet != null && def.canSet != PropertySetAccess.NONE
        val isTextRenderable = mediaType == CommonRulesKnownMimeTypes.APPLICATION_JSON
        val showGetButton = @Composable {
            Button(onClick = { onGetValueClick() }) {
                Text("Refresh")
            }
        }
        val commitSetProperty: (List<Byte>, Boolean) -> Unit = { bytes, isPartial ->
            onCommitSetPropertyClick(bytes, isPartial)
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
                                commitSetProperty(bytes, partial.isNotBlank())
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
