package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.MidiCIProtocolType
import dev.atsushieno.ktmidi.MidiCIProtocolValue

data class DeviceDetails(val manufacturer: Int = 0, val family: Short = 0, val modelNumber: Short = 0, val softwareRevisionLevel: Int = 0) {
    companion object {
        val empty = DeviceDetails()
    }
}

enum class MidiCIInitiatorState {
    Initial,
    DISCOVERY_SENT,
    DISCOVERED,
    NEW_PROTOCOL_SENT, // almost no chance to stay at the state
    TEST_SENT,
    ESTABLISHED
}

object MidiCIDiscoveryCategoryFlags {
    const val None: Byte = 0
    const val ProtocolNegotiation: Byte = 1 // Deprecated in MIDI-CI 1.2
    const val ProfileConfiguration: Byte = 4
    const val PropertyExchange: Byte = 8
    const val ProcessInquiry: Byte = 16
    // I'm inclined to say "All", but that may change in the future and it indeed did.
    // Even worse, the definition of those Three Ps had changed...
    const val ThreePs: Byte = (ProfileConfiguration + PropertyExchange + ProcessInquiry).toByte()
}

object MidiCIConstants {
    const val UNIVERSAL_SYSEX: Byte = 0x7E
    const val UNIVERSAL_SYSEX_SUB_ID_MIDI_CI: Byte = 0x0D

    const val CI_VERSION_AND_FORMAT: Byte = 0x2
    const val PROPERTY_EXCHANGE_MAJOR_VERSION: Byte = 0
    const val PROPERTY_EXCHANGE_MINOR_VERSION: Byte = 0

    const val ENDPOINT_STATUS_PRODUCT_INSTANCE_ID: Byte = 0

    const val DEFAULT_RECEIVABLE_MAX_SYSEX_SIZE = 4096

    const val DEVICE_ID_MIDI_PORT: Byte = 0x7F

    const val NO_FUNCTION_BLOCK: Byte = 0x7F
    const val WHOLE_FUNCTION_BLOCK: Byte = 0x7F

    const val BROADCAST_MUID_28 = 0xFFFFFFF
    const val BROADCAST_MUID_32 = 0x7F7F7F7F

    val Midi1ProtocolTypeInfo = MidiCIProtocolTypeInfo(MidiCIProtocolType
        .MIDI1.toByte(), MidiCIProtocolValue.MIDI1.toByte(), 0, 0, 0)
    val Midi2ProtocolTypeInfo = MidiCIProtocolTypeInfo(MidiCIProtocolType
        .MIDI2.toByte(), MidiCIProtocolValue.MIDI2_V1.toByte(), 0, 0, 0)
    // The list is ordered from most preferred protocol type info, as per MIDI-CI spec. section 6.5 describes.
    val Midi2ThenMidi1Protocols = listOf(Midi2ProtocolTypeInfo, Midi1ProtocolTypeInfo)
    val Midi1ThenMidi2Protocols = listOf(Midi1ProtocolTypeInfo, Midi2ProtocolTypeInfo)
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

