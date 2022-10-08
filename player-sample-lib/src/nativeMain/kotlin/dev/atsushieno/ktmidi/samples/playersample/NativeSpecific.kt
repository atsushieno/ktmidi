package dev.atsushieno.ktmidi.samples.playersample

import dev.atsushieno.ktmidi.EmptyMidiAccess
import dev.atsushieno.ktmidi.RtMidiNativeAccess
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import platform.posix.S_IRUSR
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen
import platform.posix.fread
import platform.posix.stat
import kotlin.system.exitProcess

actual fun getMidiAccessApi(api: String?) = when (api) {
    "EMPTY" -> EmptyMidiAccess()
    else -> RtMidiNativeAccess()
}

actual fun exitApplication(code: Int): Unit = exitProcess(code)

actual fun canReadFile(file: String): Boolean = memScoped {
    val statObj = alloc<stat>()
    stat(file, statObj.ptr)
    return (statObj.st_mode.toInt() and S_IRUSR) != 0
}

actual fun getFileExtension(file: String): String =
    file.lastIndexOf('.').let { index -> if (index < 0) "" else file.substring(index + 1) }

actual fun readFileContents(file: String): List<Byte> {
    val fp = fopen(file, "r") ?: throw IllegalArgumentException("Cannot open '$file'")
    try {
        memScoped {
            val statObj = alloc<stat>()
            stat(file, statObj.ptr)
            val bufferSize = statObj.st_size.toInt()
            val buffer = allocArray<ByteVar>(bufferSize)
            fread(buffer, 1, bufferSize.convert(), fp)
            val bytes = buffer.readBytes(bufferSize)
            return bytes.toList()
        }
    } finally {
        fclose(fp)
    }
}