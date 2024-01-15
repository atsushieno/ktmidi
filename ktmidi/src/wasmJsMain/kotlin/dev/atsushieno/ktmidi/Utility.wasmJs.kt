package dev.atsushieno.ktmidi

internal actual fun stringToByteArray(s: String): ByteArray =
    s.map { it.code.toByte() }.toByteArray()
