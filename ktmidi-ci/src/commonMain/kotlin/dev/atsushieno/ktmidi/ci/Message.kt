package dev.atsushieno.ktmidi.ci

abstract class Message(protected val common: Common) {
    val group: Byte
        get() = common.group
    val address: Byte
        get() = common.address
    val sourceMUID: Int
        get() = common.sourceMUID
    val destinationMUID: Int
        get() = common.destinationMUID

    // FIXME: maybe we should implement serialize() and deserialize() in each class

    companion object {
        const val COMMON_HEADER_SIZE = 13
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
        val Byte.groupString: String
            get() = this.toString(16)

        val messageSizes = mapOf(
            Pair(CISubId2.DISCOVERY_INQUIRY, 30),
            Pair(CISubId2.DISCOVERY_REPLY, 31),
            Pair(CISubId2.ENDPOINT_MESSAGE_INQUIRY, 14),
            Pair(CISubId2.ENDPOINT_MESSAGE_REPLY, 16),
            Pair(CISubId2.INVALIDATE_MUID, 17),
            Pair(CISubId2.ACK, 13),
            Pair(CISubId2.NAK, 13),
            Pair(CISubId2.PROFILE_INQUIRY, 13),
            Pair(CISubId2.PROFILE_INQUIRY_REPLY, 15),
            Pair(CISubId2.PROFILE_ADDED_REPORT, 18),
            Pair(CISubId2.PROFILE_REMOVED_REPORT, 18),
            Pair(CISubId2.SET_PROFILE_ON, 20),
            Pair(CISubId2.SET_PROFILE_OFF, 20),
            Pair(CISubId2.PROFILE_ENABLED_REPORT, 20),
            Pair(CISubId2.PROFILE_DISABLED_REPORT, 20),
            Pair(CISubId2.PROFILE_DETAILS_INQUIRY, 19),
            Pair(CISubId2.PROFILE_DETAILS_REPLY, 22),
            Pair(CISubId2.PROPERTY_CAPABILITIES_INQUIRY, 13),
            Pair(CISubId2.PROPERTY_CAPABILITIES_REPLY, 14),
            Pair(CISubId2.PROPERTY_GET_DATA_INQUIRY, 22),
            Pair(CISubId2.PROPERTY_GET_DATA_REPLY, 22),
            Pair(CISubId2.PROPERTY_SET_DATA_INQUIRY, 22),
            Pair(CISubId2.PROPERTY_SET_DATA_REPLY, 22),
            Pair(CISubId2.PROPERTY_SUBSCRIBE, 22),
            Pair(CISubId2.PROPERTY_SUBSCRIBE_REPLY, 22),
            Pair(CISubId2.PROPERTY_NOTIFY, 22),
            Pair(CISubId2.PROCESS_INQUIRY_CAPABILITIES, 13),
            Pair(CISubId2.PROCESS_INQUIRY_CAPABILITIES_REPLY, 14),
            Pair(CISubId2.PROCESS_INQUIRY_MIDI_MESSAGE_REPORT, 18),
            Pair(CISubId2.PROCESS_INQUIRY_MIDI_MESSAGE_REPORT_REPLY, 17),
            Pair(CISubId2.PROCESS_INQUIRY_END_OF_MIDI_MESSAGE, 13),
        )

        val dynamicallySizedMessageSubId2s = listOf(
            CISubId2.ACK,
            CISubId2.NAK,
            CISubId2.ENDPOINT_MESSAGE_REPLY,
            CISubId2.PROFILE_INQUIRY_REPLY,
            CISubId2.PROFILE_SPECIFIC_DATA,
            CISubId2.PROPERTY_GET_DATA_INQUIRY,
            CISubId2.PROPERTY_GET_DATA_REPLY,
            CISubId2.PROPERTY_SET_DATA_INQUIRY,
            CISubId2.PROPERTY_SET_DATA_REPLY,
            CISubId2.PROPERTY_SUBSCRIBE,
            CISubId2.PROPERTY_SUBSCRIBE_REPLY,
            CISubId2.PROPERTY_NOTIFY,
        )
    }

    data class Common(val sourceMUID: Int, val destinationMUID: Int = MidiCIConstants.BROADCAST_MUID_32, val address: Byte = MidiCIConstants.ADDRESS_FUNCTION_BLOCK, val group: Byte = 0) {
        override fun toString() = "{group=${group.groupString}, address=${address.addressString}, sourceMUID=${sourceMUID.muidString}, destinationMUID=${destinationMUID.muidString}}"
    }

    abstract val label: String
    abstract val bodyString: String
    override fun toString() = "$label($common, $bodyString)"

    // Discovery
    class DiscoveryInquiry(common: Common, val device: DeviceDetails, val ciCategorySupported: Byte, val receivableMaxSysExSize: Int, val outputPathId: Byte)
        : Message(common) {
        override val label = "DiscoveryInquiry"
        override val bodyString = "device=${device}, ciCategorySupported=$ciCategorySupported, receivableMaxSysExSize=$receivableMaxSysExSize, outputPathId=$outputPathId"
    }
    class DiscoveryReply(common: Common, val device: DeviceDetails, val ciCategorySupported: Byte,  val receivableMaxSysExSize: Int, val outputPathId: Byte, val functionBlock: Byte)
        : Message(common) {
        override val label = "DiscoveryReply"
        override val bodyString = "ciCategorySupported=$ciCategorySupported, receivableMaxSysExSize=$receivableMaxSysExSize, outputPathId=$outputPathId, functionBlock=$functionBlock"
    }

    class EndpointInquiry(common: Common, val status: Byte)
        : Message(common) {
        override val label = "EndpointInquiry"
        override val bodyString = "status=${status}"
    }
    class EndpointReply(common: Common, val status: Byte, val data: List<Byte>)
        : Message(common) {
        override val label = "EndpointReply"
        override val bodyString = "status=${status}, data = ${data.dataString})"
    }

    class InvalidateMUID(common: Common, val targetMUID: Int)
        : Message(common) {
        override val label = "InvalidateMUID"
        override val bodyString = "targetMUID=${targetMUID.muidString})"
    }

    class Nak(common: Common,
              val originalSubId: Byte, val statusCode: Byte, val statusData: Byte, val details: List<Byte>, val message: List<Byte>)
        : Message(common) {
        override val label = "Nak"
        override val bodyString = "originalSubId=$originalSubId, statusCode=$statusCode, statusData=$statusData, details=${details.dataString}, message=$message"
    }

    // Profile Configuration
    class ProfileInquiry(common: Common)
        : Message(common) {
        override val label = "ProfileInquiry"
        override val bodyString = ""
    }
    class ProfileReply(common: Common, val enabledProfiles: List<MidiCIProfileId>, val disabledProfiles: List<MidiCIProfileId>)
        : Message(common) {
        override val label = "ProfileReply"
        override val bodyString = "enabledProfiles=[${enabledProfiles.joinToString { it.toString() }}],  disabledProfiles=[${disabledProfiles.joinToString { it.toString() }}]"
    }
    class ProfileAdded(common: Common, val profile: MidiCIProfileId)
        : Message(common) {
        override val label = "ProfileAdded"
        override val bodyString = "profile=$profile"
    }
    class ProfileRemoved(common: Common, val profile: MidiCIProfileId)
        : Message(common) {
        override val label = "ProfileRemoved"
        override val bodyString = "profile=$profile"
    }
    class SetProfileOn(common: Common, val profile: MidiCIProfileId, val numChannelsRequested: Short)
        : Message(common) {
        override val label = "SetProfileOn"
        override val bodyString = "profile=$profile, numChannelsRequested=$numChannelsRequested"
    }
    class SetProfileOff(common: Common, val profile: MidiCIProfileId)
        : Message(common) {
        override val label = "SetProfileOff"
        override val bodyString = "profile=$profile"
    }
    class ProfileEnabled(common: Common, val profile: MidiCIProfileId, val numChannelsRequested: Short)
        : Message(common) {
        override val label = "ProfileEnabled"
        override val bodyString = "profile=$profile, numChannelsRequested=$numChannelsRequested"
    }
    class ProfileDisabled(common: Common, val profile: MidiCIProfileId, val numChannelsRequested: Short)
        : Message(common) {
        override val label = "ProfileDisabled"
        override val bodyString = "profile=$profile, numChannelsRequested=$numChannelsRequested"
    }
    class ProfileDetailsInquiry(common: Common, val profile: MidiCIProfileId, val target: Byte)
        : Message(common) {
        override val label = "ProfileDetailsInquiry"
        override val bodyString = "profile=$profile, target=$target"
    }
    class ProfileDetailsReply(common: Common, val profile: MidiCIProfileId, val target: Byte, val data: List<Byte>)
        : Message(common) {
        override val label = "ProfileDetailsReply"
        override val bodyString = "profile=$profile, target=$target, data=${data.dataString}"
    }
    class ProfileSpecificData(common: Common, val profile: MidiCIProfileId, val data: List<Byte>)
        : Message(common) {
        override val label = "ProfileSpecificData"
        override val bodyString = "profile=$profile, data=${data.dataString}"
    }

    // Property Exchange
    class PropertyGetCapabilities(common: Common, val maxSimultaneousRequests: Byte)
        : Message(common) {
        override val label = "PropertyGetCapabilities"
        override val bodyString = "maxSimultaneousRequests=${maxSimultaneousRequests}"
    }
    class PropertyGetCapabilitiesReply(common: Common, val maxSimultaneousRequests: Byte)
        : Message(common) {
        override val label = "PropertyGetCapabilitiesReply"
        override val bodyString = "maxSimultaneousRequests=${maxSimultaneousRequests}"
    }
    abstract class PropertyMessage(common: Common, val requestId: Byte, val header: List<Byte>, val body: List<Byte>)
        : Message(common) {
        override val bodyString = "requestId=${requestId}, header=${header.headerString}, body=${body.bodyString}"
    }
    class GetPropertyData(common: Common, requestId: Byte, header: List<Byte>)
        : PropertyMessage(common, requestId, header, listOf()) {
        override val label = "GetPropertyData"
    }
    class GetPropertyDataReply(common: Common, requestId: Byte, header: List<Byte>, body: List<Byte>)
        : PropertyMessage(common, requestId, header, body) {
        override val label = "GetPropertyDataReply"
    }
    class SetPropertyData(common: Common, requestId: Byte, header: List<Byte>, body: List<Byte>)
        : PropertyMessage(common, requestId, header, body) {
        override val label = "SetPropertyData"
    }
    class SetPropertyDataReply(common: Common, requestId: Byte, header: List<Byte>)
        : PropertyMessage(common, requestId, header, listOf()) {
        override val label = "SetPropertyDataReply"
    }
    class SubscribeProperty(common: Common, requestId: Byte, header: List<Byte>, body: List<Byte>)
        : PropertyMessage(common, requestId, header, body) {
        override val label = "SubscribeProperty"
    }
    class SubscribePropertyReply(common: Common, requestId: Byte, header: List<Byte>, body: List<Byte>)
        : PropertyMessage(common, requestId, header, body) {
        override val label = "SubscribePropertyReply"
    }
    class PropertyNotify(common: Common, requestId: Byte, header: List<Byte>, body: List<Byte>)
        : PropertyMessage(common, requestId, header, body) {
        override val label = "PropertyNotify"
    }

    // Process Inquiry
    class ProcessInquiry(common: Common)
        : Message(common) {
        override val label = "ProcessInquiry"
        override val bodyString = ""
    }
    class ProcessInquiryReply(common: Common, val supportedFeatures: Byte)
        : Message(common) {
        override val label = "ProcessInquiryReply"
        override val bodyString = "supportedFeatures=$supportedFeatures"
    }
    class MidiMessageReportInquiry(common: Common,
                                   val messageDataControl: Byte,
                                   val systemMessages: Byte,
                                   val channelControllerMessages: Byte,
                                   val noteDataMessages: Byte)
        : Message(common) {
        override val label = "MidiMessageReportInquiry"
        override val bodyString = "messageDataControl=$messageDataControl, systemMessages=$systemMessages, channelControllerMessages=$channelControllerMessages, noteDataMessages=$noteDataMessages"
    }
    class MidiMessageReportReply(common: Common,
                                 val systemMessages: Byte,
                                 val channelControllerMessages: Byte,
                                 val noteDataMessages: Byte)
        : Message(common) {
        override val label = "MidiMessageReportReply"
        override val bodyString = "systemMessages = $systemMessages, channelControllerMessages = $channelControllerMessages, noteDataMessages = $noteDataMessages"
    }
    class MidiMessageReportNotifyEnd(common: Common)
        : Message(common) {
        override val label = "MidiMessageReportNotifyEnd"
        override val bodyString = ""
    }
}
