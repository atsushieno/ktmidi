package dev.atsushieno.ktmidi.ci

import kotlin.test.Test
import kotlin.test.assertContentEquals

class PropertyCommonConverterTest {
    @Test
    fun encodeToMcoded7() {
        val input = (1 until 21).map { it.toByte() }
        val expected = (
                listOf(0, 1, 9, 17, 0, 0, 0, 0) +
                listOf(0, 2, 3, 4, 5, 6, 7, 8, 0, 10, 11, 12, 13, 14, 15, 16, 0, 18, 19, 20)
                ).map { it.toByte() }
        assertContentEquals(expected, PropertyCommonConverter.encodeToMcoded7(input))
    }

    @Test
    fun decodeMcoded7() {
        val expected = (1 until 21).map { it.toByte() }
        val input = (
                listOf(0, 1, 9, 17, 0, 0, 0, 0) +
                        listOf(0, 2, 3, 4, 5, 6, 7, 8, 0, 10, 11, 12, 13, 14, 15, 16, 0, 18, 19, 20)
                ).map { it.toByte() }
        assertContentEquals(expected, PropertyCommonConverter.decodeMcoded7(input))
    }
}