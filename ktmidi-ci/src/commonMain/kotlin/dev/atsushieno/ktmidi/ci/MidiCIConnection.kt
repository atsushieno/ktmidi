package dev.atsushieno.ktmidi.ci

object CISubId2 {
    const val DISCOVERY_INQUIRY: Byte = 0x70
    const val DISCOVERY_REPLY: Byte = 0x71
    const val ENDPOINT_MESSAGE_INQUIRY: Byte = 0x72
    const val ENDPOINT_MESSAGE_REPLY: Byte = 0x73
    const val ACK: Byte = 0x7D
    const val INVALIDATE_MUID: Byte = 0x7E
    const val NAK: Byte = 0x7F

    // Those Protocol Negotiation SUB ID2s are all deprecated.
    @Deprecated("Protocol Negotiation is deprecated.")
    const val PROTOCOL_NEGOTIATION_INQUIRY: Byte = 0x10
    @Deprecated("Protocol Negotiation is deprecated.")
    const val PROTOCOL_NEGOTIATION_REPLY: Byte = 0x11
    @Deprecated("Protocol Negotiation is deprecated.")
    const val SET_NEW_PROTOCOL: Byte = 0x12
    @Deprecated("Protocol Negotiation is deprecated.")
    const val TEST_NEW_PROTOCOL_I2R: Byte = 0x13
    @Deprecated("Protocol Negotiation is deprecated.")
    const val TEST_NEW_PROTOCOL_R2I: Byte = 0x14
    @Deprecated("Protocol Negotiation is deprecated.")
    const val CONFIRM_NEW_PROTOCOL_ESTABLISHED: Byte = 0x15

    const val PROFILE_INQUIRY: Byte = 0x20
    const val PROFILE_INQUIRY_REPLY: Byte = 0x21
    const val SET_PROFILE_ON: Byte = 0x22
    const val SET_PROFILE_OFF: Byte = 0x23
    const val PROFILE_ENABLED_REPORT: Byte = 0x24
    const val PROFILE_DISABLED_REPORT: Byte = 0x25
    const val PROFILE_ADDED_REPORT: Byte = 0x26
    const val PROFILE_REMOVED_REPORT: Byte = 0x27
    const val PROFILE_DETAILS_INQUIRY: Byte = 0x28
    const val PROFILE_DETAILS_REPLY: Byte = 0x29
    const val PROFILE_SPECIFIC_DATA: Byte = 0x2F

    const val PROPERTY_CAPABILITIES_INQUIRY: Byte = 0x30
    const val PROPERTY_CAPABILITIES_REPLY: Byte = 0x31
    @Deprecated("Property Has Data Inquiry is deprecated.")
    const val PROPERTY_HAS_DATA_INQUIRY: Byte = 0x32
    @Deprecated("Property Has Data Reply is deprecated.")
    const val PROPERTY_HAS_DATA_REPLY: Byte = 0x33
    const val PROPERTY_GET_DATA_INQUIRY: Byte = 0x34
    const val PROPERTY_GET_DATA_REPLY: Byte = 0x35
    const val PROPERTY_SET_DATA_INQUIRY: Byte = 0x36
    const val PROPERTY_SET_DATA_REPLY: Byte = 0x37
    const val PROPERTY_SUBSCRIBE: Byte = 0x38
    const val PROPERTY_SUBSCRIBE_REPLY: Byte = 0x39
    const val PROPERTY_NOTIFY: Byte = 0x3F

    const val PROCESS_INQUIRY_CAPABILITIES: Byte = 0x40
    const val PROCESS_INQUIRY_CAPABILITIES_REPLY: Byte = 0x41
    const val PROCESS_INQUIRY_MIDI_MESSAGE_REPORT: Byte = 0x42
    const val PROCESS_INQUIRY_MIDI_MESSAGE_REPORT_REPLY: Byte = 0x43
    const val PROCESS_INQUIRY_END_OF_MIDI_MESSAGE: Byte = 0x44
}

object CINakStatus {
    const val Nak: Byte = 0
    const val MessageNotSupported: Byte = 1
    const val CIVersionNotSupported: Byte = 2
    const val TargetNotInUse: Byte = 3 // Target = Channel/Group/FunctionBlock
    const val ProfileNotSupportedOnTarget: Byte = 4
    const val TerminateInquiry: Byte = 0x20
    const val PropertyExchangeChunksAreOutOfSequence: Byte = 0x21
    const val ErrorRetrySuggested: Byte = 0x40
    const val MalformedMessage: Byte = 0x41
    const val Timeout: Byte = 0x42
    const val TimeoutRetrySuggested = 0x43
}

data class DeviceDetails(val manufacturer: Int = 0, val family: Short = 0, val modelNumber: Short = 0, val softwareRevisionLevel: Int = 0) {
    companion object {
        val empty = DeviceDetails()
    }
}

object MidiCISupportedCategories {
    const val NONE: Byte = 0
    const val PROTOCOL_NEGOTIATION: Byte = 1 // Deprecated in MIDI-CI 1.2
    const val PROFILE_CONFIGURATION: Byte = 4
    const val PROPERTY_EXCHANGE: Byte = 8
    const val PROCES_INQUIRY: Byte = 16
    // I'm inclined to say "All", but that may change in the future and it indeed did.
    // Even worse, the definition of those Three Ps had changed...
    const val THREE_P: Byte = (PROFILE_CONFIGURATION + PROPERTY_EXCHANGE + PROCES_INQUIRY).toByte()
}

object MidiCISubscriptionCommand {
    const val START = "start"
    const val PARTIAL = "partial"
    const val FULL = "full"
    const val NOTIFY = "notify"
    const val END = "end"
}

object MidiCIConstants {
    const val UNIVERSAL_SYSEX: Byte = 0x7E
    const val UNIVERSAL_SYSEX_SUB_ID_MIDI_CI: Byte = 0x0D

    const val CI_VERSION_AND_FORMAT: Byte = 0x2
    const val PROPERTY_EXCHANGE_MAJOR_VERSION: Byte = 0
    const val PROPERTY_EXCHANGE_MINOR_VERSION: Byte = 0

    const val ENDPOINT_STATUS_PRODUCT_INSTANCE_ID: Byte = 0

    const val DEFAULT_RECEIVABLE_MAX_SYSEX_SIZE = 4096
    const val DEFAULT_MAX_PROPERTY_CHUNK_SIZE = 4096 - 256
    const val DEFAULT_MAX_SIMULTANEOUS_PROPERTY_REQUESTS: Byte = 127

    const val ADDRESS_GROUP: Byte = 0x7E
    const val ADDRESS_FUNCTION_BLOCK: Byte = 0x7F

    const val NO_FUNCTION_BLOCK: Byte = 0x7F
    const val WHOLE_FUNCTION_BLOCK: Byte = 0x7F

    const val BROADCAST_MUID_28 = 0xFFFFFFF
    const val BROADCAST_MUID_32 = 0x7F7F7F7F

    const val STANDARD_DEFINED_PROFILE: Byte = 0x7E
}

object MidiCIConverter {

    fun encodeStringToASCII(s: String): String {
        return if (s.all { it.code < 0x80 && !it.isISOControl() })
            s
        else
            s.map { if (it.code < 0x80) it.toString() else "\\u${it.code.toString(16)}" }.joinToString("")
    }
    fun decodeASCIIToString(s: String): String =
        s.split("\\u").mapIndexed { index, e ->
            if (index == 0)
                e
            else
                e.substring(0, 4).toInt(16).toChar() + s.substring(4)
        }.joinToString("")
}