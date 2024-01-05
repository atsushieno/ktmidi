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
    data class ProfileInquiry(val address: Byte, val sourceMUID: Int, val destinationMUID: Int) {
        override fun toString() = "ProfileInquiry(address = $address, sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)})"
    }
    data class ProfileReply(val address: Byte, val sourceMUID: Int, val destinationMUID: Int, val enabledProfiles: List<MidiCIProfileId>, val disabledProfiles: List<MidiCIProfileId>) {
        override fun toString() = "ProfileReply(address = $address, sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, enabledProfiles=[${enabledProfiles.joinToString { it.toString() }}],  disabledProfiles=[${disabledProfiles.joinToString { it.toString() }}])"
    }
    data class ProfileAdded(val address: Byte, val sourceMUID: Int, val profile: MidiCIProfileId) {
        override fun toString() = "ProfileAdded(address = $address, sourceMUID = ${sourceMUID.toString(16)}, profile=$profile)"
    }
    data class ProfileRemoved(val address: Byte, val sourceMUID: Int, val profile: MidiCIProfileId) {
        override fun toString() = "ProfileRemoved(address = $address, sourceMUID = ${sourceMUID.toString(16)}, profile=$profile)"
    }
    data class SetProfileOn(val address: Byte, val sourceMUID: Int, val destinationMUID: Int, val profile: MidiCIProfileId, val numChannelsRequested: Short) {
        override fun toString() = "SetProfileOn(address = $address, sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, profile=$profile, numChannelsRequested = $numChannelsRequested)"
    }
    data class SetProfileOff(val address: Byte, val sourceMUID: Int, val destinationMUID: Int, val profile: MidiCIProfileId) {
        override fun toString() = "SetProfileOff(address = $address, sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, profile=$profile)"
    }
    data class ProfileEnabled(val address: Byte, val sourceMUID: Int, val profile: MidiCIProfileId, val numChannelsRequested: Short) {
        override fun toString() = "ProfileEnabled(address = $address, sourceMUID = ${sourceMUID.toString(16)}, profile=$profile, numChannelsRequested = $numChannelsRequested)"
    }
    data class ProfileDisabled(val address: Byte, val sourceMUID: Int, val profile: MidiCIProfileId, val numChannelsRequested: Short) {
        override fun toString() = "ProfileDisabled(address = $address, sourceMUID = ${sourceMUID.toString(16)}, profile=$profile, numChannelsRequested = $numChannelsRequested)"
    }

    // Property Exchange
    data class PropertyGetCapabilities(val destination: Byte, val sourceMUID: Int, val destinationMUID: Int, val maxSimultaneousRequests: Byte) {
        override fun toString() = "PropertyGetCapabilities(destination = ${destination}, sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, maxSimultaneousRequests = ${maxSimultaneousRequests})\n"
    }
    data class PropertyGetCapabilitiesReply(val destination: Byte, val sourceMUID: Int, val destinationMUID: Int, val maxSimultaneousRequests: Byte) {
        override fun toString() = "PropertyGetCapabilitiesReply(destination = ${destination}, sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, maxSimultaneousRequests = ${maxSimultaneousRequests})"
    }
    data class GetPropertyData(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>) {
        override fun toString() = "GetPropertyData(sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, requestId =  ${requestId}, header = ${header.toByteArray().decodeToString()})"
    }
    data class GetPropertyDataReply(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>, val numTotalChunks: Short, val chunkIndex: Short, val body: List<Byte>) {
        override fun toString() = "GetPropertyDataReply(sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, requestId =  ${requestId}, header = ${header.toByteArray().decodeToString()}, numTotalChunks = $numTotalChunks, chunkIndex =$chunkIndex, body = ${body.toByteArray().decodeToString()})"
    }
    data class SetPropertyData(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>, val numTotalChunks: Short, val chunkIndex: Short, val body: List<Byte>) {
        override fun toString() = "SetPropertyData(sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, requestId =  ${requestId}, header = ${header.toByteArray().decodeToString()}, numTotalChunks = $numTotalChunks, chunkIndex =$chunkIndex, body = ${body.toByteArray().decodeToString()})"
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
