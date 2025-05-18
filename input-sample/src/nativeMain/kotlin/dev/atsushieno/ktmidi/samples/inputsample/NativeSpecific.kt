package dev.atsushieno.ktmidi.samples.inputsample

import dev.atsushieno.ktmidi.EmptyMidiAccess
import dev.atsushieno.ktmidi.MidiAccess
import kotlin.system.exitProcess

actual fun getMidiAccessApi(api: String?, midiTransportProtocol: Int): MidiAccess = when (api) {
    "EMPTY" -> EmptyMidiAccess()
    else -> getNativeMidiAccessApi(midiTransportProtocol)
}

expect fun getNativeMidiAccessApi(midiTransportProtocol: Int): MidiAccess

actual fun exitApplication(code: Int): Unit = exitProcess(code)

actual fun readLine() { readln() }
