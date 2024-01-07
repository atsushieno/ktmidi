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
fun PropertyMetadataEditor(def: PropertyMetadata,
                           metadataUpdateCommitted: (PropertyMetadata)->Unit,
                           readOnly: Boolean) {

    Column {
        Text("Property Metadata", fontWeight = FontWeight.Bold, fontSize = TextUnit(1.2f, TextUnitType.Em))

        var prev by remember { mutableStateOf(def) }

        var resource by remember { mutableStateOf(def.resource) }
        var canGet by remember { mutableStateOf(def.canGet) }
        var canSet by remember { mutableStateOf(def.canSet) }
        var canSubscribe by remember { mutableStateOf(def.canSubscribe) }
        var requireResId by remember { mutableStateOf(def.requireResId) }
        var mediaTypes by remember { mutableStateOf(def.mediaTypes.joinToString("\n")) }
        var encodings by remember { mutableStateOf(def.encodings.joinToString("\n")) }
        var schema by remember { mutableStateOf(if (def.schema == null) "" else Json.getUnescapedString(def.schema!!)) }
        var canPaginate by remember { mutableStateOf(def.canPaginate) }
        // FIXME: columns too

        var schemaParserError by remember { mutableStateOf("") }

        if (prev != def) {
            // refresh the data entries
            resource = def.resource
            canGet = def.canGet
            canSet = def.canSet
            canSubscribe = def.canSubscribe
            requireResId = def.requireResId
            mediaTypes = def.mediaTypes.joinToString("\n")
            encodings = def.encodings.joinToString("\n")
            schema = if (def.schema == null) "" else Json.getUnescapedString(def.schema!!)
            canPaginate = def.canPaginate
            // FIXME: columns too
            prev = def
        }

        val updateButton = @Composable { if (!readOnly) Button(onClick = {
            schemaParserError = ""
            val schemaJson = if (schema.isNotBlank()) {
                try {
                    Json.parse(schema)
                } catch(ex: JsonParserException) {
                    schemaParserError = ex.message ?: "JSON Schema parser error"
                    return@Button
                }
            } else null
            metadataUpdateCommitted(PropertyMetadata().also {
                it.resource = resource
                it.canGet = canGet
                it.canSet = canSet
                it.canSubscribe = canSubscribe
                it.requireResId = requireResId
                it.mediaTypes = mediaTypes.split('\n')
                it.encodings = encodings.split('\n')
                it.schema = schemaJson
                it.canPaginate = canPaginate
                // FIXME: columns too
            })
        }) {
            Text("Update")
        }}
        updateButton()
        PropertyColumn("resource") { TextField(resource, { resource = it }, readOnly = readOnly) }
        PropertyColumn("canGet") { Checkbox(canGet, { canGet = it }, enabled = !readOnly) }
        PropertyColumn("canSet") { TextField(canSet, { canSet = it }, readOnly = readOnly) }
        PropertyColumn("canSubscribe") { Checkbox(canSubscribe, { canSubscribe = it }, enabled = !readOnly) }
        PropertyColumn("requireResId") { Checkbox(requireResId, { requireResId = it }, enabled = !readOnly) }
        PropertyColumn("mediaTypes") { TextField(mediaTypes, { mediaTypes = it }, readOnly = readOnly, minLines = 2) }
        PropertyColumn("encodings") { TextField(encodings, { encodings = it }, readOnly = readOnly, minLines = 2) }
        PropertyColumn("schema") {
            Column {
                TextField(schema, { schema = it }, readOnly = readOnly, minLines = 2)
                if (schemaParserError.isNotBlank())
                    Text(schemaParserError)
            }
        }
        PropertyColumn("canPaginate") { Checkbox(canPaginate, { canPaginate = it }, enabled = !readOnly) }
        //  FIXME: implement add/remove
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
        updateButton()
    }
}

@Composable
fun PropertyValueEditor(isLocalEditor: Boolean,
                        mediaType: String,
                        metadata: PropertyMetadata?,
                        body: List<Byte>,
                        refreshValueClicked: () -> Unit,
                        commitChangeClicked: (List<Byte>, Boolean) -> Unit) {
    // It is saved to optionally reset cached state
    var prev by remember { mutableStateOf(metadata) }
    val resetState = prev != metadata

    Column {
        Text("Property Value", fontWeight = FontWeight.Bold, fontSize = TextUnit(1.2f, TextUnitType.Em))

        val isEditableByMetadata = metadata?.canSet != null && metadata.canSet != PropertySetAccess.NONE
        val isEditable = isLocalEditor || isEditableByMetadata
        val isTextRenderable = mediaType == CommonRulesKnownMimeTypes.APPLICATION_JSON
        val showRefreshButton = @Composable {
            if (!isLocalEditor)
                Button(onClick = { refreshValueClicked() }) {
                    Text("Refresh")
                }
        }
        var showFilePicker by remember { mutableStateOf(false) }
        if (resetState)
            showFilePicker = false
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
                            commitChangeClicked(bytes, false)
                        }
                    }
                }
            }
        }

        if (isTextRenderable) {
            val bodyText = PropertyCommonConverter.decodeASCIIToString(body.toByteArray().decodeToString())
            if (isEditable) {
                var editing by remember { mutableStateOf(false) }
                if (resetState)
                    editing = false
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(editing, { editing = !editing })
                    Text("edit")
                }
                if (editing) {
                    var text by remember { mutableStateOf(bodyText) }
                    var partial by remember { mutableStateOf("") }
                    if (resetState) {
                        text = bodyText
                        partial = ""
                    }
                    Row {
                        if (isEditable) {
                            if (metadata?.canSet == PropertySetAccess.PARTIAL) {
                                TextField(partial, { partial = it }, label = {
                                    Text("Partial? RFC6901 Pointer here then:")
                                })
                            }
                            showRefreshButton()
                            Button(onClick = {
                                val jsonString = Json.getEscapedString(partial.ifEmpty { text })
                                val bytes =
                                    PropertyCommonConverter.encodeStringToASCII(jsonString).encodeToByteArray().toList()
                                commitChangeClicked(bytes, partial.isNotBlank())
                            }) {
                                Text("Commit changes")
                            }
                        }
                    }
                    TextField(text, { text = it })
                    showUploadButton()
                } else {
                    showRefreshButton()
                    TextField(bodyText, {}, readOnly = true)
                }
            } else {
                Text("read-only")
                showRefreshButton()
                TextField(bodyText, {}, readOnly = true)
            }
        } else {
            Text("MIME type '$mediaType' not supported for editing")
            showRefreshButton()
            if (isEditable)
                showUploadButton()
        }

        prev = metadata
    }
}
