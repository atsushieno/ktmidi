package dev.atsushieno.ktmidi.ci

class Message {

    // Property Exchange
    data class PropertyGetCapabilities(val destination: Byte, val sourceMUID: Int, val destinationMUID: Int, val max: Byte)
    data class GetPropertyData(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>)
    data class GetPropertyDataReply(val header: List<Byte>, val body: List<Byte>)
    data class SetPropertyData(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>, val body: List<Byte>)
    data class SetPropertyDataReply(val header: List<Byte>)
    data class SubscribeProperty(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>)
    data class SubscribePropertyReply(val header: List<Byte>, val body: List<Byte>)
}
