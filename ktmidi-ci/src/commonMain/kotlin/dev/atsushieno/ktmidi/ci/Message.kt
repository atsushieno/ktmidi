package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.Message.Companion.dataString

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
        val Byte.addressString: String
            get() = when (this.toInt()) {
                0x7E -> "Group"
                0x7F -> "FunctionBlock"
                else -> "Ch. $this"
            }
    }

    data class Common(val sourceMUID: Int, val destinationMUID: Int = MidiCIConstants.BROADCAST_MUID_32, val address: Byte = MidiCIConstants.ADDRESS_FUNCTION_BLOCK) {
        override fun toString() = "{address=$address, sourceMUID=${sourceMUID.muidString}, destinationMUID=${destinationMUID.muidString}}"
    }

    abstract val label: String
    abstract val bodyString: String
    override fun toString() = "$label($common, $bodyString)"

    // Discovery
    class DiscoveryInquiry(muid: Int, val device: DeviceDetails, val ciCategorySupported: Byte, val receivableMaxSysExSize: Int, val outputPathId: Byte)
        : Message(Common(sourceMUID = muid)) {
        override val label = "DiscoveryInquiry"
        override val bodyString = "device=${device}, ciCategorySupported=$ciCategorySupported, receivableMaxSysExSize=$receivableMaxSysExSize, outputPathId=$outputPathId"
    }
    class DiscoveryReply(sourceMUID: Int, destinationMUID: Int, val device: DeviceDetails, val ciCategorySupported: Byte,  val receivableMaxSysExSize: Int, val outputPathId: Byte, val functionBlock: Byte)
        : Message(Common(sourceMUID, destinationMUID)){
        override val label = "DiscoveryReply"
        override val bodyString = "ciCategorySupported=$ciCategorySupported, receivableMaxSysExSize=$receivableMaxSysExSize, outputPathId=$outputPathId, functionBlock=$functionBlock"
    }

    class EndpointInquiry(sourceMUID: Int, destinationMUID: Int, val status: Byte)
        : Message(Common(sourceMUID, destinationMUID)) {
        override val label = "EndpointInquiry"
        override val bodyString = "status=${status})"
    }
    class EndpointReply(sourceMUID: Int, destinationMUID: Int, val status: Byte, val data: List<Byte>)
        : Message(Common(sourceMUID, destinationMUID)) {
        override val label = "EndpointReply"
        override val bodyString = "status=${status}, data = ${data.dataString})"
    }

    class InvalidateMUID(sourceMUID: Int, targetMUID: Int)
        : Message(Common(sourceMUID, MidiCIConstants.BROADCAST_MUID_32)) {
        override val label = "InvalidateMUID"
        override val bodyString = "targetMUID=${targetMUID.muidString})"
    }

    class Nak(address: Byte, sourceMUID: Int, destinationMUID: Int, statusCode: Byte, statusData: Byte, message: String)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override val label = "Nak"
        override val bodyString = "statusCode=$statusCode, statusData=$statusData, message=$message"
    }

    // Profile Configuration
    class ProfileInquiry(address: Byte, sourceMUID: Int, destinationMUID: Int)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override val label = "ProfileInquiry"
        override val bodyString = ""
    }
    class ProfileReply(address: Byte, sourceMUID: Int, destinationMUID: Int, val enabledProfiles: List<MidiCIProfileId>, val disabledProfiles: List<MidiCIProfileId>)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override val label = "ProfileReply"
        override val bodyString = "enabledProfiles=[${enabledProfiles.joinToString { it.toString() }}],  disabledProfiles=[${disabledProfiles.joinToString { it.toString() }}])"
    }
    class ProfileAdded(address: Byte, sourceMUID: Int, val profile: MidiCIProfileId)
        : Message(Common(sourceMUID, address = address)) {
        override val label = "ProfileAdded"
        override val bodyString = "profile=$profile)"
    }
    class ProfileRemoved(address: Byte, sourceMUID: Int, val profile: MidiCIProfileId)
        : Message(Common(sourceMUID, address = address)) {
        override val label = "ProfileRemoved"
        override val bodyString = "profile=$profile)"
    }
    class SetProfileOn(address: Byte, sourceMUID: Int, destinationMUID: Int, val profile: MidiCIProfileId, val numChannelsRequested: Short)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override val label = "SetProfileOn"
        override val bodyString = "profile=$profile, numChannelsRequested=$numChannelsRequested)"
    }
    class SetProfileOff(address: Byte, sourceMUID: Int, destinationMUID: Int, val profile: MidiCIProfileId)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override val label = "SetProfileOff"
        override val bodyString = "profile=$profile)"
    }
    class ProfileEnabled(address: Byte, sourceMUID: Int, val profile: MidiCIProfileId, val numChannelsRequested: Short)
        : Message(Common(sourceMUID, address = address)) {
        override val label = "ProfileEnabled"
        override val bodyString = "profile=$profile, numChannelsRequested=$numChannelsRequested)"
    }
    class ProfileDisabled(address: Byte, sourceMUID: Int, val profile: MidiCIProfileId, val numChannelsRequested: Short)
        : Message(Common(sourceMUID, address = address)) {
        override val label = "ProfileDisabled"
        override val bodyString = "profile=$profile, numChannelsRequested=$numChannelsRequested)"
    }
    class ProfileDetailsInquiry(address: Byte, sourceMUID: Int, destinationMUID: Int, val profile: MidiCIProfileId, val target: Byte)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override val label = "ProfileDetailsInquiry"
        override val bodyString = "profile=$profile, target=$target)"
    }
    class ProfileDetailsReply(address: Byte, sourceMUID: Int, destinationMUID: Int, val profile: MidiCIProfileId, val target: Byte, val data: List<Byte>)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override val label = "ProfileDetailsReply"
        override val bodyString = "profile=$profile, target=$target, data=${data.dataString}))"
    }

    // Property Exchange
    class PropertyGetCapabilities(address: Byte, sourceMUID: Int, destinationMUID: Int, val maxSimultaneousRequests: Byte)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override val label = "PropertyGetCapabilities"
        override val bodyString = "maxSimultaneousRequests=${maxSimultaneousRequests})\n"
    }
    class PropertyGetCapabilitiesReply(address: Byte, sourceMUID: Int, destinationMUID: Int, val maxSimultaneousRequests: Byte)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override val label = "PropertyGetCapabilitiesReply"
        override val bodyString = "maxSimultaneousRequests=${maxSimultaneousRequests})"
    }
    abstract class PropertyMessage(common: Common, val requestId: Byte, val header: List<Byte>, val body: List<Byte>)
        : Message(common) {
        override val bodyString = "requestId=${requestId}, header=${header.headerString}, body=${body.bodyString}"
    }
    class GetPropertyData(sourceMUID: Int, destinationMUID: Int, requestId: Byte, header: List<Byte>)
        : PropertyMessage(Common(sourceMUID, destinationMUID), requestId, header, listOf()) {
        override val label = "GetPropertyData"
    }
    class GetPropertyDataReply(sourceMUID: Int, destinationMUID: Int, requestId: Byte, header: List<Byte>, body: List<Byte>)
        : PropertyMessage(Common(sourceMUID, destinationMUID), requestId, header, body) {
        override val label = "GetPropertyDataReply"
    }
    class SetPropertyData(sourceMUID: Int, destinationMUID: Int, requestId: Byte, header: List<Byte>, body: List<Byte>)
        : PropertyMessage(Common(sourceMUID, destinationMUID), requestId, header, body) {
        override val label = "SetPropertyData"
    }
    class SetPropertyDataReply(sourceMUID: Int, destinationMUID: Int, requestId: Byte, header: List<Byte>)
        : PropertyMessage(Common(sourceMUID, destinationMUID), requestId, header, listOf()) {
        override val label = "SetPropertyDataReply"
    }
    class SubscribeProperty(sourceMUID: Int, destinationMUID: Int, requestId: Byte, header: List<Byte>, body: List<Byte>)
        : PropertyMessage(Common(sourceMUID, destinationMUID), requestId, header, body) {
        override val label = "SubscribeProperty"
    }
    class SubscribePropertyReply(sourceMUID: Int, destinationMUID: Int, requestId: Byte, header: List<Byte>, body: List<Byte>)
        : PropertyMessage(Common(sourceMUID, destinationMUID), requestId, header, body) {
        override val label = "SubscribePropertyReply"
    }
    class PropertyNotify(sourceMUID: Int, destinationMUID: Int, requestId: Byte, header: List<Byte>, body: List<Byte>)
        : PropertyMessage(Common(sourceMUID, destinationMUID), requestId, header, body) {
        override val label = "PropertyNotify"
    }

    // Process Inquiry
    class ProcessInquiry(sourceMUID: Int, destinationMUID: Int)
        : Message(Common(sourceMUID, destinationMUID)) {
        override val label = "ProcessInquiry"
        override val bodyString = ""
    }
    class ProcessInquiryReply(sourceMUID: Int, destinationMUID: Int, val supportedFeatures: Byte)
        : Message(Common(sourceMUID, destinationMUID)) {
        override val label = "ProcessInquiryReply"
        override val bodyString = "supportedFeatures=$supportedFeatures)"
    }
    class ProcessMidiMessageReport(address: Byte, sourceMUID: Int, destinationMUID: Int,
                                    val messageDataControl: Byte,
                                    val systemMessages: Byte,
                                    val channelControllerMessages: Byte,
                                    val noteDataMessages: Byte)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override val label = "ProcessMidiMessageReport"
        override val bodyString = "messageDataControl=$messageDataControl, systemMessages=$systemMessages, channelControllerMessages=$channelControllerMessages, noteDataMessages=$noteDataMessages)"
    }
    class ProcessMidiMessageReportReply(address: Byte, sourceMUID: Int, destinationMUID: Int,
                                        val messageDataControl: Byte,
                                        val systemMessages: Byte,
                                        val channelControllerMessages: Byte,
                                        val noteDataMessages: Byte)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override val label = "ProcessMidiMessageReportReply"
        override val bodyString = "messageDataControl = $messageDataControl, systemMessages = $systemMessages, channelControllerMessages = $channelControllerMessages, noteDataMessages = $noteDataMessages)"
    }
    class ProcessEndOfMidiMessageReport(address: Byte, sourceMUID: Int, destinationMUID: Int)
        : Message(Common(sourceMUID, destinationMUID, address)) {
        override val label = "ProcessEndOfMidiMessageReport"
        override val bodyString = ""
    }
}
