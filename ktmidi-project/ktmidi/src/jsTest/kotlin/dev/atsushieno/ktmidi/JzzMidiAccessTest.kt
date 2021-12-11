package dev.atsushieno.ktmidi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.newCoroutineContext
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
            // FIXME: wait for results
        }.await()
    }
}