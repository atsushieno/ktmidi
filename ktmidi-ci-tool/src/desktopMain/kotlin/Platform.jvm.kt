import androidx.compose.runtime.Composable
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
    override val canReadLocalFile = true

    private val toolName = "ktmidi-ci-tool"

    private fun getPath(path: String): Path {
        val file = File(path)
        return if (file.isAbsolute)
            Path.of(path)
        else
            Path.of(ensureAppConfigPath(), toolName, path)
    }
    override fun loadFileContent(path: String): ByteArray =
        Files.readAllBytes(getPath(path))
    override fun saveFileContent(path: String, bytes: ByteArray) {
        Files.write(getPath(path), bytes)
    }

    private fun ensureAppConfigPath(): String {
        val os = System.getProperty("os.name")
        val ret = when {
            os.startsWith("windows", true) -> System.getenv("LOCALAPPDATA")
            os.startsWith("mac", true) -> System.getProperty("user.home") + "/Library/Application Support"
            else -> System.getProperty("user.home") + "/.config"
        }
        val toolDir = Path.of(ret, toolName)
        if (!toolDir.exists())
            Files.createDirectory(toolDir)
        return ret
    }

    @Composable
    override fun BinaryFilePicker(show: Boolean, fileChosen: (String?) -> Unit) =
        FilePicker(show = show) { fileChosen(it?.path) }
}

actual fun getPlatform(): Platform = JVMPlatform()