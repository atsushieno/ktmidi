import androidx.compose.runtime.Composable
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import kotlinx.cinterop.*
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create
import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override val canReadLocalFile = false
    @OptIn(ExperimentalForeignApi::class)
    override fun loadFileContent(path: String): ByteArray {
        val data = NSFileManager.defaultManager.contentsAtPath(path)
        return data?.bytes?.readBytes(data.length.toInt()) ?: byteArrayOf()
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun saveFileContent(path: String, bytes: ByteArray) {
        TODO("FIXME: implement")
        /*bytes.usePinned {
            NSFileManager.defaultManager.createFileAtPath(path, NSData(), null)
        }*/
    }

    @Composable
    override fun BinaryFilePicker(show: Boolean, fileChosen: (String?) -> Unit) =
        FilePicker(show = show) { fileChosen(it?.path) }
}

actual fun getPlatform(): Platform = IOSPlatform()