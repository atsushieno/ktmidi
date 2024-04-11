import androidx.compose.ui.window.ComposeUIViewController
import dev.atsushieno.ktmidi.UmpCoreMidiAccess
import dev.atsushieno.ktmidi.citool.AppModel
import dev.atsushieno.ktmidi.citool.initializeAppModel
import dev.atsushieno.ktmidi.citool.view.App

fun MainViewController() = ComposeUIViewController {
    initializeAppModel(null)
    AppModel.midiDeviceManager.midiAccess = UmpCoreMidiAccess()
    App()
}
