import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import dev.atsushieno.ktmidi.WebMidiAccess
import dev.atsushieno.ktmidi.citool.AppModel
import dev.atsushieno.ktmidi.citool.initializeAppModel
import dev.atsushieno.ktmidi.citool.view.App
import kotlinx.coroutines.*

@OptIn(ExperimentalComposeUiApi::class, DelicateCoroutinesApi::class)
fun main() {
    initializeAppModel(null)
    AppModel.midiDeviceManager.midiAccess = WebMidiAccess()
    MainScope().launch { // we need this for delay
        while (!WebMidiAccess.isReady)
            delay(1)
        CanvasBasedWindow(canvasElementId = "ComposeTarget") { App() }
    }
}