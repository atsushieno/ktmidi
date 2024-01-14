import androidx.compose.ui.window.ComposeUIViewController
import dev.atsushieno.ktmidi.CoreMidiAccess
import dev.atsushieno.ktmidi.citool.AppModel
import dev.atsushieno.ktmidi.citool.initializeAppModel
import dev.atsushieno.ktmidi.citool.view.App

fun MainViewController() = ComposeUIViewController {
    initializeAppModel(null)
    AppModel.midiDeviceManager.midiAccess = CoreMidiAccess()
    App()
}
