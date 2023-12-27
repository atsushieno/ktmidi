import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val canReadLocalFile = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
    override fun loadFileContent(path: String): List<Byte> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Files.readAllBytes(FileSystems.getDefault().getPath(path)).toList()
        else TODO("Not supported on Android N or earlier")

    @Composable
    override fun BinaryFilePicker(show: Boolean, fileChosen: (String?) -> Unit) =
        FilePicker(show = show) { fileChosen(it?.path) }
}

actual fun getPlatform(): Platform = AndroidPlatform()