import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import java.nio.file.*

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val canReadLocalFile = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
    override fun loadFileContent(path: String): ByteArray =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Files.readAllBytes(FileSystems.getDefault().getPath(path))
        else TODO("Not supported on Android N or earlier")

    override fun saveFileContent(path: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Files.write(FileSystems.getDefault().getPath(path), bytes)
        else TODO("Not supported on Android N or earlier")
    }

    @Composable
    override fun BinaryFilePicker(show: Boolean, fileChosen: (String?) -> Unit) =
        FilePicker(show = show) { fileChosen(it?.path) }
}

actual fun getPlatform(): Platform = AndroidPlatform()