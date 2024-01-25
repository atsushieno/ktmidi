package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.ci.json.Json
import dev.atsushieno.ktmidi.ci.json.JsonParserException
import dev.atsushieno.ktmidi.ci.propertycommonrules.CommonRulesKnownMimeTypes
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyResourceColumn
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertySetAccess
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
        Text("Property Metadata", fontWeight = FontWeight.Bold, fontSize = 20.sp)

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
        val columns = remember { mutableStateListOf<PropertyResourceColumn>().apply { addAll(def.columns) } }

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
            columns.clear()
            columns.addAll(def.columns)
            prev = def
        }

        val updateButton = @Composable { if (!readOnly) Button(onClick = {
            schemaParserError = ""
            metadataUpdateCommitted(PropertyMetadata().also {
                it.resource = resource
                it.canGet = canGet
                it.canSet = canSet
                it.canSubscribe = canSubscribe
                it.requireResId = requireResId
                it.mediaTypes = mediaTypes.split('\n')
                it.encodings = encodings.split('\n')
                it.schema = schema.ifEmpty { null }
                it.canPaginate = canPaginate
                it.columns = columns.toList()
            })
        }) {
            Text("Update Metadata")
        }}
        updateButton()
        PropertyColumn("resource") { TextField(resource, { resource = it }, readOnly = readOnly) }
        PropertyColumn("canGet") { Checkbox(canGet, { canGet = it }, enabled = !readOnly) }
        PropertyColumn("canSet") { PropertySetAccessSelector(canSet, { canSet = it }, readOnly = readOnly) }
        PropertyColumn("canSubscribe") { Checkbox(canSubscribe, { canSubscribe = it }, enabled = !readOnly) }
        PropertyColumn("requireResId") { Checkbox(requireResId, { requireResId = it }, enabled = !readOnly) }
        PropertyColumn("mediaTypes") { TextField(mediaTypes, { mediaTypes = it }, readOnly = readOnly, minLines = 2) }
        PropertyColumn("encodings") { TextField(encodings, { encodings = it }, readOnly = readOnly, minLines = 2) }
        PropertyColumn("schema") {
            Column {
                TextField(schema, { schema = it }, readOnly = readOnly, minLines = 2, maxLines = 10)
                if (schemaParserError.isNotBlank())
                    Text(schemaParserError)
            }
        }
        PropertyColumn("canPaginate") { Checkbox(canPaginate, { canPaginate = it }, enabled = !readOnly) }
        PropertyColumn("columns") {
            Column {
                columns.forEachIndexed { index, it ->
                    var titleText by remember { mutableStateOf(it.title) }
                    var isLink by remember { mutableStateOf(it.link?.isNotEmpty() ?: false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(titleText, { s: String -> titleText = s; it.title = s }, readOnly = readOnly)
                        if (!readOnly) {
                            Button(onClick = { columns.removeAt(index) }) {
                                Image(Icons.Default.Delete, "Delete")
                            }
                        }
                    }
                    var linkText by remember { mutableStateOf(it.link) }
                    var propertyText by remember { mutableStateOf(it.property) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Link?", fontSize = 12.sp)
                        Checkbox(isLink, { isLink = it }, enabled = !readOnly)
                        TextField(
                            if (isLink) linkText ?: "" else propertyText ?: "",
                            { s: String ->
                                if (isLink) {
                                    linkText = s
                                    it.link = s
                                } else {
                                    propertyText = s
                                    it.property = s
                                }
                            }, readOnly = readOnly,
                            modifier = Modifier.width(180.dp))
                    }
                }
                if (!readOnly) {
                    Button(onClick = {
                        columns.add(PropertyResourceColumn())
                    }) {
                        Image(Icons.Default.Add, "Add")
                    }
                }
            }
        }
        updateButton()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyEncodingSelector(encodings: List<String>,
                             selectedEncoding: String,
                             onSelectionChange: (String)->Unit) {
    var mutualEncodingsExposed by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(mutualEncodingsExposed, { mutualEncodingsExposed = !mutualEncodingsExposed }) {
        TextField(
            modifier = Modifier.menuAnchor(),
            value = selectedEncoding, onValueChange = {},
            label = { Text("With mutualEncoding?") },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mutualEncodingsExposed) },
            colors = ExposedDropdownMenuDefaults.textFieldColors()
        )
        ExposedDropdownMenu(
            expanded = mutualEncodingsExposed,
            onDismissRequest = {
                mutualEncodingsExposed = false
            },
        ) {
            // menu items
            encodings.forEach { encoding ->
                DropdownMenuItem(
                    text = { Text(encoding) },
                    onClick = {
                        onSelectionChange(encoding)
                        mutualEncodingsExposed = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertySetAccessSelector(canSet: String,
                              onSelectionChange: (String)->Unit,
                              readOnly: Boolean) {
    var exposed by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(exposed, { if (!readOnly) exposed = !exposed }) {
        TextField(
            modifier = Modifier.menuAnchor(),
            value = canSet, onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = exposed) },
            colors = ExposedDropdownMenuDefaults.textFieldColors()
        )
        ExposedDropdownMenu(
            expanded = exposed,
            onDismissRequest = {
                exposed = false
            },
        ) {
            // menu items
            listOf("none", "full", "partial").forEach { canSet ->
                DropdownMenuItem(
                    text = { Text(canSet) },
                    onClick = {
                        onSelectionChange(canSet)
                        exposed = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
fun PropertyValueEditor(isLocalEditor: Boolean,
                        mediaType: String,
                        metadata: PropertyMetadata?,
                        body: List<Byte>,
                        refreshValueClicked: (requestedEncoding: String?, paginateOffset: Int?, paginateLimit: Int?) -> Unit,
                        isSubscribing: Boolean,
                        subscriptionChanged: (newSubscribing: Boolean, requestedEncoding: String?) -> Unit,
                        commitChangeClicked: (List<Byte>, String?, Boolean) -> Unit) {
    // It is saved to optionally reset cached state
    var prev by remember { mutableStateOf(metadata) }
    val resetState = prev != metadata

    Column {
        Text("Property Value", fontWeight = FontWeight.Bold, fontSize = 20.sp)

        val isEditableByMetadata = metadata?.canSet != null && metadata.canSet != PropertySetAccess.NONE
        val isEditable = metadata?.originator == PropertyMetadata.Originator.USER && (isLocalEditor || isEditableByMetadata)
        val isTextRenderable = mediaType == CommonRulesKnownMimeTypes.APPLICATION_JSON
        var editing by remember { mutableStateOf(false) }
        val showRefreshAndSubscribeButtons = @Composable {
            if (!isLocalEditor && !editing) {
                var paginateOffset by remember { mutableStateOf("0") }
                var paginateLimit by remember { mutableStateOf("9999") }
                Row {
                    var selectedEncoding by remember { mutableStateOf<String?>(null) }
                    if (resetState)
                        selectedEncoding = null
                    Button(onClick = {
                        if (metadata?.canPaginate == true) {
                            val offset = paginateOffset.toIntOrNull()
                            val limit = paginateLimit.toIntOrNull()
                            // FIXME: maybe we should warn number parsing errors.
                            refreshValueClicked(selectedEncoding, offset, limit)
                        }
                        else
                            refreshValueClicked(selectedEncoding, null, null)
                    }) {
                        Text("Refresh")
                    }
                    if (metadata?.canSubscribe == true) {
                        Button(onClick = { subscriptionChanged(!isSubscribing, selectedEncoding) }) {
                            Text(if (isSubscribing) "Unsubscribe" else "Subscribe")
                        }
                    }
                    // encoding selector
                    PropertyEncodingSelector(
                        metadata?.encodings ?: listOf(),
                        selectedEncoding ?: "",
                        onSelectionChange = { selectedEncoding = it.ifEmpty { null } })
                }
                Row {
                    if (metadata?.canPaginate == true) {
                        Text("Pagenate? offset: ")
                        TextField(paginateOffset, { paginateOffset = it }, Modifier.width(80.dp))
                        Text(" limit: ")
                        TextField(paginateLimit, { paginateLimit = it }, Modifier.width(80.dp))
                    }
                }
            }
        }
        var showFilePicker by remember { mutableStateOf(false) }
        if (resetState)
            showFilePicker = false
        if (resetState)
            editing = false
        var selectedEncoding by remember { mutableStateOf<String?>(null) }
        if (resetState)
            selectedEncoding = null
        val showUploadButton = @Composable {
            if (getPlatform().canReadLocalFile) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { showFilePicker = !showFilePicker }) {
                        Text("Set value by file (choose)")
                    }
                    getPlatform().BinaryFilePicker(showFilePicker) { file ->
                        showFilePicker = false
                        if (file != null) {
                            val bytes = getPlatform().loadFileContent(file).toList()
                            commitChangeClicked(bytes, selectedEncoding, false)
                            editing = false
                        }
                    }
                }
            }
        }

        if (isTextRenderable) {
            val bodyText = MidiCIConverter.decodeASCIIToString(body.toByteArray().decodeToString())
            if (isEditable) {
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
                        // There is no reason to support partial editor on Local property.
                        if (isEditable && !isLocalEditor) {
                            if (metadata?.canSet == PropertySetAccess.PARTIAL) {
                                TextField(partial, { partial = it },
                                    label = { Text("Partial? RFC6901 Pointer here then:") }
                                )
                            }
                            showRefreshAndSubscribeButtons()
                        }
                    }
                    TextField(text, { text = it }, minLines = 2, maxLines = 10)
                    if (isEditable) {
                        Button(onClick = {
                            val jsonString = Json.getEscapedString(partial.ifEmpty { text })
                            val bytes =
                                MidiCIConverter.encodeStringToASCII(jsonString).encodeToByteArray().toList()
                            commitChangeClicked(bytes, selectedEncoding, partial.isNotBlank())
                            editing = false
                        }) {
                            Text("Commit changes")
                        }
                    }
                    Text("... or ...")
                    showUploadButton()
                    PropertyEncodingSelector(metadata?.encodings ?: listOf(), selectedEncoding ?: "", onSelectionChange = { selectedEncoding = it.ifEmpty { null } })
                } else {
                    showRefreshAndSubscribeButtons()
                    TextField(bodyText, {}, readOnly = true, minLines = 2, maxLines = 10)
                }
            } else {
                Text("read-only")
                showRefreshAndSubscribeButtons()
                TextField(bodyText, {}, readOnly = true, minLines = 2, maxLines = 10)
            }
        } else {
            Text("MIME type '$mediaType' not supported for editing")
            showRefreshAndSubscribeButtons()
            if (isEditable)
                showUploadButton()
            PropertyEncodingSelector(metadata?.encodings ?: listOf(), selectedEncoding ?: "", onSelectionChange = { selectedEncoding = it.ifEmpty { null } })
        }

        prev = metadata
    }
}
