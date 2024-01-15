package dev.atsushieno.ktmidi.ci

internal infix fun Byte.shl(n: Int): Int = this.toInt() shl n
internal infix fun Byte.shr(n: Int): Int = this.toInt() shr n
internal infix fun Short.shl(n: Int): Int = this.toInt() shl n
internal infix fun Short.shr(n: Int): Int = this.toInt() shr n

// FIXME: maybe replace with ktor.toByteArray() (workaround for ktor removal)
internal fun String.toASCIIByteArray() = map { it.code.toByte() }.toByteArray()
