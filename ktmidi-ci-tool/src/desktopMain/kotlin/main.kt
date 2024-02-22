import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.atsushieno.ktmidi.AlsaMidiAccess
import dev.atsushieno.ktmidi.JvmMidiAccess
import dev.atsushieno.ktmidi.RtMidiAccess
import dev.atsushieno.ktmidi.citool.view.App
import dev.atsushieno.ktmidi.citool.AppModel
import dev.atsushieno.ktmidi.citool.initializeAppModel
import java.io.File

fun main(args: Array<String>) = application {
    initializeAppModel(this)
    AppModel.midiDeviceManager.midiAccess =
        if (File("/dev/snd/seq").exists()) AlsaMidiAccess()
        else if (args.contains("jvm")) JvmMidiAccess()
        else if (System.getProperty("os.name").contains("Windows")) JvmMidiAccess()
        else RtMidiAccess() // rtmidi-javacpp does not support Windows build nowadays.
    Window(onCloseRequest = ::exitApplication,
        state = rememberWindowState(),
        title = "midi-ci-tool") {
        App()
    }
}

@Preview
@Composable
fun AppDesktopPreview() {
    App()
}