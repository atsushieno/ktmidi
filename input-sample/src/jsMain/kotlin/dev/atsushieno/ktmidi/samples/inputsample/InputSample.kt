package dev.atsushieno.ktmidi.samples.inputsample

import dev.atsushieno.ktmidi.JzzMidiAccess
import dev.atsushieno.ktmidi.MidiAccess

actual fun getMidiAccessApi(api: String?, midiTransportProtocol: Int): MidiAccess =
    JzzMidiAccess.create(true)

actual fun exitApplication(code: Int) {
}

actual fun runLoop(body: () -> Unit) {
    body()
}

actual fun readLine() {
}

