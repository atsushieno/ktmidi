import androidx.compose.runtime.Composable
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import java.nio.file.Files
import java.nio.file.Path

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val canReadLocalFile = true
    override fun loadFileContent(path: String): ByteArray =
        Files.readAllBytes(Path.of(path))

    override fun saveFileContent(path: String, bytes: ByteArray) {
        Files.write(Path.of(path), bytes)
    }

    @Composable
    override fun BinaryFilePicker(show: Boolean, fileChosen: (String?) -> Unit) =
        FilePicker(show = show) { fileChosen(it?.path) }
}

actual fun getPlatform(): Platform = JVMPlatform()