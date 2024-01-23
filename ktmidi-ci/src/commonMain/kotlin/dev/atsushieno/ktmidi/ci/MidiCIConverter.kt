package dev.atsushieno.ktmidi.ci

object MidiCIConverter {

    fun encodeStringToASCII(s: String): String {
        return if (s.all { it.code < 0x80 && !it.isISOControl() })
            s
        else
            s.map { if (it.code < 0x80) it.toString() else "\\u${it.code.toString(16)}" }.joinToString("")
    }
    fun decodeASCIIToString(s: String): String =
        s.split("\\u").mapIndexed { index, e ->
            if (index == 0)
                e
            else
                e.substring(0, 4).toInt(16).toChar() + s.substring(4)
        }.joinToString("")
}