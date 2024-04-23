package dev.atsushieno.ktmidi
import io.ktor.utils.io.core.*

typealias KtorByteOrder = io.ktor.utils.io.core.ByteOrder

internal actual fun stringToByteArray(s: String): ByteArray = s.toByteArray()
internal actual fun nativeByteOrder(): ByteOrder = when(KtorByteOrder.nativeOrder()) {
    KtorByteOrder.BIG_ENDIAN -> ByteOrder.BIG_ENDIAN
    KtorByteOrder.LITTLE_ENDIAN -> ByteOrder.LITTLE_ENDIAN
    else -> throw UnsupportedOperationException()
}
