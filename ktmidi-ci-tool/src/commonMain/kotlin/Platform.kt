interface Platform {
    val name: String

    val canReadLocalFile: Boolean

    fun loadFileContent(path: String): ByteArray
    fun saveFileContent(path: String, bytes: ByteArray)
}

expect fun getPlatform(): Platform