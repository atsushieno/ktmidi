package dev.atsushieno.ktmidi.ci

import kotlin.test.Test
import kotlin.test.assertEquals

class MidiCIConverterTest {
    @Test
    fun encodeStringToASCII() {
        val expected = "test\\u005cu#\$%&'"
        val actual = MidiCIConverter.encodeStringToASCII("test\\u#$%&'").lowercase()
        assertEquals(expected, actual)
    }

    @Test
    fun decodeStringToASCII() {
        val expected = "test\\u#\$%&'"
        val actual = MidiCIConverter.decodeASCIIToString("test\\u005cu#\$%&'")
        assertEquals(expected, actual)
    }
}