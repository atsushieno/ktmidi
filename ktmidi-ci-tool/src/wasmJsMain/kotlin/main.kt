import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import dev.atsushieno.ktmidi.citool.AppModel
import dev.atsushieno.ktmidi.citool.initializeAppModel
import dev.atsushieno.ktmidi.citool.view.App
/*
import dev.atsushieno.ktmidi.JzzMidiAccess
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
*/

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initializeAppModel(null)
    /*GlobalScope.launch {
        AppModel.midiDeviceManager.midiAccess = JzzMidiAccess.create(true)
    }*/
    CanvasBasedWindow(canvasElementId = "ComposeTarget") { App() }
}