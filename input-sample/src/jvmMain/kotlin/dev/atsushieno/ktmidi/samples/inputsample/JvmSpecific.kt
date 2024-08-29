package dev.atsushieno.ktmidi.samples.inputsample

import dev.atsushieno.ktmidi.AlsaMidiAccess
import dev.atsushieno.ktmidi.EmptyMidiAccess
import dev.atsushieno.ktmidi.JvmMidiAccess
import dev.atsushieno.ktmidi.RtMidiAccess
import kotlin.system.exitProcess

actual fun getMidiAccessApi(api: String?, midiTransportProtocol: Int) = when (api) {
    "EMPTY" -> EmptyMidiAccess()
    "JVM" -> JvmMidiAccess()
    else -> {
        val os = System.getProperty("os.name")
        when {
            os.contains("Windows") -> JvmMidiAccess()
            os == "Linux" -> AlsaMidiAccess()
            else -> RtMidiAccess() // rtmidi-javacpp does not support Windows build nowadays.
        }
    }
}

actual fun exitApplication(code: Int): Unit = exitProcess(code)

actual fun runLoop(body: ()->Unit) = body()
