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

class cmidi2_ci_protocol_type_info(
    val type: Byte,
    val version: Byte,
    val extensions: Byte,
    val reserved1: Byte,
    val reserved2: Byte
)

class cmidi2_profile_id(
    val fixed_7e: Byte,
    val bank: Byte,
    val number: Byte,
    val version: Byte,
    val level: Byte
)

// Assumes the input value is already 7-bit encoded if required.
fun cmidi2_ci_direct_uint16_at(buf: MutableList<Byte>, offset: Int, v: Short) {
    buf[offset] = (v and 0xFF).toByte()
    buf[offset + 1] = (v.toInt() shr 8 and 0xFF).toByte()
}

// Assumes the input value is already 7-bit encoded if required.
fun cmidi2_ci_direct_uint32_at(buf: MutableList<Byte>, offset: Int, v: Int) {
    buf[offset] = (v and 0xFF).toByte()
    buf[offset + 1] = (v shr 8 and 0xFF).toByte()
    buf[offset + 2] = (v shr 16 and 0xFF).toByte()
    buf[offset + 3] = (v shr 24 and 0xFF).toByte()
}

fun cmidi2_ci_7bit_int14_at(buf: MutableList<Byte>, offset: Int, v: Short) {
    buf[offset] = (v and 0x7F).toByte()
    buf[offset + 1] = (v.toInt() shr 7 and 0x7F).toByte()
}

fun cmidi2_ci_7bit_int21_at(buf: MutableList<Byte>, offset: Int, v: Int) {
    buf[offset] = (v and 0x7F).toByte()
    buf[offset + 1] = (v shr 7 and 0x7F).toByte()
    buf[offset + 2] = (v shr 14 and 0x7F).toByte()
}

fun cmidi2_ci_7bit_int28_at(buf: MutableList<Byte>, offset: Int, v: Int) {
    buf[offset] = (v and 0x7F).toByte()
    buf[offset + 1] = (v shr 7 and 0x7F).toByte()
    buf[offset + 2] = (v shr 14 and 0x7F).toByte()
    buf[offset + 3] = (v shr 21 and 0x7F).toByte()
}

fun cmidi2_ci_message_common(
    buf: MutableList<Byte>,
    destination: Byte, sysexSubId2: Byte, versionAndFormat: Byte, sourceMUID: Int, destinationMUID: Int
) {
    buf[0] = 0x7E
    buf[1] = destination
    buf[2] = 0xD
    buf[3] = sysexSubId2
    buf[4] = versionAndFormat
    cmidi2_ci_direct_uint32_at(buf, 5, sourceMUID)
    cmidi2_ci_direct_uint32_at(buf, 9, destinationMUID)
}


// Discovery

fun cmidi2_ci_discovery_common(
    buf: MutableList<Byte>, sysexSubId2: Byte,
    versionAndFormat: Byte, sourceMUID: Int, destinationMUID: Int,
    deviceManufacturer3Bytes: Int, deviceFamily: Short, deviceFamilyModelNumber: Short,
    softwareRevisionLevel: Int, ciCategorySupported: Byte, receivableMaxSysExSize: Int
) {
    cmidi2_ci_message_common(buf, 0x7F, sysexSubId2, versionAndFormat, sourceMUID!!, destinationMUID!!)
    cmidi2_ci_direct_uint32_at(
        buf, 13,
        deviceManufacturer3Bytes.toInt()
    ) // the last byte is extraneous, but will be overwritten next.
    cmidi2_ci_direct_uint16_at(buf, 16, deviceFamily!!)
    cmidi2_ci_direct_uint16_at(buf, 18, deviceFamilyModelNumber!!)
    // LAMESPEC: Software Revision Level does not mention in which endianness this field is stored.
    cmidi2_ci_direct_uint32_at(buf, 20, softwareRevisionLevel.toInt())
    buf[24] = ciCategorySupported
    cmidi2_ci_direct_uint32_at(buf, 25, receivableMaxSysExSize.toInt())
}

fun cmidi2_ci_discovery(
    buf: MutableList<Byte>,
    versionAndFormat: Byte, sourceMUID: Int,
    deviceManufacturer: Int, deviceFamily: Short, deviceFamilyModelNumber: Short,
    softwareRevisionLevel: Int, ciCategorySupported: Byte, receivableMaxSysExSize: Int
) {
    cmidi2_ci_discovery_common(
        buf, CMIDI2_CI_SUB_ID_2_DISCOVERY_INQUIRY,
        versionAndFormat, sourceMUID, 0x7F7F7F7F,
        deviceManufacturer, deviceFamily, deviceFamilyModelNumber,
        softwareRevisionLevel, ciCategorySupported, receivableMaxSysExSize
    )
}

fun cmidi2_ci_discovery_reply(
    buf: MutableList<Byte>,
    versionAndFormat: Byte, sourceMUID: Int, destinationMUID: Int,
    deviceManufacturer: Int, deviceFamily: Short, deviceFamilyModelNumber: Short,
    softwareRevisionLevel: Int, ciCategorySupported: Byte, receivableMaxSysExSize: Int
) {
    cmidi2_ci_discovery_common(
        buf, CMIDI2_CI_SUB_ID_2_DISCOVERY_REPLY,
        versionAndFormat, sourceMUID, destinationMUID,
        deviceManufacturer, deviceFamily, deviceFamilyModelNumber,
        softwareRevisionLevel, ciCategorySupported, receivableMaxSysExSize
    )
}

fun cmidi2_ci_discovery_invalidate_muid(
    buf: MutableList<Byte>,
    versionAndFormat: Byte, sourceMUID: Int, targetMUID: Int
) {
    cmidi2_ci_message_common(buf, 0x7F, 0x7E, versionAndFormat, sourceMUID, 0x7F7F7F7F)
    cmidi2_ci_direct_uint32_at(buf, 13, targetMUID)
}

fun cmidi2_ci_discovery_nak(
    buf: MutableList<Byte>, deviceId: Byte,
    versionAndFormat: Byte, sourceMUID: Int, destinationMUID: Int
) {
    cmidi2_ci_message_common(buf, deviceId, 0x7F, versionAndFormat, sourceMUID, destinationMUID)
}


// Protocol Negotiation

fun cmidi2_ci_protocol_info(buf: MutableList<Byte>, offset: Int, info: cmidi2_ci_protocol_type_info) {
    buf[offset] = info.type
    buf[offset + 1] = info.version
    buf[offset + 2] = info.extensions
    buf[offset + 3] = info.reserved1
    buf[offset + 4] = info.reserved2
}

fun cmidi2_ci_protocols(
    buf: MutableList<Byte>,
    offset: Int,
    numSupportedProtocols: Byte,
    protocolTypes: MutableList<cmidi2_ci_protocol_type_info>
) {
    buf[offset] = numSupportedProtocols
    for (i in 0 until numSupportedProtocols.toInt())
        cmidi2_ci_protocol_info(buf, offset + 1 + i * 5, protocolTypes[i])
}

fun cmidi2_ci_protocol_negotiation(
    buf: MutableList<Byte>, isReply: Boolean,
    sourceMUID: Int, destinationMUID: Int,
    authorityLevel: Byte,
    numSupportedProtocols: Byte, protocolTypes: MutableList<cmidi2_ci_protocol_type_info>
) {
    cmidi2_ci_message_common(
        buf, 0x7F,
        if (isReply) CMIDI2_CI_SUB_ID_2_PROTOCOL_NEGOTIATION_REPLY else CMIDI2_CI_SUB_ID_2_PROTOCOL_NEGOTIATION_INQUIRY,
        1, sourceMUID, destinationMUID
    )
    buf[13] = authorityLevel
    cmidi2_ci_protocols(buf, 14, numSupportedProtocols, protocolTypes)
}

fun cmidi2_ci_protocol_set(
    buf: MutableList<Byte>,
    sourceMUID: Int, destinationMUID: Int,
    authorityLevel: Byte, newProtocolType: cmidi2_ci_protocol_type_info
) {
    cmidi2_ci_message_common(
        buf, 0x7F,
        CMIDI2_CI_SUB_ID_2_SET_NEW_PROTOCOL,
        1, sourceMUID, destinationMUID
    )
    buf[13] = authorityLevel
    cmidi2_ci_protocol_info(buf, 14, newProtocolType)
}

fun cmidi2_ci_protocol_test(
    buf: MutableList<Byte>,
    isInitiatorToResponder: Boolean,
    sourceMUID: Int, destinationMUID: Int,
    authorityLevel: Byte, testData48Bytes: MutableList<Byte>
) {
    cmidi2_ci_message_common(
        buf, 0x7F,
        if (isInitiatorToResponder) CMIDI2_CI_SUB_ID_2_TEST_NEW_PROTOCOL_I2R else CMIDI2_CI_SUB_ID_2_TEST_NEW_PROTOCOL_R2I,
        1, sourceMUID, destinationMUID
    )
    buf[13] = authorityLevel
    memcpy(buf, 14, testData48Bytes, 48)
}

fun cmidi2_ci_protocol_confirm_established(
    buf: MutableList<Byte>,
    sourceMUID: Int, destinationMUID: Int,
    authorityLevel: Byte
) {
    cmidi2_ci_message_common(
        buf, 0x7F,
        CMIDI2_CI_SUB_ID_2_CONFIRM_NEW_PROTOCOL_ESTABLISHED,
        1, sourceMUID, destinationMUID
    )
    buf[13] = authorityLevel
}


// Profile Configuration

fun cmidi2_ci_profile(buf: MutableList<Byte>, offset: Int, info: cmidi2_profile_id) {
    buf[offset] = info.fixed_7e
    buf[offset + 1] = info.bank
    buf[offset + 2] = info.number
    buf[offset + 3] = info.version
    buf[offset + 4] = info.level
}

fun cmidi2_ci_profile_inquiry(
    buf: MutableList<Byte>, source: Byte,
    sourceMUID: Int, destinationMUID: Int
) {
    cmidi2_ci_message_common(
        buf, source,
        CMIDI2_CI_SUB_ID_2_PROFILE_INQUIRY,
        1, sourceMUID, destinationMUID
    )
}

fun cmidi2_ci_profile_inquiry_reply(
    buf: MutableList<Byte>, source: Byte,
    sourceMUID: Int, destinationMUID: Int,
    numEnabledProfiles: Byte, enabledProfiles: MutableList<cmidi2_profile_id>,
    numDisabledProfiles: Byte, disabledProfiles: MutableList<cmidi2_profile_id>
) {
    cmidi2_ci_message_common(
        buf, source,
        CMIDI2_CI_SUB_ID_2_PROFILE_INQUIRY_REPLY,
        1, sourceMUID, destinationMUID
    )
    buf[13] = numEnabledProfiles
    for (i in 0 until numEnabledProfiles)
        cmidi2_ci_profile(buf, 14 + i * 5, enabledProfiles[i])
    var pos: Int = 14 + numEnabledProfiles * 5
    buf[pos++] = numDisabledProfiles
    for (i in 0 until numDisabledProfiles)
        cmidi2_ci_profile(buf, pos + i * 5, disabledProfiles[i])
}

fun cmidi2_ci_profile_set(
    buf: MutableList<Byte>, destination: Byte, turnOn: Boolean,
    sourceMUID: Int, destinationMUID: Int, profile: cmidi2_profile_id
) {
    cmidi2_ci_message_common(
        buf, destination,
        if (turnOn) CMIDI2_CI_SUB_ID_2_SET_PROFILE_ON else CMIDI2_CI_SUB_ID_2_SET_PROFILE_OFF,
        1, sourceMUID, destinationMUID
    )
    cmidi2_ci_profile(buf, 13, profile)
}

fun cmidi2_ci_profile_report(
    buf: MutableList<Byte>, source: Byte, isEnabledReport: Boolean,
    sourceMUID: Int, profile: cmidi2_profile_id
) {
    cmidi2_ci_message_common(
        buf, source,
        if (isEnabledReport) CMIDI2_CI_SUB_ID_2_PROFILE_ENABLED_REPORT else CMIDI2_CI_SUB_ID_2_PROFILE_DISABLED_REPORT,
        1, sourceMUID, 0x7F7F7F7F
    )
    cmidi2_ci_profile(buf, 13, profile)
}

fun cmidi2_ci_profile_specific_data(
    buf: MutableList<Byte>, source: Byte,
    sourceMUID: Int, destinationMUID: Int, profile: cmidi2_profile_id, dataSize: Int, data: MutableList<Byte>
) {
    cmidi2_ci_message_common(
        buf, source,
        CMIDI2_CI_SUB_ID_2_PROFILE_SPECIFIC_DATA,
        1, sourceMUID, destinationMUID
    )
    cmidi2_ci_profile(buf, 13, profile)
    cmidi2_ci_direct_uint32_at(buf, 18, dataSize)
    memcpy(buf, 22, data, dataSize)
}


// Property Exchange
fun cmidi2_ci_property_get_capabilities(
    buf: MutableList<Byte>, destination: Byte, isReply: Boolean,
    sourceMUID: Int, destinationMUID: Int, maxSupportedRequests: Byte
) {
    cmidi2_ci_message_common(
        buf, destination,
        if (isReply) CMIDI2_CI_SUB_ID_2_PROPERTY_CAPABILITIES_REPLY else CMIDI2_CI_SUB_ID_2_PROPERTY_CAPABILITIES_INQUIRY,
        1, sourceMUID, destinationMUID
    )
    buf[13] = maxSupportedRequests
}

// common to all of: has data & reply, get data & reply, set data & reply, subscribe & reply, notify
fun cmidi2_ci_property_common(
    buf: MutableList<Byte>, destination: Byte, messageTypeSubId2: Byte,
    sourceMUID: Int, destinationMUID: Int,
    requestId: Byte, headerSize: Short, header: List<Byte>,
    numChunks: Short, chunkIndex: Short, dataSize: Short, data: List<Byte>
) {
    cmidi2_ci_message_common(buf, destination, messageTypeSubId2, 1, sourceMUID, destinationMUID)
    buf[13] = requestId
    cmidi2_ci_direct_uint16_at(buf, 14, headerSize)
    memcpy(buf, 16, header, headerSize.toInt())
    cmidi2_ci_direct_uint16_at(buf, 16 + headerSize, numChunks)
    cmidi2_ci_direct_uint16_at(buf, 18 + headerSize, chunkIndex)
    cmidi2_ci_direct_uint16_at(buf, 20 + headerSize, dataSize)
    memcpy(buf, 22 + headerSize, data, dataSize.toInt())
}

private fun memcpy(dst: MutableList<Byte>, dstOffset: Int, src: List<Byte>, size: Int) {
    for (i in 0 until size)
        dst[i + dstOffset] = src[i]
}
