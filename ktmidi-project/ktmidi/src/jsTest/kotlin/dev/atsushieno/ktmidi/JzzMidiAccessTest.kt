package dev.atsushieno.ktmidi

import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class JzzMidiAccessTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun create() = runTest {
        async {
            val access = JzzMidiAccess.create(true)
            assertTrue(access.inputs.toList().size >= 0, "inputs")
            assertTrue(access.outputs.toList().size >= 0, "outputs")
        }.await()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun output() = runTest {
        async {
            val access = JzzMidiAccess.create(true)
            val outPort = access.outputs.firstOrNull() ?: return@async
            val output = access.openOutputAsync(outPort.id)
            output.send(byteArrayOf(0x90.toByte(), 0x30, 0x80.toByte()), 0, 3, 0)
            output.close()
        }.await()
    }
}