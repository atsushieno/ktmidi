package dev.atsushieno.ktmidi

enum class ByteOrder {
    LITTLE_ENDIAN,
    BIG_ENDIAN;

    companion object {
        // FIXME: replace this with ktor-io ByteOrder.nativeOrder() once it gets ready for wasmJs target
        fun nativeOrder() = nativeByteOrder()
    }
}

// FIXME: replace this with ktor-io toByteArray() once it gets ready for wasmJs target
fun String.toUtf8ByteArray() = stringToByteArray(this)

internal expect fun stringToByteArray(s: String): ByteArray

internal expect fun nativeByteOrder(): ByteOrder

internal sealed class ByteSource {
    data class Bytes(val array: ByteArray) : ByteSource()
    data class BytesList(val list: List<Byte>) : ByteSource()

    val size: Int get() = when(this){
        is Bytes -> array.size
        is BytesList -> list.size
    }

    operator fun get(index: Int): Byte = when(this){
        is Bytes -> array[index]
        is BytesList -> list[index]
    }

    fun copyOfRange(fromIndex: Int, toIndex: Int): ByteSource = when(this) {
        is Bytes -> Bytes(array.copyOfRange(fromIndex, toIndex))
        is BytesList -> BytesList(list.subList(fromIndex, toIndex))
    }

    fun append(other: ByteSource): ByteSource = when (this) {
        is Bytes -> when (other) {
            is Bytes -> Bytes(array + other.array)
            is BytesList -> Bytes(array + other.list)
        }

        is BytesList -> when (other) {
            is Bytes -> BytesList(list + other.array.asList())
            is BytesList -> BytesList(list + other.list)
        }
    }

    fun asByteArray(): ByteArray = when(this) {
        is Bytes -> array
        is BytesList -> list.toByteArray()
    }

    fun asList(): List<Byte> = when(this) {
        is Bytes -> array.toList()
        is BytesList -> list
    }
}