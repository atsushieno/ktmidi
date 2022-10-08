package dev.atsushieno.ktmidi.samples.playersample

import dev.atsushieno.ktmidi.EmptyMidiAccess
import dev.atsushieno.ktmidi.JvmMidiAccess
import dev.atsushieno.ktmidi.RtMidiAccess
import java.io.File
import kotlin.system.exitProcess

actual fun getMidiAccessApi(api: String?) = when (api) {
    "EMPTY" -> EmptyMidiAccess()
    "JVM" -> JvmMidiAccess()
    else -> RtMidiAccess()
}

actual fun exitApplication(code: Int): Unit = exitProcess(code)

actual fun canReadFile(file: String): Boolean = File(file).canRead()
actual fun getFileExtension(file: String): String = File(file).extension
actual fun readFileContents(file: String): List<Byte> = File(file).readBytes().toList()
