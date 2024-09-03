package dev.atsushieno.ktmidi.samples.inputsample

import dev.atsushieno.ktmidi.*
import kotlin.system.exitProcess

actual fun getMidiAccessApi(api: String?, midiTransportProtocol: Int) = when (api) {
    "EMPTY" -> EmptyMidiAccess()
    "JVM" -> JvmMidiAccess()
    "ALSA" -> AlsaMidiAccess()
    "RtMidi" ->
        if (System.getProperty("os.name").contains("Windows")) JvmMidiAccess()
        else RtMidiAccess() // rtmidi-javacpp does not support Windows build nowadays.
    else -> LibreMidiAccess.create(midiTransportProtocol) // rtmidi-javacpp does not support Windows build nowadays.
}

actual fun exitApplication(code: Int): Unit = exitProcess(code)

actual fun runLoop(body: ()->Unit) = body()
