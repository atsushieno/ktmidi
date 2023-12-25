package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.MidiCIProtocolType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MidiCIResponderTest {
    @Test
    fun initialState() {
        val mediator = TestCIMediator()
        val responder = mediator.responder
    }
}
