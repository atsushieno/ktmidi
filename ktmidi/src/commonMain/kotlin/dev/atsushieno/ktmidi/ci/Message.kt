package dev.atsushieno.ktmidi.ci

class Message {

    // FIXME: maybe we should implement serialize() and deserialize() in each class

    // Discovery
    data class DiscoveryInquiry(val muid: Int, val manufacturerId: Int, val familyId: Short, val modelId: Short,
                                val versionId: Int, val ciCategorySupported: Byte, val receivableMaxSysExSize: Int, val outputPathId: Byte)
    data class DiscoveryReply(val device: DeviceDetails, val sourceMUID: Int, val destinationMUID: Int)

    // Property Exchange
    data class PropertyGetCapabilities(val destination: Byte, val sourceMUID: Int, val destinationMUID: Int, val max: Byte)
    data class PropertyGetCapabilitiesReply(val destination: Byte, val sourceMUID: Int, val destinationMUID: Int, val max: Byte)
    data class GetPropertyData(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>)
    data class GetPropertyDataReply(val header: List<Byte>, val body: List<Byte>)
    data class SetPropertyData(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>, val body: List<Byte>)
    data class SetPropertyDataReply(val header: List<Byte>)
    data class SubscribeProperty(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>)
    data class SubscribePropertyReply(val header: List<Byte>, val body: List<Byte>)
}
