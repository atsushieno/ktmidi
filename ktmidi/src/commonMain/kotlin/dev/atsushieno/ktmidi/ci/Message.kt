package dev.atsushieno.ktmidi.ci

class Message {

    // FIXME: maybe we should implement serialize() and deserialize() in each class

    // Discovery
    data class DiscoveryInquiry(val muid: Int, val device: DeviceDetails, val ciCategorySupported: Byte, val receivableMaxSysExSize: Int, val outputPathId: Byte) {
        override fun toString() = "DiscoveryInquiry(device = ${device}, ciCategorySupported = $ciCategorySupported, receivableMaxSysExSize = $receivableMaxSysExSize, outputPathId = $outputPathId"
    }
    data class DiscoveryReply(val sourceMUID: Int, val destinationMUID: Int, val device: DeviceDetails, val ciCategorySupported: Byte,  val receivableMaxSysExSize: Int, val outputPathId: Byte, val functionBlock: Byte) {
        override fun toString() = "DiscoveryReply(device = ${device}, sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, ciCategorySupported = $ciCategorySupported, receivableMaxSysExSize = $receivableMaxSysExSize, outputPathId = $outputPathId, functionBlock = $functionBlock)"
    }

    data class EndpointInquiry(val sourceMUID: Int, val destinationMUID: Int, val status: Byte) {
        override fun toString() = "EndpointInquiry(sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, status = ${status})"
    }
    data class EndpointReply(val sourceMUID: Int, val destinationMUID: Int, val status: Byte, val data: List<Byte>) {
        override fun toString() = "EndpointReply(status = ${status}, sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, data = (string: ${data.toByteArray().decodeToString()}, bytes: ${data.joinToString { it.toString(16) }}))"
    }

    // Profile Configuration
    data class ProfileInquiry(val source: Byte, val sourceMUID: Int, val destinationMUID: Int) {
        override fun toString() = "ProfileInquiry(source = $source, sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)})"
    }
    data class ProfileReply(val source: Byte, val sourceMUID: Int, val destinationMUID: Int, val profiles: List<Pair<MidiCIProfileId,Boolean>>) {
        override fun toString() = "ProfileReply(source = $source, sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, enabledProfiles=[${profiles.filter { it.second }.joinToString { it.toString() }}],  disabledProfiles=[${profiles.filter { !it.second }.joinToString { it.toString() }}])"
    }
    data class ProfileAdded(val deviceId: Byte, val sourceMUID: Int, val profile: MidiCIProfileId) {
        override fun toString() = "ProfileAdded(deviceId = $deviceId, sourceMUID = ${sourceMUID.toString(16)}, profile=$profile)"
    }
    data class ProfileRemoved(val deviceId: Byte, val sourceMUID: Int, val profile: MidiCIProfileId) {
        override fun toString() = "ProfileRemoved(deviceId = $deviceId, sourceMUID = ${sourceMUID.toString(16)}, profile=$profile)"
    }

    // Property Exchange
    data class PropertyGetCapabilities(val destination: Byte, val sourceMUID: Int, val destinationMUID: Int, val max: Byte)
    data class PropertyGetCapabilitiesReply(val destination: Byte, val sourceMUID: Int, val destinationMUID: Int, val max: Byte)
    data class GetPropertyData(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>) {
        override fun toString() = "GetPropertyData(sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, requestId =  ${requestId}, header = ${header.toByteArray().decodeToString()})"
    }
    data class GetPropertyDataReply(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>, val body: List<Byte>) {
        override fun toString() = "GetPropertyDataReply(sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, requestId =  ${requestId}, header = ${header.toByteArray().decodeToString()}, body = ${body.toByteArray().decodeToString()})"
    }
    data class SetPropertyData(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>, val body: List<Byte>) {
        override fun toString() = "SetPropertyData(sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, requestId =  ${requestId}, header = ${header.toByteArray().decodeToString()}, body = ${body.toByteArray().decodeToString()})"
    }
    data class SetPropertyDataReply(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>) {
        override fun toString() = "SetPropertyDataReply(sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, requestId =  ${requestId}, header = ${header.toByteArray().decodeToString()})"
    }
    data class SubscribeProperty(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>) {
        override fun toString() = "SubscribeProperty(sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, requestId =  ${requestId}, header = ${header.toByteArray().decodeToString()})"
    }
    data class SubscribePropertyReply(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>, val body: List<Byte>) {
        override fun toString() = "SubscribePropertyReply(sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, requestId =  ${requestId}, header = ${header.toByteArray().decodeToString()})"
    }
}
