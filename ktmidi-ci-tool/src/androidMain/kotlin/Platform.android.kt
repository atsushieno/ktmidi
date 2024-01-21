import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.Composable
import com.darkrockstudios.libraries.mpfilepicker.FilePicker

class AndroidPlatform(private val context: Context) : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val canReadLocalFile = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
    private val spName = "ktmidi-ci-tool"
    private val spKeyName = "ktmidi-ci-tool"
    override fun loadFileContent(path: String): ByteArray =
        (context.getSharedPreferences(spName, Context.MODE_PRIVATE).getString(spKeyName, null) ?: "{}").encodeToByteArray()

    override fun saveFileContent(path: String, bytes: ByteArray) {
        context.getSharedPreferences(spName, Context.MODE_PRIVATE).edit().putString(spKeyName, bytes.decodeToString()).apply()
    }

    @Composable
    override fun BinaryFilePicker(show: Boolean, fileChosen: (String?) -> Unit) =
        FilePicker(show = show) { fileChosen(it?.path) }
}

var androidPlatform: AndroidPlatform? = null

actual fun getPlatform(): Platform = androidPlatform!!
