package dev.atsushieno.ktmidi

import io.ktor.utils.io.core.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class UmpTest {
    @Test
    fun testToAndFromBytes() {
        val l1 = UmpFactory.midi2NoteOn(1, 2, 0x30, 0, 0xF000, 0)
        val bytes1 = Ump(l1).toPlatformBytes(ByteOrder.LITTLE_ENDIAN)
        val expected1 = arrayOf(0, 0x30, 0x92, 0x41, 0, 0, 0x00, 0xF0).map { b -> b.toByte() }.toByteArray()
        assertContentEquals(expected1, bytes1, "toBytes 1")
        val u1s = Ump.fromBytes(bytes1, 0, bytes1.size).toList()
        assertEquals(1, u1s.size, "fromBytes 1 => size")
        assertEquals(1, u1s[0].group, "fromBytes 1 => group")
        assertEquals(2, u1s[0].channelInGroup, "fromBytes 1 => channel")
        assertEquals(0x90, u1s[0].statusCode, "fromBytes 1 => statusCode")
        assertEquals(0x30, u1s[0].midi2Note, "fromBytes 1 => note")
        assertEquals(0xF000, u1s[0].midi2Velocity16, "fromBytes 1 => velocity16")
        val l2 = UmpFactory.midi2NoteOff(1, 2, 0x30, 0, 0, 0)
        val bytes2 = Ump(l2).toPlatformBytes(ByteOrder.LITTLE_ENDIAN)
        val expected2 = arrayOf(0, 0x30, 0x82, 0x41, 0, 0, 0, 0).map { b -> b.toByte() }.toByteArray()
        assertContentEquals(expected2, bytes2, "toBytes 2")
        // combined
        val u2s = Ump.fromBytes(bytes1 + bytes2, 0, bytes1.size + bytes2.size).toList()
        assertEquals(2, u2s.size, "fromBytes 2 => size")
        assertEquals(1, u2s[1].group, "fromBytes 2 => group")
        assertEquals(2, u2s[1].channelInGroup, "fromBytes 2 => channel")
        assertEquals(0x80, u2s[1].statusCode, "fromBytes 2 => statusCode")
        assertEquals(0x30, u2s[1].midi2Note, "fromBytes 2 => note")
        assertEquals(0, u2s[1].midi2Velocity16, "fromBytes 2 => velocity16")
    }
}