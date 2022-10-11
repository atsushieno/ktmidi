package dev.atsushieno.ktmidi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertTrue

class JzzMidiAccessTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun create() = runTest {
        val access = JzzMidiAccess.create(true)
        assertTrue(access.inputs.toList().size >= 0, "inputs")
        assertTrue(access.outputs.toList().size >= 0, "outputs")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun output() = runTest(dispatchTimeoutMs = 1000) {
        async {
            val access = JzzMidiAccess.create(true)
            val outPort = access.outputs.firstOrNull() ?: return@async
            val output = access.openOutput(outPort.id)
            output.send(byteArrayOf(0x90.toByte(), 0x30, 0x80.toByte()), 0, 3, 0)
            output.close()
        }.await()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun input() = runTest(dispatchTimeoutMs = 1000) {
        async {
            val access = JzzMidiAccess.create(true)
            val inPort = access.inputs.firstOrNull() ?: return@async
            val input = withContext(Dispatchers.Default) { access.openInput(inPort.id) }
            input.close()
        }.await()
    }
}