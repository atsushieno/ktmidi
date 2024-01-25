package dev.atsushieno.ktmidi.ci

object MidiCIConverter {

    // Implements the standard string conversion specified as per MIDI-CI specification, section 5.10.4
    fun encodeStringToASCII(s: String): String {
        return if (s.all { it.code < 0x80 && it != '\\' })
            s
        else
            s.map { if (it.code < 0x80 && it != '\\') it.toString() else "\\u${it.code.toString(16).padStart(4, '0')}" }.joinToString("")
    }
    fun decodeASCIIToString(s: String): String =
        s.split("\\u").mapIndexed { index, e ->
            if (index == 0)
                e
            else
                e.substring(0, 4).toInt(16).toChar() + e.substring(4)
        }.joinToString("")
}