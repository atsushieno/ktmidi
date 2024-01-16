package dev.atsushieno.ktmidi

internal actual fun stringToByteArray(s: String): ByteArray =
    s.map { it.code.toByte() }.toByteArray()

internal actual fun nativeByteOrder(): ByteOrder = ByteOrder.LITTLE_ENDIAN
