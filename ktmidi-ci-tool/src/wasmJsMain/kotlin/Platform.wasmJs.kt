import androidx.compose.runtime.Composable
import dev.atsushieno.ktmidi.toUtf8ByteArray
import kotlinx.browser.window

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
    override val canReadLocalFile = false
    override fun loadFileContent(path: String): ByteArray =
        window.localStorage.getItem(path)?.toUtf8ByteArray() ?: byteArrayOf()
    override fun saveFileContent(path: String, bytes: ByteArray) =
        window.localStorage.setItem(path, bytes.decodeToString())

    @Composable
    override fun BinaryFilePicker(show: Boolean, fileChosen: (String?) -> Unit) =
        TODO("FIXME: implement")
}

actual fun getPlatform(): Platform = WasmPlatform()