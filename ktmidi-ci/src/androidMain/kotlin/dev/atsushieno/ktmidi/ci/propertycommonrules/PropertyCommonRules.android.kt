package dev.atsushieno.ktmidi.ci.propertycommonrules

import java.util.zip.Deflater
import java.util.zip.Inflater

actual fun decodeZlibWorkaround(bytes: ByteArray): ByteArray {
    val inflater = Inflater(false)
    inflater.setInput(bytes)
    val output = ByteArray(bytes.size) { 0 }
    val size = inflater.inflate(output)
    inflater.end()
    return output.take(size).toByteArray()
}

actual fun encodeZlibWorkaround(bytes: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.FILTERED, false)
    deflater.setInput(bytes)
    deflater.finish()
    val output = ByteArray(bytes.size + 1) // extra space for wrapper
    val size = deflater.deflate(output)
    deflater.end()
    return output.take(size).toByteArray()
}