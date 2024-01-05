package dev.atsushieno.ktmidi.ci

import kotlin.test.Test

class MidiCIResponderTest {
    @Test
    fun initialState() {
        val mediator = TestCIMediator()
        val responder = mediator.responder
    }
}
