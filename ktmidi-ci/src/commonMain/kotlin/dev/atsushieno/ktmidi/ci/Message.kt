package dev.atsushieno.ktmidi.ci

class Message {

    // FIXME: maybe we should implement serialize() and deserialize() in each class

    companion object {
        const val MAX_TO_STRING_LENGTH = 1024

        fun List<Byte>.formatBody() =
            this.toByteArray().take(MAX_TO_STRING_LENGTH).toByteArray().decodeToString()

        val Int.muidString: String
            get() = toString(16)
    }

    // Discovery
    data class DiscoveryInquiry(val muid: Int, val device: DeviceDetails, val ciCategorySupported: Byte, val receivableMaxSysExSize: Int, val outputPathId: Byte) {
        override fun toString() = "DiscoveryInquiry(device = ${device}, ciCategorySupported = $ciCategorySupported, receivableMaxSysExSize = $receivableMaxSysExSize, outputPathId = $outputPathId"
    }
    data class DiscoveryReply(val sourceMUID: Int, val destinationMUID: Int, val device: DeviceDetails, val ciCategorySupported: Byte,  val receivableMaxSysExSize: Int, val outputPathId: Byte, val functionBlock: Byte) {
        override fun toString() = "DiscoveryReply(device = ${device}, sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString}, ciCategorySupported = $ciCategorySupported, receivableMaxSysExSize = $receivableMaxSysExSize, outputPathId = $outputPathId, functionBlock = $functionBlock)"
    }

    data class EndpointInquiry(val sourceMUID: Int, val destinationMUID: Int, val status: Byte) {
        override fun toString() = "EndpointInquiry(sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString}, status = ${status})"
    }
    data class EndpointReply(val sourceMUID: Int, val destinationMUID: Int, val status: Byte, val data: List<Byte>) {
        override fun toString() = "EndpointReply(status = ${status}, sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString}, data = (string: ${data.toByteArray().decodeToString()}, bytes: ${data.joinToString { it.toString(16) }}))"
    }

    // Profile Configuration
    data class ProfileInquiry(val address: Byte, val sourceMUID: Int, val destinationMUID: Int) {
        override fun toString() = "ProfileInquiry(address = $address, sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString})"
    }
    data class ProfileReply(val address: Byte, val sourceMUID: Int, val destinationMUID: Int, val enabledProfiles: List<MidiCIProfileId>, val disabledProfiles: List<MidiCIProfileId>) {
        override fun toString() = "ProfileReply(address = $address, sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString}, enabledProfiles=[${enabledProfiles.joinToString { it.toString() }}],  disabledProfiles=[${disabledProfiles.joinToString { it.toString() }}])"
    }
    data class ProfileAdded(val address: Byte, val sourceMUID: Int, val profile: MidiCIProfileId) {
        override fun toString() = "ProfileAdded(address = $address, sourceMUID = ${sourceMUID.muidString}, profile=$profile)"
    }
    data class ProfileRemoved(val address: Byte, val sourceMUID: Int, val profile: MidiCIProfileId) {
        override fun toString() = "ProfileRemoved(address = $address, sourceMUID = ${sourceMUID.muidString}, profile=$profile)"
    }
    data class SetProfileOn(val address: Byte, val sourceMUID: Int, val destinationMUID: Int, val profile: MidiCIProfileId, val numChannelsRequested: Short) {
        override fun toString() = "SetProfileOn(address = $address, sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString}, profile=$profile, numChannelsRequested = $numChannelsRequested)"
    }
    data class SetProfileOff(val address: Byte, val sourceMUID: Int, val destinationMUID: Int, val profile: MidiCIProfileId) {
        override fun toString() = "SetProfileOff(address = $address, sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString}, profile=$profile)"
    }
    data class ProfileEnabled(val address: Byte, val sourceMUID: Int, val profile: MidiCIProfileId, val numChannelsRequested: Short) {
        override fun toString() = "ProfileEnabled(address = $address, sourceMUID = ${sourceMUID.muidString}, profile = $profile, numChannelsRequested = $numChannelsRequested)"
    }
    data class ProfileDisabled(val address: Byte, val sourceMUID: Int, val profile: MidiCIProfileId, val numChannelsRequested: Short) {
        override fun toString() = "ProfileDisabled(address = $address, sourceMUID = ${sourceMUID.muidString}, profile = $profile, numChannelsRequested = $numChannelsRequested)"
    }
    data class ProfileDetailsInquiry(val address: Byte, val sourceMUID: Int, val destinationMUID: Int, val profile: MidiCIProfileId, val target: Byte) {
        override fun toString() = "ProfileDetailsInquiry(address = $address, sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString} profile = $profile, target = $target)"
    }
    data class ProfileDetailsReply(val address: Byte, val sourceMUID: Int, val destinationMUID: Int, val profile: MidiCIProfileId, val target: Byte, val data: List<Byte>) {
        override fun toString() = "ProfileDetailsReply(address = $address, sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString} profile = $profile, target = $target, data = (string: ${data.toByteArray().decodeToString()}, bytes: ${data.joinToString { it.toString(16) }}))"
    }

    // Property Exchange
    data class PropertyGetCapabilities(val destination: Byte, val sourceMUID: Int, val destinationMUID: Int, val maxSimultaneousRequests: Byte) {
        override fun toString() = "PropertyGetCapabilities(destination = ${destination}, sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString}, maxSimultaneousRequests = ${maxSimultaneousRequests})\n"
    }
    data class PropertyGetCapabilitiesReply(val destination: Byte, val sourceMUID: Int, val destinationMUID: Int, val maxSimultaneousRequests: Byte) {
        override fun toString() = "PropertyGetCapabilitiesReply(destination = ${destination}, sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString}, maxSimultaneousRequests = ${maxSimultaneousRequests})"
    }
    data class GetPropertyData(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>) {
        override fun toString() = "GetPropertyData(sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString}, requestId =  ${requestId}, header = ${header.toByteArray().decodeToString()})"
    }
    data class GetPropertyDataReply(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>, val body: List<Byte>) {
        override fun toString() = "GetPropertyDataReply(sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString}, requestId =  ${requestId}, header = ${header.toByteArray().decodeToString()}, body = ${body.formatBody()})"
    }
    data class SetPropertyData(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>, val body: List<Byte>) {
        override fun toString() = "SetPropertyData(sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString}, requestId =  ${requestId}, header = ${header.toByteArray().decodeToString()}, body = ${body.formatBody()})"
    }
    data class SetPropertyDataReply(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>) {
        override fun toString() = "SetPropertyDataReply(sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString}, requestId =  ${requestId}, header = ${header.toByteArray().decodeToString()})"
    }
    data class SubscribeProperty(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>, val body: List<Byte>) {
        override fun toString() = "SubscribeProperty(sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString}, requestId =  ${requestId}, header = ${header.toByteArray().decodeToString()}, body = ${body.formatBody()})"
    }
    data class SubscribePropertyReply(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>, val body: List<Byte>) {
        override fun toString() = "SubscribePropertyReply(sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString}, requestId =  ${requestId}, header = ${header.toByteArray().decodeToString()})"
    }
    data class PropertyNotify(val sourceMUID: Int, val destinationMUID: Int, val requestId: Byte, val header: List<Byte>, val body: List<Byte>) {
        override fun toString() = "PropertyNotify(sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString}, requestId =  ${requestId}, header = ${header.toByteArray().decodeToString()}, body = ${body.formatBody()})"
    }

    // Process Inquiry
    data class ProcessInquiry(val sourceMUID: Int, val destinationMUID: Int) {
        override fun toString() = "ProcessInquiry(sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString})"
    }
    data class ProcessInquiryReply(val sourceMUID: Int, val destinationMUID: Int, val supportedFeatures: Byte) {
        override fun toString() = "ProcessInquiryReply(sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString}, supportedFeatures = $supportedFeatures)"
    }
    data class ProcessMidiMessageReport(val address: Byte, val sourceMUID: Int, val destinationMUID: Int,
                                        val messageDataControl: Byte,
                                        val systemMessages: Byte,
                                        val channelControllerMessages: Byte,
                                        val noteDataMessages: Byte) {
        override fun toString() = "ProcessMidiMessageReport(sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString}, messageDataControl = $messageDataControl, systemMessages = $systemMessages, channelControllerMessages = $channelControllerMessages, noteDataMessages = $noteDataMessages)"
    }
    data class ProcessMidiMessageReportReply(val address: Byte, val sourceMUID: Int, val destinationMUID: Int,
                                        val messageDataControl: Byte,
                                        val systemMessages: Byte,
                                        val channelControllerMessages: Byte,
                                        val noteDataMessages: Byte) {
        override fun toString() = "ProcessMidiMessageReportReply(sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString}, messageDataControl = $messageDataControl, systemMessages = $systemMessages, channelControllerMessages = $channelControllerMessages, noteDataMessages = $noteDataMessages)"
    }
    data class ProcessEndOfMidiMessageReport(val address: Byte, val sourceMUID: Int, val destinationMUID: Int) {
        override fun toString() = "ProcessEndOfMidiMessageReport(address = $address, sourceMUID = ${sourceMUID.muidString}, destinationMUID = ${destinationMUID.muidString})"
    }
}
