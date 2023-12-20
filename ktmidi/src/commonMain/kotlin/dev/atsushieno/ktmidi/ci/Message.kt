package dev.atsushieno.ktmidi.ci

class Message {

    // FIXME: maybe we should implement serialize() and deserialize() in each class

    // Discovery
    data class DiscoveryInquiry(val muid: Int, val device: DeviceDetails, val ciCategorySupported: Byte, val receivableMaxSysExSize: Int, val outputPathId: Byte)
    data class DiscoveryReply(val sourceMUID: Int, val destinationMUID: Int, val device: DeviceDetails, val ciCategorySupported: Byte,  val receivableMaxSysExSize: Int, val outputPathId: Byte)

    data class EndpointInquiry(val sourceMUID: Int, val destinationMUID: Int, val status: Byte)
    data class EndpointReply(val sourceMUID: Int, val destinationMUID: Int, val status: Byte, val data: List<Byte>)

    // Property Exchange
    data class PropertyGetCapabilities(val destination: Byte, val sourceMUID: Int, val destinationMUID: Int, val max: Byte)
    data class PropertyGetCapabilitiesReply(val destination: Byte, val sourceMUID: Int, val destinationMUID: Int, val max: Byte)
    data class GetPropertyData(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>)
    data class GetPropertyDataReply(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>, val body: List<Byte>)
    data class SetPropertyData(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>, val body: List<Byte>)
    data class SetPropertyDataReply(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>)
    data class SubscribeProperty(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>)
    data class SubscribePropertyReply(val header: List<Byte>, val body: List<Byte>)
}
