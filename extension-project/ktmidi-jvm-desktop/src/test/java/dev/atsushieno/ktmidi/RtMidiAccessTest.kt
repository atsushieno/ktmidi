package dev.atsushieno.ktmidi

import kotlin.test.Test
import kotlin.test.assertTrue

class RtMidiAccessTest {
    @Test
    fun rtMidiAccessInfo() {
        val rtmidi = RtMidiAccess()
        rtmidi.inputs.forEach { println("${it.id}: ${it.name}") }
        rtmidi.outputs.forEach { println("${it.id}: ${it.name}") }

        rtmidi.inputs.forEach {
            assertTrue(it.id.isNotEmpty(), "input.id")
            assertTrue(it.name!!.isNotEmpty(), "input.name for id: '${it.id}'")
        }
        rtmidi.outputs.forEach {
            assertTrue(it.id.isNotEmpty(), "output.id")
            assertTrue(it.name!!.isNotEmpty(), "output.name for id: '${it.id}'")
        }
    }
}