package dev.atsushieno.ktmidi.ci

abstract class Message(protected val common: Common) {

    val address: Byte
        get() = common.address
    val sourceMUID: Int
        get() = common.sourceMUID
    val destinationMUID: Int
        get() = common.destinationMUID

    // FIXME: maybe we should implement serialize() and deserialize() in each class

    companion object {
        private const val MAX_TO_STRING_LENGTH = 1024

        val List<Byte>.headerString: String
            get() = toByteArray().decodeToString()
        val List<Byte>.bodyString: String
            get() = toByteArray().take(MAX_TO_STRING_LENGTH).toByteArray().decodeToString()
        val List<Byte>.dataString: String
            get() = "(string: ${this.toByteArray().decodeToString()}, bytes: ${this.joinToString { it.toString(16) }})"
        val Int.muidString: String
            get() = toString(16)
    }

    data class Common(val sourceMUID: Int, val destinationMUID: Int = MidiCIConstants.BROADCAST_MUID_32, val address: Byte = MidiCIConstants.ADDRESS_FUNCTION_BLOCK) {
        override fun toString() = "{address=$address, sourceMUID=${sourceMUID.muidString}, destinationMUID=${destinationMUID.muidString}}"
    }

    // Discovery
    class DiscoveryInquiry(muid: Int, val device: DeviceDetails, val ciCategorySupported: Byte, val receivableMaxSysExSize: Int, val outputPathId: Byte)
        : Message(Common(sourceMUID = muid)) {
        override fun toString() = "DiscoveryInquiry($common, device = ${device}, ciCategorySupported = $ciCategorySupported, receivableMaxSysExSize = $receivableMaxSysExSize, outputPathId = $outputPathId"
    }
    class DiscoveryReply(sourceMUID: Int, destinationMUID: Int, val device: DeviceDetails, val ciCategorySupported: Byte,  val receivableMaxSysExSize: Int, val outputPathId: Byte, val functionBlock: Byte)
        : Message(Common(sourceMUID, destinationMUID)){
        override fun toString() = "DiscoveryReply($common, device=${device}, ciCategorySupported=$ciCategorySupported, receivableMaxSysExSize=$receivableMaxSysExSize, outputPathId=$outputPathId, functionBlock=$functionBlock)"
    }

    class EndpointInquiry(sourceMUID: Int, destinationMUID: Int, val status: Byte)
        : Message(Common(sourceMUID, destinationMUID)) {
        override fun toString() = "EndpointInquiry($common, status=${status})"
    }
    class EndpointReply(sourceMUID: Int, destinationMUID: Int, val status: Byte, val data: List<Byte>)
        : Message(Common(sourceMUID, destinationMUID)) {
        override fun toString() = "EndpointReply($common, status=${status}, data = ${data.dataString})"
    }

    // Profile Configuration
    class ProfileInquiry(address: Byte, sourceMUID: Int, destinationMUID: Int)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override fun toString() = "ProfileInquiry($common)"
    }
    class ProfileReply(address: Byte, sourceMUID: Int, destinationMUID: Int, val enabledProfiles: List<MidiCIProfileId>, val disabledProfiles: List<MidiCIProfileId>)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override fun toString() = "ProfileReply($common, enabledProfiles=[${enabledProfiles.joinToString { it.toString() }}],  disabledProfiles=[${disabledProfiles.joinToString { it.toString() }}])"
    }
    class ProfileAdded(address: Byte, sourceMUID: Int, val profile: MidiCIProfileId)
        : Message(Common(sourceMUID, address = address)) {
        override fun toString() = "ProfileAdded($common, profile=$profile)"
    }
    class ProfileRemoved(address: Byte, sourceMUID: Int, val profile: MidiCIProfileId)
        : Message(Common(sourceMUID, address = address)) {
        override fun toString() = "ProfileRemoved($common, profile=$profile)"
    }
    class SetProfileOn(address: Byte, sourceMUID: Int, destinationMUID: Int, val profile: MidiCIProfileId, val numChannelsRequested: Short)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override fun toString() = "SetProfileOn($common, profile=$profile, numChannelsRequested=$numChannelsRequested)"
    }
    class SetProfileOff(address: Byte, sourceMUID: Int, destinationMUID: Int, val profile: MidiCIProfileId)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override fun toString() = "SetProfileOff($common, profile=$profile)"
    }
    class ProfileEnabled(address: Byte, sourceMUID: Int, val profile: MidiCIProfileId, val numChannelsRequested: Short)
        : Message(Common(sourceMUID, address = address)) {
            override fun toString() = "ProfileEnabled($common, profile=$profile, numChannelsRequested=$numChannelsRequested)"
    }
    class ProfileDisabled(address: Byte, sourceMUID: Int, val profile: MidiCIProfileId, val numChannelsRequested: Short)
        : Message(Common(sourceMUID, address = address)) {
        override fun toString() = "ProfileDisabled($common, profile=$profile, numChannelsRequested=$numChannelsRequested)"
    }
    class ProfileDetailsInquiry(address: Byte, sourceMUID: Int, destinationMUID: Int, val profile: MidiCIProfileId, val target: Byte)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override fun toString() = "ProfileDetailsInquiry($common, profile=$profile, target=$target)"
    }
    class ProfileDetailsReply(address: Byte, sourceMUID: Int, destinationMUID: Int, val profile: MidiCIProfileId, val target: Byte, val data: List<Byte>)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override fun toString() = "ProfileDetailsReply($common, profile=$profile, target=$target, data=${data.dataString}))"
    }

    // Property Exchange
    class PropertyGetCapabilities(address: Byte, sourceMUID: Int, destinationMUID: Int, val maxSimultaneousRequests: Byte)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override fun toString() = "PropertyGetCapabilities($common, maxSimultaneousRequests=${maxSimultaneousRequests})\n"
    }
    class PropertyGetCapabilitiesReply(address: Byte, sourceMUID: Int, destinationMUID: Int, val maxSimultaneousRequests: Byte)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override fun toString() = "PropertyGetCapabilitiesReply($common, maxSimultaneousRequests=${maxSimultaneousRequests})"
    }
    class GetPropertyData(sourceMUID: Int, destinationMUID: Int, val requestId: Byte, val header: List<Byte>)
        : Message(Common(sourceMUID, destinationMUID)) {
        override fun toString() = "GetPropertyData($common, requestId=${requestId}, header=${header.headerString})"
    }
    class GetPropertyDataReply(sourceMUID: Int, destinationMUID: Int, val requestId: Byte, val header: List<Byte>, val body: List<Byte>)
        : Message(Common(sourceMUID, destinationMUID)) {
        override fun toString() = "GetPropertyDataReply($common, requestId=${requestId}, header=${header.headerString}, body=${body.bodyString})"
    }
    class SetPropertyData(sourceMUID: Int, destinationMUID: Int, val requestId: Byte, val header: List<Byte>, val body: List<Byte>)
        : Message(Common(sourceMUID, destinationMUID)) {
        override fun toString() = "SetPropertyData($common, requestId=${requestId}, header=${header.headerString}, body=${body.bodyString})"
    }
    class SetPropertyDataReply(sourceMUID: Int, destinationMUID: Int, val requestId: Byte, val header: List<Byte>)
        : Message(Common(sourceMUID, destinationMUID)) {
        override fun toString() = "SetPropertyDataReply($common, requestId=${requestId}, header=${header.headerString})"
    }
    class SubscribeProperty(sourceMUID: Int, destinationMUID: Int, val requestId: Byte, val header: List<Byte>, val body: List<Byte>)
        : Message(Common(sourceMUID, destinationMUID)) {
        override fun toString() = "SubscribeProperty($common, requestId=${requestId}, header=${header.headerString}, body=${body.bodyString})"
    }
    class SubscribePropertyReply(sourceMUID: Int, destinationMUID: Int, val requestId: Byte, val header: List<Byte>, val body: List<Byte>)
        : Message(Common(sourceMUID, destinationMUID)) {
        override fun toString() = "SubscribePropertyReply($common, requestId=${requestId}, header=${header.headerString}, body=${body.bodyString})"
    }
    class PropertyNotify(sourceMUID: Int, destinationMUID: Int, val requestId: Byte, val header: List<Byte>, val body: List<Byte>)
        : Message(Common(sourceMUID, destinationMUID)) {
        override fun toString() = "PropertyNotify($common, requestId=${requestId}, header=${header.headerString}, body=${body.bodyString})"
    }

    // Process Inquiry
    class ProcessInquiry(sourceMUID: Int, destinationMUID: Int)
        : Message(Common(sourceMUID, destinationMUID)) {
        override fun toString() = "ProcessInquiry($common)"
    }
    class ProcessInquiryReply(sourceMUID: Int, destinationMUID: Int, val supportedFeatures: Byte)
        : Message(Common(sourceMUID, destinationMUID)) {
        override fun toString() = "ProcessInquiryReply($common, supportedFeatures=$supportedFeatures)"
    }
    class ProcessMidiMessageReport(address: Byte, sourceMUID: Int, destinationMUID: Int,
                                    val messageDataControl: Byte,
                                    val systemMessages: Byte,
                                    val channelControllerMessages: Byte,
                                    val noteDataMessages: Byte)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override fun toString() = "ProcessMidiMessageReport($common, messageDataControl=$messageDataControl, systemMessages=$systemMessages, channelControllerMessages=$channelControllerMessages, noteDataMessages=$noteDataMessages)"
    }
    class ProcessMidiMessageReportReply(address: Byte, sourceMUID: Int, destinationMUID: Int,
                                        val messageDataControl: Byte,
                                        val systemMessages: Byte,
                                        val channelControllerMessages: Byte,
                                        val noteDataMessages: Byte)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override fun toString() = "ProcessMidiMessageReportReply($common, messageDataControl = $messageDataControl, systemMessages = $systemMessages, channelControllerMessages = $channelControllerMessages, noteDataMessages = $noteDataMessages)"
    }
    class ProcessEndOfMidiMessageReport(address: Byte, sourceMUID: Int, destinationMUID: Int)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override fun toString() = "ProcessEndOfMidiMessageReport($common)"
    }
}
