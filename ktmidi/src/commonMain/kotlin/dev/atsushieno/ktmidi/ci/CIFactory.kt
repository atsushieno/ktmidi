package dev.atsushieno.ktmidi.ci
import kotlin.experimental.and

const val CMIDI2_CI_SUB_ID:Byte = 0xD

const val CMIDI2_CI_SUB_ID_2_DISCOVERY_INQUIRY:Byte = 0x70
const val CMIDI2_CI_SUB_ID_2_DISCOVERY_REPLY:Byte = 0x71
const val CMIDI2_CI_SUB_ID_2_INVALIDATE_MUID:Byte = 0x7E
const val CMIDI2_CI_SUB_ID_2_NAK:Byte = 0x7F
const val CMIDI2_CI_SUB_ID_2_PROTOCOL_NEGOTIATION_INQUIRY:Byte = 0x10
const val CMIDI2_CI_SUB_ID_2_PROTOCOL_NEGOTIATION_REPLY:Byte = 0x11
const val CMIDI2_CI_SUB_ID_2_SET_NEW_PROTOCOL:Byte = 0x12
const val CMIDI2_CI_SUB_ID_2_TEST_NEW_PROTOCOL_I2R:Byte = 0x13
const val CMIDI2_CI_SUB_ID_2_TEST_NEW_PROTOCOL_R2I:Byte = 0x14
const val CMIDI2_CI_SUB_ID_2_CONFIRM_NEW_PROTOCOL_ESTABLISHED:Byte = 0x15
const val CMIDI2_CI_SUB_ID_2_PROFILE_INQUIRY:Byte = 0x20
const val CMIDI2_CI_SUB_ID_2_PROFILE_INQUIRY_REPLY:Byte = 0x21
const val CMIDI2_CI_SUB_ID_2_SET_PROFILE_ON:Byte = 0x22
const val CMIDI2_CI_SUB_ID_2_SET_PROFILE_OFF:Byte = 0x23
const val CMIDI2_CI_SUB_ID_2_PROFILE_ENABLED_REPORT:Byte = 0x24
const val CMIDI2_CI_SUB_ID_2_PROFILE_DISABLED_REPORT:Byte = 0x25
const val CMIDI2_CI_SUB_ID_2_PROFILE_SPECIFIC_DATA:Byte = 0x2F
const val CMIDI2_CI_SUB_ID_2_PROPERTY_CAPABILITIES_INQUIRY:Byte = 0x30
const val CMIDI2_CI_SUB_ID_2_PROPERTY_CAPABILITIES_REPLY:Byte = 0x31
const val CMIDI2_CI_SUB_ID_2_PROPERTY_HAS_DATA_INQUIRY:Byte = 0x32
const val CMIDI2_CI_SUB_ID_2_PROPERTY_HAS_DATA_REPLY:Byte = 0x33
const val CMIDI2_CI_SUB_ID_2_PROPERTY_GET_DATA_INQUIRY:Byte = 0x34
const val CMIDI2_CI_SUB_ID_2_PROPERTY_GET_DATA_REPLY:Byte = 0x35
const val CMIDI2_CI_SUB_ID_2_PROPERTY_SET_DATA_INQUIRY:Byte = 0x36
const val CMIDI2_CI_SUB_ID_2_PROPERTY_SET_DATA_REPLY:Byte = 0x37
const val CMIDI2_CI_SUB_ID_2_PROPERTY_SUBSCRIBE:Byte = 0x38
const val CMIDI2_CI_SUB_ID_2_PROPERTY_SUBSCRIBE_REPLY:Byte = 0x39
const val CMIDI2_CI_SUB_ID_2_PROPERTY_NOTIFY:Byte = 0x3F

const val CMIDI2_CI_PROTOCOL_NEGOTIATION_SUPPORTED = 2
const val CMIDI2_CI_PROFILE_CONFIGURATION_SUPPORTED = 4
const val CMIDI2_CI_PROPERTY_EXCHANGE_SUPPORTED = 8

class MidiCIProtocolTypeInfo(
    val type: Byte,
    val version: Byte,
    val extensions: Byte,
    val reserved1: Byte,
    val reserved2: Byte
)

class MidiCIProfileId(
    val fixed_7e: Byte,
    val bank: Byte,
    val number: Byte,
    val version: Byte,
    val level: Byte
)

// Assumes the input value is already 7-bit encoded if required.
fun midiCiDirectUint16At(buf: MutableList<Byte>, offset: Int, v: Short) {
    buf[offset] = (v and 0xFF).toByte()
    buf[offset + 1] = (v.toInt() shr 8 and 0xFF).toByte()
}

// Assumes the input value is already 7-bit encoded if required.
fun midiCiDirectUint32At(buf: MutableList<Byte>, offset: Int, v: Int) {
    buf[offset] = (v and 0xFF).toByte()
    buf[offset + 1] = (v shr 8 and 0xFF).toByte()
    buf[offset + 2] = (v shr 16 and 0xFF).toByte()
    buf[offset + 3] = (v shr 24 and 0xFF).toByte()
}

fun midiCI7bitInt14At(buf: MutableList<Byte>, offset: Int, v: Short) {
    buf[offset] = (v and 0x7F).toByte()
    buf[offset + 1] = (v.toInt() shr 7 and 0x7F).toByte()
}

fun midiCI7bitInt21At(buf: MutableList<Byte>, offset: Int, v: Int) {
    buf[offset] = (v and 0x7F).toByte()
    buf[offset + 1] = (v shr 7 and 0x7F).toByte()
    buf[offset + 2] = (v shr 14 and 0x7F).toByte()
}

fun midiCI7bitInt28At(buf: MutableList<Byte>, offset: Int, v: Int) {
    buf[offset] = (v and 0x7F).toByte()
    buf[offset + 1] = (v shr 7 and 0x7F).toByte()
    buf[offset + 2] = (v shr 14 and 0x7F).toByte()
    buf[offset + 3] = (v shr 21 and 0x7F).toByte()
}

fun midiCIMessageCommon(
    buf: MutableList<Byte>,
    destination: Byte, sysexSubId2: Byte, versionAndFormat: Byte, sourceMUID: Int, destinationMUID: Int
) {
    buf[0] = 0x7E
    buf[1] = destination
    buf[2] = 0xD
    buf[3] = sysexSubId2
    buf[4] = versionAndFormat
    midiCiDirectUint32At(buf, 5, sourceMUID)
    midiCiDirectUint32At(buf, 9, destinationMUID)
}


// Discovery

fun midiCIDiscoveryCommon(
    buf: MutableList<Byte>, sysexSubId2: Byte,
    versionAndFormat: Byte, sourceMUID: Int, destinationMUID: Int,
    deviceManufacturer3Bytes: Int, deviceFamily: Short, deviceFamilyModelNumber: Short,
    softwareRevisionLevel: Int, ciCategorySupported: Byte, receivableMaxSysExSize: Int
) {
    midiCIMessageCommon(buf, 0x7F, sysexSubId2, versionAndFormat, sourceMUID, destinationMUID)
    midiCiDirectUint32At(
        buf, 13,
        deviceManufacturer3Bytes.toInt()
    ) // the last byte is extraneous, but will be overwritten next.
    midiCiDirectUint16At(buf, 16, deviceFamily)
    midiCiDirectUint16At(buf, 18, deviceFamilyModelNumber)
    // LAMESPEC: Software Revision Level does not mention in which endianness this field is stored.
    midiCiDirectUint32At(buf, 20, softwareRevisionLevel.toInt())
    buf[24] = ciCategorySupported
    midiCiDirectUint32At(buf, 25, receivableMaxSysExSize.toInt())
}

fun midiCIDiscovery(
    buf: MutableList<Byte>,
    versionAndFormat: Byte, sourceMUID: Int,
    deviceManufacturer: Int, deviceFamily: Short, deviceFamilyModelNumber: Short,
    softwareRevisionLevel: Int, ciCategorySupported: Byte, receivableMaxSysExSize: Int
) {
    midiCIDiscoveryCommon(
        buf, CMIDI2_CI_SUB_ID_2_DISCOVERY_INQUIRY,
        versionAndFormat, sourceMUID, 0x7F7F7F7F,
        deviceManufacturer, deviceFamily, deviceFamilyModelNumber,
        softwareRevisionLevel, ciCategorySupported, receivableMaxSysExSize
    )
}

fun midiCIDiscoveryReply(
    buf: MutableList<Byte>,
    versionAndFormat: Byte, sourceMUID: Int, destinationMUID: Int,
    deviceManufacturer: Int, deviceFamily: Short, deviceFamilyModelNumber: Short,
    softwareRevisionLevel: Int, ciCategorySupported: Byte, receivableMaxSysExSize: Int
) {
    midiCIDiscoveryCommon(
        buf, CMIDI2_CI_SUB_ID_2_DISCOVERY_REPLY,
        versionAndFormat, sourceMUID, destinationMUID,
        deviceManufacturer, deviceFamily, deviceFamilyModelNumber,
        softwareRevisionLevel, ciCategorySupported, receivableMaxSysExSize
    )
}

fun midiCIDiscoveryInvalidateMuid(
    buf: MutableList<Byte>,
    versionAndFormat: Byte, sourceMUID: Int, targetMUID: Int
) {
    midiCIMessageCommon(buf, 0x7F, 0x7E, versionAndFormat, sourceMUID, 0x7F7F7F7F)
    midiCiDirectUint32At(buf, 13, targetMUID)
}

fun midiCIDiscoveryNak(
    buf: MutableList<Byte>, deviceId: Byte,
    versionAndFormat: Byte, sourceMUID: Int, destinationMUID: Int
) {
    midiCIMessageCommon(buf, deviceId, 0x7F, versionAndFormat, sourceMUID, destinationMUID)
}


// Protocol Negotiation

fun midiCIProtocolInfo(buf: MutableList<Byte>, offset: Int, info: MidiCIProtocolTypeInfo) {
    buf[offset] = info.type
    buf[offset + 1] = info.version
    buf[offset + 2] = info.extensions
    buf[offset + 3] = info.reserved1
    buf[offset + 4] = info.reserved2
}

fun midiCIProtocols(
    buf: MutableList<Byte>,
    offset: Int,
    numSupportedProtocols: Byte,
    protocolTypes: MutableList<MidiCIProtocolTypeInfo>
) {
    buf[offset] = numSupportedProtocols
    for (i in 0 until numSupportedProtocols.toInt())
        midiCIProtocolInfo(buf, offset + 1 + i * 5, protocolTypes[i])
}

fun midiCIProtocolNegotiation(
    buf: MutableList<Byte>, isReply: Boolean,
    sourceMUID: Int, destinationMUID: Int,
    authorityLevel: Byte,
    numSupportedProtocols: Byte, protocolTypes: MutableList<MidiCIProtocolTypeInfo>
) {
    midiCIMessageCommon(
        buf, 0x7F,
        if (isReply) CMIDI2_CI_SUB_ID_2_PROTOCOL_NEGOTIATION_REPLY else CMIDI2_CI_SUB_ID_2_PROTOCOL_NEGOTIATION_INQUIRY,
        1, sourceMUID, destinationMUID
    )
    buf[13] = authorityLevel
    midiCIProtocols(buf, 14, numSupportedProtocols, protocolTypes)
}

fun midiCIProtocolSet(
    buf: MutableList<Byte>,
    sourceMUID: Int, destinationMUID: Int,
    authorityLevel: Byte, newProtocolType: MidiCIProtocolTypeInfo
) {
    midiCIMessageCommon(
        buf, 0x7F,
        CMIDI2_CI_SUB_ID_2_SET_NEW_PROTOCOL,
        1, sourceMUID, destinationMUID
    )
    buf[13] = authorityLevel
    midiCIProtocolInfo(buf, 14, newProtocolType)
}

fun midiCIProtocolTest(
    buf: MutableList<Byte>,
    isInitiatorToResponder: Boolean,
    sourceMUID: Int, destinationMUID: Int,
    authorityLevel: Byte, testData48Bytes: MutableList<Byte>
) {
    midiCIMessageCommon(
        buf, 0x7F,
        if (isInitiatorToResponder) CMIDI2_CI_SUB_ID_2_TEST_NEW_PROTOCOL_I2R else CMIDI2_CI_SUB_ID_2_TEST_NEW_PROTOCOL_R2I,
        1, sourceMUID, destinationMUID
    )
    buf[13] = authorityLevel
    memcpy(buf, 14, testData48Bytes, 48)
}

fun midiCIProtocolConfirmEstablished(
    buf: MutableList<Byte>,
    sourceMUID: Int, destinationMUID: Int,
    authorityLevel: Byte
) {
    midiCIMessageCommon(
        buf, 0x7F,
        CMIDI2_CI_SUB_ID_2_CONFIRM_NEW_PROTOCOL_ESTABLISHED,
        1, sourceMUID, destinationMUID
    )
    buf[13] = authorityLevel
}


// Profile Configuration

fun midiCIProfile(buf: MutableList<Byte>, offset: Int, info: MidiCIProfileId) {
    buf[offset] = info.fixed_7e
    buf[offset + 1] = info.bank
    buf[offset + 2] = info.number
    buf[offset + 3] = info.version
    buf[offset + 4] = info.level
}

fun midiCIProfileInquiry(
    buf: MutableList<Byte>, source: Byte,
    sourceMUID: Int, destinationMUID: Int
) {
    midiCIMessageCommon(
        buf, source,
        CMIDI2_CI_SUB_ID_2_PROFILE_INQUIRY,
        1, sourceMUID, destinationMUID
    )
}

fun midiCIProfileInquiryReply(
    buf: MutableList<Byte>, source: Byte,
    sourceMUID: Int, destinationMUID: Int,
    numEnabledProfiles: Byte, enabledProfiles: MutableList<MidiCIProfileId>,
    numDisabledProfiles: Byte, disabledProfiles: MutableList<MidiCIProfileId>
) {
    midiCIMessageCommon(
        buf, source,
        CMIDI2_CI_SUB_ID_2_PROFILE_INQUIRY_REPLY,
        1, sourceMUID, destinationMUID
    )
    buf[13] = numEnabledProfiles
    for (i in 0 until numEnabledProfiles)
        midiCIProfile(buf, 14 + i * 5, enabledProfiles[i])
    var pos: Int = 14 + numEnabledProfiles * 5
    buf[pos++] = numDisabledProfiles
    for (i in 0 until numDisabledProfiles)
        midiCIProfile(buf, pos + i * 5, disabledProfiles[i])
}

fun midiCIProfileSet(
    buf: MutableList<Byte>, destination: Byte, turnOn: Boolean,
    sourceMUID: Int, destinationMUID: Int, profile: MidiCIProfileId
) {
    midiCIMessageCommon(
        buf, destination,
        if (turnOn) CMIDI2_CI_SUB_ID_2_SET_PROFILE_ON else CMIDI2_CI_SUB_ID_2_SET_PROFILE_OFF,
        1, sourceMUID, destinationMUID
    )
    midiCIProfile(buf, 13, profile)
}

fun midiCIProfileReport(
    buf: MutableList<Byte>, source: Byte, isEnabledReport: Boolean,
    sourceMUID: Int, profile: MidiCIProfileId
) {
    midiCIMessageCommon(
        buf, source,
        if (isEnabledReport) CMIDI2_CI_SUB_ID_2_PROFILE_ENABLED_REPORT else CMIDI2_CI_SUB_ID_2_PROFILE_DISABLED_REPORT,
        1, sourceMUID, 0x7F7F7F7F
    )
    midiCIProfile(buf, 13, profile)
}

fun midiCIProfileSpecificData(
    buf: MutableList<Byte>, source: Byte,
    sourceMUID: Int, destinationMUID: Int, profile: MidiCIProfileId, dataSize: Int, data: MutableList<Byte>
) {
    midiCIMessageCommon(
        buf, source,
        CMIDI2_CI_SUB_ID_2_PROFILE_SPECIFIC_DATA,
        1, sourceMUID, destinationMUID
    )
    midiCIProfile(buf, 13, profile)
    midiCiDirectUint32At(buf, 18, dataSize)
    memcpy(buf, 22, data, dataSize)
}


// Property Exchange
fun midiCIPropertyGetCapabilities(
    buf: MutableList<Byte>, destination: Byte, isReply: Boolean,
    sourceMUID: Int, destinationMUID: Int, maxSupportedRequests: Byte
) {
    midiCIMessageCommon(
        buf, destination,
        if (isReply) CMIDI2_CI_SUB_ID_2_PROPERTY_CAPABILITIES_REPLY else CMIDI2_CI_SUB_ID_2_PROPERTY_CAPABILITIES_INQUIRY,
        1, sourceMUID, destinationMUID
    )
    buf[13] = maxSupportedRequests
}

// common to all of: has data & reply, get data & reply, set data & reply, subscribe & reply, notify
fun midiCIPropertyCommon(
    buf: MutableList<Byte>, destination: Byte, messageTypeSubId2: Byte,
    sourceMUID: Int, destinationMUID: Int,
    requestId: Byte, headerSize: Short, header: List<Byte>,
    numChunks: Short, chunkIndex: Short, dataSize: Short, data: List<Byte>
) {
    midiCIMessageCommon(buf, destination, messageTypeSubId2, 1, sourceMUID, destinationMUID)
    buf[13] = requestId
    midiCiDirectUint16At(buf, 14, headerSize)
    memcpy(buf, 16, header, headerSize.toInt())
    midiCiDirectUint16At(buf, 16 + headerSize, numChunks)
    midiCiDirectUint16At(buf, 18 + headerSize, chunkIndex)
    midiCiDirectUint16At(buf, 20 + headerSize, dataSize)
    memcpy(buf, 22 + headerSize, data, dataSize.toInt())
}

private fun memcpy(dst: MutableList<Byte>, dstOffset: Int, src: List<Byte>, size: Int) {
    for (i in 0 until size)
        dst[i + dstOffset] = src[i]
}
