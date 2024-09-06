import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.atsushieno.ktmidi.*
import dev.atsushieno.ktmidi.citool.view.App
import dev.atsushieno.ktmidi.citool.AppModel
import dev.atsushieno.ktmidi.citool.initializeAppModel
import java.io.File

fun main(args: Array<String>) = application {
    initializeAppModel(this)
    AppModel.midiDeviceManager.midiAccess =
        if (args.contains("alsa"))
            AlsaMidiAccess()
        else if (args.contains("jvm"))
            JvmMidiAccess()
        // else RtMidiAccess()
        else if (System.getProperty("os.name").contains("Windows"))
            LibreMidiAccess.create(MidiTransportProtocol.MIDI1)
        else
            LibreMidiAccess.create(MidiTransportProtocol.UMP)
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