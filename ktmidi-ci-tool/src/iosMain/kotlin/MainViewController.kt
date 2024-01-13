import androidx.compose.ui.window.ComposeUIViewController
import dev.atsushieno.ktmidi.citool.initializeAppModel
import dev.atsushieno.ktmidi.citool.view.App

fun MainViewController() = ComposeUIViewController {
    initializeAppModel(null)
    App()
}
