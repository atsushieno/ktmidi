package dev.atsushieno.ktmidi.citool.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.atsushieno.ktmidi.ci.Message
import dev.atsushieno.ktmidi.ci.Message.Companion.addressString
import dev.atsushieno.ktmidi.ci.Message.Companion.muidString

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogScreen() {
    Column(Modifier.padding(10.dp)) {
        Button(onClick = { ViewModel.clearLogs() }) {
            Text("Clear")
        }
        LazyColumn(contentPadding = PaddingValues(10.dp, 0.dp)) {
            items(ViewModel.logs.toList()) {
                Row {
                    Text(it.timestamp.time.toString().substring(0, 12), fontSize = 14.sp)
                    Text(it.direction.name, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.width(50.dp))
                    val msg = it.data
                    if (msg is Message) {
                        Column {
                            Text("@" + msg.address.addressString, Modifier.width(150.dp).padding(10.dp, 0.dp), fontSize = 14.sp)
                            Text(msg.sourceMUID.muidString, Modifier.width(150.dp).padding(10.dp, 0.dp), fontSize = 14.sp)
                            Text("->" + msg.destinationMUID.muidString, Modifier.width(150.dp).padding(10.dp, 0.dp), fontSize = 14.sp)
                        }
                        TextField(msg.label + ": " + msg.bodyString, {}, readOnly = true)
                    }
                    else {
                        Text("", Modifier.width(150.dp))
                        TextField(it.data.toString(), {}, readOnly = true)
                    }
                }
            }
        }
    }
}