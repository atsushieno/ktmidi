package dev.atsushieno.ktmidi.ci.propertycommonrules

import java.util.zip.Deflater
import java.util.zip.Inflater

actual fun decodeZlibWorkaround(bytes: ByteArray): ByteArray {
    val inflater = Inflater(true)
    inflater.setInput(bytes)
    val output = ByteArray(bytes.size)
    val size = inflater.inflate(output)
    inflater.end()
    return output.take(size).toByteArray()
}

actual fun encodeZlibWorkaround(bytes: ByteArray): ByteArray {
    val deflater = Deflater(1, true)
    deflater.setInput(bytes)
    deflater.finish()
    val output = ByteArray(bytes.size)
    val size = deflater.deflate(output)
    deflater.end()
    return output.take(size).toByteArray()
}