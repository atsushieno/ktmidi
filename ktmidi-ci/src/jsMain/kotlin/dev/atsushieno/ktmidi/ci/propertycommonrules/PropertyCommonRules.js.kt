package dev.atsushieno.ktmidi.ci.propertycommonrules

actual fun decodeZlibWorkaround(bytes: ByteArray): ByteArray {
    TODO("FIXME: enable implementation once ktor-utils 3.0.0 is released")
    //DeflateEncoder.decode(ByteReadChannel(bytes)).toByteArray()
}

actual fun encodeZlibWorkaround(bytes: ByteArray): ByteArray {
    TODO("FIXME: enable implementation once ktor-utils 3.0.0 is released")
    //DeflateEncoder.encode(ByteReadChannel(bytes)).toByteArray()
}