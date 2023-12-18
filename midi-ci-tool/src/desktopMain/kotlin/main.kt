import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.atsushieno.ktmidi.citool.App

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "midi-ci-tool") {
        App()
    }
}

@Preview
@Composable
fun AppDesktopPreview() {
    App()
}