package dev.atsushieno.ktmidi.samples.inputsample

import dev.atsushieno.ktmidi.EmptyMidiAccess
import dev.atsushieno.ktmidi.JvmMidiAccess
import dev.atsushieno.ktmidi.RtMidiAccess
import kotlin.system.exitProcess

actual fun getMidiAccessApi(api: String?, midiTransportProtocol: Int) = when (api) {
    "EMPTY" -> EmptyMidiAccess()
    "JVM" -> JvmMidiAccess()
    else ->
        if (System.getProperty("os.name").contains("Windows")) JvmMidiAccess()
        else RtMidiAccess() // rtmidi-javacpp does not support Windows build nowadays.
}

actual fun exitApplication(code: Int): Unit = exitProcess(code)

actual fun runLoop(body: ()->Unit) = body()
