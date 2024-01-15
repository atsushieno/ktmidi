package dev.atsushieno.ktmidi

enum class ByteOrder {
    LITTLE_ENDIAN,
    BIG_ENDIAN;

    companion object {
        // FIXME: implement
        fun nativeOrder() = LITTLE_ENDIAN
    }
}

// FIXME: replace this with ktor-io toByteArray() once it gets ready for wasmJs target
fun String.toUtf8ByteArray() = stringToByteArray(this)

internal expect fun stringToByteArray(s: String): ByteArray
