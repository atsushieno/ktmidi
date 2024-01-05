package dev.atsushieno.ktmidi.ci

internal infix fun Byte.shl(n: Int): Int = this.toInt() shl n
internal infix fun Byte.shr(n: Int): Int = this.toInt() shr n
internal infix fun Short.shl(n: Int): Int = this.toInt() shl n
internal infix fun Short.shr(n: Int): Int = this.toInt() shr n
