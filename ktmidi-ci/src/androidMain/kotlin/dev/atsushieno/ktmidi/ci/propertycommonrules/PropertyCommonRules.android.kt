package dev.atsushieno.ktmidi.ci.propertycommonrules

import java.util.zip.Deflater
import java.util.zip.Inflater

actual fun decodeZlibWorkaround(bytes: ByteArray): ByteArray {
    val inflater = Inflater()
    inflater.setInput(bytes)
    val output = ByteArray(bytes.size)
    val size = inflater.inflate(output)
    return output.take(size).toByteArray()
}

actual fun encodeZlibWorkaround(bytes: ByteArray): ByteArray {
    val deflater = Deflater()
    deflater.setInput(bytes)
    val output = ByteArray(bytes.size)
    val size = deflater.deflate(output)
    return output.take(size).toByteArray()
}