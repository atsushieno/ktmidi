import androidx.compose.runtime.Composable
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override val canReadLocalFile = false
    override fun loadFileContent(path: String): ByteArray =
        TODO("FIXME: implement")

    override fun saveFileContent(path: String, bytes: ByteArray) {
        TODO("Not yet implemented")
    }

    @Composable
    override fun BinaryFilePicker(show: Boolean, fileChosen: (String?) -> Unit) =
        FilePicker(show = show) { fileChosen(it?.path) }
}

actual fun getPlatform(): Platform = IOSPlatform()