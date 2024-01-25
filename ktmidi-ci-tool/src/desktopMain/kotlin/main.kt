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
        // FIXME: enable AlsaMidiAccess once we fixed https://github.com/atsushieno/alsakt/issues/3
        /*if (File("/dev/snd/seq").exists()) AlsaMidiAccess()
        else*/ if (args.contains("jvm")) JvmMidiAccess()
        //else if (System.getProperty("os.name").contains("Mac OS", true) &&
        //    System.getProperty("os.arch").contains("aarch64")) JvmMidiAccess()
        else RtMidiAccess()
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