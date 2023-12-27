class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
    override val canReadLocalFile = false
    override fun loadFileContent(path: String): List<Byte> =
        TODO("FIXME: implement")

    @Composable
    override fun BinaryFilePicker(show: Boolean, fileChosen: (String?) -> Unit) =
        TODO("FIXME: implement")
}

actual fun getPlatform(): Platform = WasmPlatform()