import androidx.compose.runtime.Composable

interface Platform {
    val name: String

    val canReadLocalFile: Boolean

    fun loadFileContent(path: String): ByteArray
    fun saveFileContent(path: String, bytes: ByteArray)

    @Composable
    fun BinaryFilePicker(show: Boolean, fileChosen: (String?) -> Unit)
}

expect fun getPlatform(): Platform