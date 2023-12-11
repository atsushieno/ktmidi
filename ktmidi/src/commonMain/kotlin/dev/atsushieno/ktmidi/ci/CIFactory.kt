package dev.atsushieno.ktmidi.ci

data class MidiCIProtocolTypeInfo(
    val type: Byte,
    val version: Byte,
    val extensions: Byte,
    val reserved1: Byte,
    val reserved2: Byte
)

data class MidiCIAckNakData(
    val source: Byte,
    val sourceMUID: Int,
    val destinationMUID: Int,
    val originalSubId: Byte,
    val statusCode: Byte,
    val statusData: Byte,
    val nakDetails: List<Byte>,
    val messageTextData: List<Byte>
)

// manufacture ID1,2,3 + manufacturer specific 1,2 ... or ... 0x7E, bank, number, version, level.
class MidiCIProfileId(val mid1_7e: Byte = 0x7E, val mid2_bank: Byte, val mid3_number: Byte, val msi1_version: Byte, val msi2_level: Byte)

object CIFactory {

    const val SUB_ID: Byte = 0xD

    const val SUB_ID_2_DISCOVERY_INQUIRY: Byte = 0x70
    const val SUB_ID_2_DISCOVERY_REPLY: Byte = 0x71
    const val SUB_ID_2_ENDPOINT_MESSAGE_INQUIRY: Byte = 0x72
    const val SUB_ID_2_ENDPOINT_MESSAGE_REPLY: Byte = 0x73
    const val SUB_ID_2_ACK: Byte = 0x7D
    const val SUB_ID_2_INVALIDATE_MUID: Byte = 0x7E
    const val SUB_ID_2_NAK: Byte = 0x7F
    const val SUB_ID_2_PROTOCOL_NEGOTIATION_INQUIRY: Byte = 0x10
    const val SUB_ID_2_PROTOCOL_NEGOTIATION_REPLY: Byte = 0x11
    const val SUB_ID_2_SET_NEW_PROTOCOL: Byte = 0x12
    const val SUB_ID_2_TEST_NEW_PROTOCOL_I2R: Byte = 0x13
    const val SUB_ID_2_TEST_NEW_PROTOCOL_R2I: Byte = 0x14
    const val SUB_ID_2_CONFIRM_NEW_PROTOCOL_ESTABLISHED: Byte = 0x15
    const val SUB_ID_2_PROFILE_INQUIRY: Byte = 0x20
    const val SUB_ID_2_PROFILE_INQUIRY_REPLY: Byte = 0x21
    const val SUB_ID_2_SET_PROFILE_ON: Byte = 0x22
    const val SUB_ID_2_SET_PROFILE_OFF: Byte = 0x23
    const val SUB_ID_2_PROFILE_ENABLED_REPORT: Byte = 0x24
    const val SUB_ID_2_PROFILE_DISABLED_REPORT: Byte = 0x25
    const val SUB_ID_2_PROFILE_ADDED_REPORT: Byte = 0x26
    const val SUB_ID_2_PROFILE_REMOVED_REPORT: Byte = 0x27
    const val SUB_ID_2_PROFILE_DETAILS_INQUIRY: Byte = 0x28
    const val SUB_ID_2_PROFILE_DETAILS_INQUIRY_REPLY: Byte = 0x29
    const val SUB_ID_2_PROFILE_SPECIFIC_DATA: Byte = 0x2F
    const val SUB_ID_2_PROPERTY_CAPABILITIES_INQUIRY: Byte = 0x30
    const val SUB_ID_2_PROPERTY_CAPABILITIES_REPLY: Byte = 0x31
    const val SUB_ID_2_PROPERTY_HAS_DATA_INQUIRY: Byte = 0x32
    const val SUB_ID_2_PROPERTY_HAS_DATA_REPLY: Byte = 0x33
    const val SUB_ID_2_PROPERTY_GET_DATA_INQUIRY: Byte = 0x34
    const val SUB_ID_2_PROPERTY_GET_DATA_REPLY: Byte = 0x35
    const val SUB_ID_2_PROPERTY_SET_DATA_INQUIRY: Byte = 0x36
    const val SUB_ID_2_PROPERTY_SET_DATA_REPLY: Byte = 0x37
    const val SUB_ID_2_PROPERTY_SUBSCRIBE: Byte = 0x38
    const val SUB_ID_2_PROPERTY_SUBSCRIBE_REPLY: Byte = 0x39
    const val SUB_ID_2_PROPERTY_NOTIFY: Byte = 0x3F

    const val PROTOCOL_NEGOTIATION_SUPPORTED = 2
    const val PROFILE_CONFIGURATION_SUPPORTED = 4
    const val PROPERTY_EXCHANGE_SUPPORTED = 8

    // Assumes the input value is already 7-bit encoded if required.
    fun midiCiDirectUint16At(dst: MutableList<Byte>, offset: Int, v: UShort) {
        dst[offset] = (v and 0xFFu).toByte()
        dst[offset + 1] = (v.toInt() shr 8 and 0xFF).toByte()
    }

    // Assumes the input value is already 7-bit encoded if required.
    fun midiCiDirectUint32At(dst: MutableList<Byte>, offset: Int, v: Int) {
        dst[offset] = (v and 0xFF).toByte()
        dst[offset + 1] = ((v shr 8) and 0xFF).toByte()
        dst[offset + 2] = ((v shr 16) and 0xFF).toByte()
        dst[offset + 3] = ((v shr 24) and 0xFF).toByte()
    }

    fun midiCI7bitInt14At(dst: MutableList<Byte>, offset: Int, v: UShort) {
        dst[offset] = (v and 0x7Fu).toByte()
        dst[offset + 1] = (v.toInt() shr 7 and 0x7F).toByte()
    }

    fun midiCI7bitInt21At(dst: MutableList<Byte>, offset: Int, v: Int) {
        dst[offset] = (v and 0x7F).toByte()
        dst[offset + 1] = ((v shr 7) and 0x7F).toByte()
        dst[offset + 2] = ((v shr 14) and 0x7F).toByte()
    }

    fun midiCI7bitInt28At(dst: MutableList<Byte>, offset: Int, v: Int) {
        dst[offset] = (v and 0x7F).toByte()
        dst[offset + 1] = ((v shr 7) and 0x7F).toByte()
        dst[offset + 2] = ((v shr 14) and 0x7F).toByte()
        dst[offset + 3] = ((v shr 21) and 0x7F).toByte()
    }

    fun midiCIMessageCommon(
        dst: MutableList<Byte>,
        deviceId: Byte, sysexSubId2: Byte, versionAndFormat: Byte, sourceMUID: Int, destinationMUID: Int
    ) {
        dst[0] = MidiCIConstants.UNIVERSAL_SYSEX
        dst[1] = deviceId
        dst[2] = MidiCIConstants.UNIVERSAL_SYSEX_SUB_ID_MIDI_CI
        dst[3] = sysexSubId2
        dst[4] = versionAndFormat
        midiCiDirectUint32At(dst, 5, sourceMUID)
        midiCiDirectUint32At(dst, 9, destinationMUID)
    }


// Discovery

    fun midiCIDiscoveryCommon(
        dst: MutableList<Byte>, sysexSubId2: Byte,
        versionAndFormat: Byte, sourceMUID: Int, destinationMUID: Int,
        deviceManufacturer3Bytes: Int, deviceFamily: UShort, deviceFamilyModelNumber: UShort,
        softwareRevisionLevel: Int, ciCategorySupported: Byte, receivableMaxSysExSize: Int,
        initiatorOutputPathId: Byte
    ) {
        midiCIMessageCommon(dst, 0x7F, sysexSubId2, versionAndFormat, sourceMUID, destinationMUID)
        midiCiDirectUint32At(
            dst, 13,
            deviceManufacturer3Bytes
        ) // the last byte is extraneous, but will be overwritten next.
        midiCiDirectUint16At(dst, 16, deviceFamily)
        midiCiDirectUint16At(dst, 18, deviceFamilyModelNumber)
        // LAMESPEC: Software Revision Level does not mention in which endianness this field is stored.
        midiCiDirectUint32At(dst, 20, softwareRevisionLevel)
        dst[24] = ciCategorySupported
        midiCiDirectUint32At(dst, 25, receivableMaxSysExSize)
        dst[29] = initiatorOutputPathId
    }

    fun midiCIDiscovery(
        dst: MutableList<Byte>,
        versionAndFormat: Byte, sourceMUID: Int,
        deviceManufacturer: Int, deviceFamily: UShort, deviceFamilyModelNumber: UShort,
        softwareRevisionLevel: Int, ciCategorySupported: Byte, receivableMaxSysExSize: Int,
        initiatorOutputPathId: Byte
    ) : List<Byte> {
        midiCIDiscoveryCommon(
            dst, SUB_ID_2_DISCOVERY_INQUIRY,
            versionAndFormat, sourceMUID, 0x7F7F7F7F,
            deviceManufacturer, deviceFamily, deviceFamilyModelNumber,
            softwareRevisionLevel, ciCategorySupported, receivableMaxSysExSize,
            initiatorOutputPathId
        )
        return dst.take(30)
    }

    fun midiCIDiscoveryReply(
        dst: MutableList<Byte>,
        versionAndFormat: Byte, sourceMUID: Int, destinationMUID: Int,
        deviceManufacturer: Int, deviceFamily: UShort, deviceFamilyModelNumber: UShort,
        softwareRevisionLevel: Int, ciCategorySupported: Byte, receivableMaxSysExSize: Int,
        initiatorOutputPathId: Byte, functionBlock: Byte
    ) : List<Byte> {
        midiCIDiscoveryCommon(
            dst, SUB_ID_2_DISCOVERY_REPLY,
            versionAndFormat, sourceMUID, destinationMUID,
            deviceManufacturer, deviceFamily, deviceFamilyModelNumber,
            softwareRevisionLevel, ciCategorySupported, receivableMaxSysExSize,
            initiatorOutputPathId
        )
        dst[30] = functionBlock
        return dst.take(31)
    }

    fun midiCIEndpointMessage(dst: MutableList<Byte>, versionAndFormat: Byte, sourceMUID: Int, destinationMUID: Int, status: Byte
    ) : List<Byte> {
        midiCIMessageCommon(dst, MidiCIConstants.FROM_FUNCTION_BLOCK, SUB_ID_2_ENDPOINT_MESSAGE_INQUIRY, versionAndFormat, sourceMUID, destinationMUID)
        dst[13] = status
        return dst.take(14)
    }

    fun midiCIEndpointMessageReply(dst: MutableList<Byte>, versionAndFormat: Byte, sourceMUID: Int, destinationMUID: Int, status: Byte, informationData: List<Byte>
    ) : List<Byte> {
        midiCIMessageCommon(dst, MidiCIConstants.FROM_FUNCTION_BLOCK, SUB_ID_2_ENDPOINT_MESSAGE_REPLY, versionAndFormat, sourceMUID, destinationMUID)
        dst[13] = status
        midiCI7bitInt14At(dst, 14, informationData.size.toUShort())
        memcpy(dst, 16, informationData, informationData.size)
        return dst.take(16 + informationData.size)
    }

    fun midiCIDiscoveryInvalidateMuid(
        dst: MutableList<Byte>,
        versionAndFormat: Byte, sourceMUID: Int, targetMUID: Int
    ) : List<Byte> {
        midiCIMessageCommon(dst, MidiCIConstants.FROM_FUNCTION_BLOCK, SUB_ID_2_INVALIDATE_MUID, versionAndFormat, sourceMUID, 0x7F7F7F7F)
        midiCiDirectUint32At(dst, 13, targetMUID)
        return dst.take(17)
    }

    fun midiCIDiscoveryNak(
        dst: MutableList<Byte>, deviceId: Byte,
        versionAndFormat: Byte, sourceMUID: Int, destinationMUID: Int
    ) : List<Byte> {
        midiCIMessageCommon(dst, deviceId, 0x7F, versionAndFormat, sourceMUID, destinationMUID)
        return dst.take(13)
    }


// Protocol Negotiation

    fun midiCIProtocolInfo(dst: MutableList<Byte>, offset: Int, info: MidiCIProtocolTypeInfo) {
        dst[offset] = info.type
        dst[offset + 1] = info.version
        dst[offset + 2] = info.extensions
        dst[offset + 3] = info.reserved1
        dst[offset + 4] = info.reserved2
    }

    fun midiCIProtocols(
        dst: MutableList<Byte>,
        offset: Int,
        protocolTypes: List<MidiCIProtocolTypeInfo>
    ) {
        dst[offset] = protocolTypes.size.toByte()
        protocolTypes.forEachIndexed { i, p ->
            midiCIProtocolInfo(dst, offset + 1 + i * 5, p)
        }
    }

    fun midiCIProtocolNegotiation(
        dst: MutableList<Byte>, isReply: Boolean,
        sourceMUID: Int, destinationMUID: Int,
        authorityLevel: Byte,
        protocolTypes: List<MidiCIProtocolTypeInfo>
    ) {
        midiCIMessageCommon(
            dst, 0x7F,
            if (isReply) SUB_ID_2_PROTOCOL_NEGOTIATION_REPLY else SUB_ID_2_PROTOCOL_NEGOTIATION_INQUIRY,
            1, sourceMUID, destinationMUID
        )
        dst[13] = authorityLevel
        midiCIProtocols(dst, 14, protocolTypes)
    }

    fun midiCIProtocolSet(
        dst: MutableList<Byte>,
        sourceMUID: Int, destinationMUID: Int,
        authorityLevel: Byte, newProtocolType: MidiCIProtocolTypeInfo
    ) {
        midiCIMessageCommon(
            dst, 0x7F,
            SUB_ID_2_SET_NEW_PROTOCOL,
            1, sourceMUID, destinationMUID
        )
        dst[13] = authorityLevel
        midiCIProtocolInfo(dst, 14, newProtocolType)
    }

    fun midiCIProtocolTest(
        dst: MutableList<Byte>,
        isInitiatorToResponder: Boolean,
        sourceMUID: Int, destinationMUID: Int,
        authorityLevel: Byte, testData48Bytes: List<Byte>
    ) {
        midiCIMessageCommon(
            dst, 0x7F,
            if (isInitiatorToResponder) SUB_ID_2_TEST_NEW_PROTOCOL_I2R else SUB_ID_2_TEST_NEW_PROTOCOL_R2I,
            1, sourceMUID, destinationMUID
        )
        dst[13] = authorityLevel
        memcpy(dst, 14, testData48Bytes, 48)
    }

    fun midiCIProtocolConfirmEstablished(
        dst: MutableList<Byte>,
        sourceMUID: Int, destinationMUID: Int,
        authorityLevel: Byte
    ) {
        midiCIMessageCommon(
            dst, 0x7F,
            SUB_ID_2_CONFIRM_NEW_PROTOCOL_ESTABLISHED,
            1, sourceMUID, destinationMUID
        )
        dst[13] = authorityLevel
    }


// Profile Configuration

    fun midiCIProfile(dst: MutableList<Byte>, offset: Int, info: MidiCIProfileId) {
        dst[offset] = info.mid1_7e
        dst[offset + 1] = info.mid2_bank
        dst[offset + 2] = info.mid3_number
        dst[offset + 3] = info.msi1_version
        dst[offset + 4] = info.msi2_level
    }

    fun midiCIProfileInquiry(
        dst: MutableList<Byte>, destinationChannelOr7F: Byte,
        sourceMUID: Int, destinationMUID: Int
    ) {
        midiCIMessageCommon(
            dst, destinationChannelOr7F,
            SUB_ID_2_PROFILE_INQUIRY,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, destinationMUID
        )
    }

    fun midiCIProfileInquiryReply(
        dst: MutableList<Byte>, source: Byte,
        sourceMUID: Int, destinationMUID: Int,
        enabledProfiles: List<MidiCIProfileId>,
        disabledProfiles: List<MidiCIProfileId>
    ) : List<Byte> {
        midiCIMessageCommon(
            dst, source,
            SUB_ID_2_PROFILE_INQUIRY_REPLY,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, destinationMUID
        )
        dst[13] = (enabledProfiles.size and 0x7F).toByte()
        dst[14] = ((enabledProfiles.size shr 7) and 0x7F).toByte()
        enabledProfiles.forEachIndexed { i, p ->
            midiCIProfile(dst, 15 + i * 5, p)
        }
        var pos: Int = 15 + enabledProfiles.size * 5
        dst[pos++] = (disabledProfiles.size and 0x7F).toByte()
        dst[pos++] = ((disabledProfiles.size shr 7) and 0x7F).toByte()
        disabledProfiles.forEachIndexed { i, p ->
            midiCIProfile(dst, pos + i * 5, p)
        }
        pos += disabledProfiles.size * 5
        return dst.take(pos)
    }

    fun midiCIProfileSet(
        dst: MutableList<Byte>, destination: Byte, turnOn: Boolean,
        sourceMUID: Int, destinationMUID: Int, profile: MidiCIProfileId
    ) {
        midiCIMessageCommon(
            dst, destination,
            if (turnOn) SUB_ID_2_SET_PROFILE_ON else SUB_ID_2_SET_PROFILE_OFF,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, destinationMUID
        )
        midiCIProfile(dst, 13, profile)
    }

    fun midiCIProfileAddedRemoved(
        dst: MutableList<Byte>, destination: Byte, isRemoved: Boolean,
        sourceMUID: Int, profile: MidiCIProfileId
    ) {
        midiCIMessageCommon(
            dst, destination,
            if (isRemoved) SUB_ID_2_PROFILE_REMOVED_REPORT else SUB_ID_2_PROFILE_ADDED_REPORT,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, 0x7F7F7F7F
        )
        midiCIProfile(dst, 13, profile)
    }

    fun midiCIProfileReport(
        dst: MutableList<Byte>, source: Byte, isEnabledReport: Boolean,
        sourceMUID: Int, profile: MidiCIProfileId
    ) {
        midiCIMessageCommon(
            dst, source,
            if (isEnabledReport) SUB_ID_2_PROFILE_ENABLED_REPORT else SUB_ID_2_PROFILE_DISABLED_REPORT,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, 0x7F7F7F7F
        )
        midiCIProfile(dst, 13, profile)
    }

    fun midiCIProfileSpecificData(
        dst: MutableList<Byte>, source: Byte,
        sourceMUID: Int, destinationMUID: Int, profile: MidiCIProfileId, dataSize: Int, data: MutableList<Byte>
    ) {
        midiCIMessageCommon(
            dst, source,
            SUB_ID_2_PROFILE_SPECIFIC_DATA,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, destinationMUID
        )
        midiCIProfile(dst, 13, profile)
        midiCiDirectUint32At(dst, 18, dataSize)
        memcpy(dst, 22, data, dataSize)
    }


    // Property Exchange
    fun midiCIPropertyGetCapabilities(
        dst: MutableList<Byte>, destination: Byte, isReply: Boolean,
        sourceMUID: Int, destinationMUID: Int, maxSimulutaneousRequests: Byte
    ) {
        midiCIMessageCommon(
            dst, destination,
            if (isReply) SUB_ID_2_PROPERTY_CAPABILITIES_REPLY else SUB_ID_2_PROPERTY_CAPABILITIES_INQUIRY,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, destinationMUID
        )
        dst[13] = maxSimulutaneousRequests
    }

    // common to all of: has data & reply, get data & reply, set data & reply, subscribe & reply, notify
    fun midiCIPropertyCommon(
        dst: MutableList<Byte>, destination: Byte, messageTypeSubId2: Byte,
        sourceMUID: Int, destinationMUID: Int,
        requestId: Byte, headerSize: UShort, header: List<Byte>,
        numChunks: UShort, chunkIndex: UShort, dataSize: UShort, data: List<Byte>
    ) {
        midiCIMessageCommon(dst, destination, messageTypeSubId2,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, destinationMUID)
        dst[13] = requestId
        midiCiDirectUint16At(dst, 14, headerSize)
        memcpy(dst, 16, header, headerSize.toInt())
        midiCiDirectUint16At(dst, 16 + headerSize.toInt(), numChunks)
        midiCiDirectUint16At(dst, 18 + headerSize.toInt(), chunkIndex)
        midiCiDirectUint16At(dst, 20 + headerSize.toInt(), dataSize)
        memcpy(dst, 22 + headerSize.toInt(), data, dataSize.toInt())
    }

    private fun memcpy(dst: MutableList<Byte>, dstOffset: Int, src: List<Byte>, size: Int) {
        for (i in 0 until size)
            dst[i + dstOffset] = src[i]
    }

    fun midiCIAckNak(dst: MutableList<Byte>, isNak: Boolean, data: MidiCIAckNakData) =
        midiCIAckNak(
            dst, isNak, data.source, MidiCIConstants.CI_VERSION_AND_FORMAT,
            data.sourceMUID, data.destinationMUID,
            data.originalSubId, data.statusCode, data.statusData,
            data.nakDetails, data.messageTextData)

    fun midiCIAckNak(
        dst: MutableList<Byte>,
        isNak: Boolean,
        sourceDeviceId: Byte,
        versionAndFormat: Byte,
        sourceMUID: Int,
        destinationMUID: Int,
        originalSubId: Byte,
        statusCode: Byte,
        statusData: Byte,
        nakDetails: List<Byte>,
        messageTextData: List<Byte>
    ): Int {
        midiCIMessageCommon(
            dst, sourceDeviceId, if (isNak) SUB_ID_2_NAK else SUB_ID_2_ACK,
            versionAndFormat, sourceMUID, destinationMUID)
        dst[13] = originalSubId
        dst[14] = statusCode
        dst[15] = statusData
        if (nakDetails.size == 5)
            memcpy(dst, 16, nakDetails, 5)
        dst[16] = (messageTextData.size % 0x80).toByte()
        dst[17] = (messageTextData.size / 0x80).toByte()
        if (messageTextData.isNotEmpty())
            memcpy(dst, 18, messageTextData, messageTextData.size)
        return 18 + messageTextData.size
    }

}