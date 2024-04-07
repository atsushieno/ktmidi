package dev.atsushieno.ktmidi.samples.inputsample

import dev.atsushieno.ktmidi.EmptyMidiAccess
import dev.atsushieno.ktmidi.JvmMidiAccess
import dev.atsushieno.ktmidi.RtMidiAccess
import java.io.File
import kotlin.system.exitProcess

actual fun getMidiAccessApi(api: String?, midiTransportProtocol: Int) = when (api) {
    "EMPTY" -> EmptyMidiAccess()
    "JVM" -> JvmMidiAccess()
    else ->
        if (System.getProperty("os.name").contains("Windows")) JvmMidiAccess()
        else RtMidiAccess() // rtmidi-javacpp does not support Windows build nowadays.
}

actual fun exitApplication(code: Int): Unit = exitProcess(code)

actual fun canReadFile(file: String): Boolean = File(file).canRead()
actual fun getFileExtension(file: String): String = File(file).extension
actual fun readFileContents(file: String): List<Byte> = File(file).readBytes().toList()
