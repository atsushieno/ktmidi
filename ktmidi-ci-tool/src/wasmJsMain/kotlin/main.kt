import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import dev.atsushieno.ktmidi.WebMidiAccess
import dev.atsushieno.ktmidi.citool.AppModel
import dev.atsushieno.ktmidi.citool.initializeAppModel
import dev.atsushieno.ktmidi.citool.view.App

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initializeAppModel(null)
    AppModel.midiDeviceManager.midiAccess = WebMidiAccess()
    CanvasBasedWindow(canvasElementId = "ComposeTarget") { App() }
}