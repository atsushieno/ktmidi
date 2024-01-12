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
data class MidiCIProfileId(val manuId1OrStandard: Byte = 0x7E, val manuId2OrBank: Byte, val manuId3OrNumber: Byte, val specificInfoOrVersion: Byte, val specificInfoOrLevel: Byte) {
    override fun toString() =
        "${manuId1OrStandard.toString(16)}:${manuId2OrBank.toString(16)}:${manuId3OrNumber.toString(16)}:${specificInfoOrVersion.toString(16)}:${specificInfoOrLevel.toString(16)}"
}

data class MidiCIProfile(val profile: MidiCIProfileId, var address: Byte, var enabled: Boolean)

object CIFactory {
    // Assumes the input value is already 7-bit encoded if required.
    fun midiCiDirectInt16At(dst: MutableList<Byte>, offset: Int, v: Short) {
        dst[offset] = (v.toInt() and 0x7F).toByte()
        dst[offset + 1] = (v.toInt() shr 8 and 0x7F).toByte()
    }

    // Assumes the input value is already 7-bit encoded if required.
    fun midiCiDirectUint32At(dst: MutableList<Byte>, offset: Int, v: Int) {
        dst[offset] = (v and 0xFF).toByte()
        dst[offset + 1] = ((v shr 8) and 0xFF).toByte()
        dst[offset + 2] = ((v shr 16) and 0xFF).toByte()
        dst[offset + 3] = ((v shr 24) and 0xFF).toByte()
    }

    fun midiCI7bitInt14At(dst: MutableList<Byte>, offset: Int, v: Short) {
        dst[offset] = (v.toInt() and 0x7F).toByte()
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
        address: Byte, sysexSubId2: Byte, versionAndFormat: Byte, sourceMUID: Int, destinationMUID: Int
    ) {
        dst[0] = MidiCIConstants.UNIVERSAL_SYSEX
        dst[1] = address
        dst[2] = MidiCIConstants.SYSEX_SUB_ID_MIDI_CI
        dst[3] = sysexSubId2
        dst[4] = versionAndFormat
        midiCiDirectUint32At(dst, 5, sourceMUID)
        midiCiDirectUint32At(dst, 9, destinationMUID)
    }


// Discovery

    fun midiCIDiscoveryCommon(
        dst: MutableList<Byte>, sysexSubId2: Byte,
        versionAndFormat: Byte, sourceMUID: Int, destinationMUID: Int,
        deviceManufacturer3Bytes: Int, deviceFamily: Short, deviceFamilyModelNumber: Short,
        softwareRevisionLevel: Int, ciCategorySupported: Byte, receivableMaxSysExSize: Int,
        initiatorOutputPathId: Byte
    ) {
        midiCIMessageCommon(dst, MidiCIConstants.WHOLE_FUNCTION_BLOCK, sysexSubId2, versionAndFormat, sourceMUID, destinationMUID)
        midiCiDirectUint32At(
            dst, 13,
            deviceManufacturer3Bytes
        ) // the last byte is extraneous, but will be overwritten next.
        midiCiDirectInt16At(dst, 16, deviceFamily)
        midiCiDirectInt16At(dst, 18, deviceFamilyModelNumber)
        // LAMESPEC: Software Revision Level does not mention in which endianness this field is stored.
        midiCiDirectUint32At(dst, 20, softwareRevisionLevel)
        dst[24] = ciCategorySupported
        midiCiDirectUint32At(dst, 25, receivableMaxSysExSize)
        dst[29] = initiatorOutputPathId
    }

    fun midiCIDiscovery(
        dst: MutableList<Byte>,
        versionAndFormat: Byte, sourceMUID: Int,
        deviceManufacturer: Int, deviceFamily: Short, deviceFamilyModelNumber: Short,
        softwareRevisionLevel: Int, ciCategorySupported: Byte, receivableMaxSysExSize: Int,
        initiatorOutputPathId: Byte
    ) : List<Byte> {
        midiCIDiscoveryCommon(
            dst, CISubId2.DISCOVERY_INQUIRY,
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
        deviceManufacturer: Int, deviceFamily: Short, deviceFamilyModelNumber: Short,
        softwareRevisionLevel: Int, ciCategorySupported: Byte, receivableMaxSysExSize: Int,
        initiatorOutputPathId: Byte, functionBlock: Byte
    ) : List<Byte> {
        midiCIDiscoveryCommon(
            dst, CISubId2.DISCOVERY_REPLY,
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
        midiCIMessageCommon(dst, MidiCIConstants.WHOLE_FUNCTION_BLOCK, CISubId2.ENDPOINT_MESSAGE_INQUIRY, versionAndFormat, sourceMUID, destinationMUID)
        dst[13] = status
        return dst.take(14)
    }

    fun midiCIEndpointMessageReply(dst: MutableList<Byte>, versionAndFormat: Byte, sourceMUID: Int, destinationMUID: Int, status: Byte, informationData: List<Byte>
    ) : List<Byte> {
        midiCIMessageCommon(dst, MidiCIConstants.WHOLE_FUNCTION_BLOCK, CISubId2.ENDPOINT_MESSAGE_REPLY, versionAndFormat, sourceMUID, destinationMUID)
        dst[13] = status
        midiCI7bitInt14At(dst, 14, informationData.size.toShort())
        memcpy(dst, 16, informationData, informationData.size)
        return dst.take(16 + informationData.size)
    }

    fun midiCIDiscoveryInvalidateMuid(
        dst: MutableList<Byte>,
        versionAndFormat: Byte, sourceMUID: Int, targetMUID: Int
    ) : List<Byte> {
        midiCIMessageCommon(dst, MidiCIConstants.WHOLE_FUNCTION_BLOCK, CISubId2.INVALIDATE_MUID, versionAndFormat, sourceMUID, 0x7F7F7F7F)
        midiCiDirectUint32At(dst, 13, targetMUID)
        return dst.take(17)
    }

    fun midiCIDiscoveryNak(
        dst: MutableList<Byte>, address: Byte,
        versionAndFormat: Byte, sourceMUID: Int, destinationMUID: Int
    ) : List<Byte> {
        midiCIMessageCommon(dst, address, 0x7F, versionAndFormat, sourceMUID, destinationMUID)
        return dst.take(13)
    }

// Profile Configuration

    fun midiCIProfile(dst: MutableList<Byte>, offset: Int, info: MidiCIProfileId) {
        dst[offset] = info.manuId1OrStandard
        dst[offset + 1] = info.manuId2OrBank
        dst[offset + 2] = info.manuId3OrNumber
        dst[offset + 3] = info.specificInfoOrVersion
        dst[offset + 4] = info.specificInfoOrLevel
    }

    fun midiCIProfileInquiry(
        dst: MutableList<Byte>, address: Byte,
        sourceMUID: Int, destinationMUID: Int
    ) : List<Byte> {
        midiCIMessageCommon(
            dst, address,
            CISubId2.PROFILE_INQUIRY,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, destinationMUID
        )
        return dst.take(13)
    }

    fun midiCIProfileInquiryReply(
        dst: MutableList<Byte>, address: Byte,
        sourceMUID: Int, destinationMUID: Int,
        enabledProfiles: List<MidiCIProfileId>,
        disabledProfiles: List<MidiCIProfileId>
    ) : List<Byte> {
        midiCIMessageCommon(
            dst, address,
            CISubId2.PROFILE_INQUIRY_REPLY,
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
        dst: MutableList<Byte>, address: Byte, turnOn: Boolean,
        sourceMUID: Int, destinationMUID: Int, profile: MidiCIProfileId,
        numChannelsRequested: Short
    ) : List<Byte> {
        midiCIMessageCommon(
            dst, address,
            if (turnOn) CISubId2.SET_PROFILE_ON else CISubId2.SET_PROFILE_OFF,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, destinationMUID
        )
        midiCIProfile(dst, 13, profile)
        // new field in MIDI-CI v1.2
        midiCI7bitInt14At(dst, 18, numChannelsRequested)
        return dst.take(20)
    }

    fun midiCIProfileAddedRemoved(
        dst: MutableList<Byte>, address: Byte, isRemoved: Boolean,
        sourceMUID: Int, profile: MidiCIProfileId
    ) : List<Byte> {
        midiCIMessageCommon(
            dst, address,
            if (isRemoved) CISubId2.PROFILE_REMOVED_REPORT else CISubId2.PROFILE_ADDED_REPORT,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, 0x7F7F7F7F
        )
        midiCIProfile(dst, 13, profile)
        return dst.take(18)
    }

    fun midiCIProfileReport(
        dst: MutableList<Byte>, address: Byte, isEnabledReport: Boolean,
        sourceMUID: Int, profile: MidiCIProfileId
    ) : List<Byte> {
        midiCIMessageCommon(
            dst, address,
            if (isEnabledReport) CISubId2.PROFILE_ENABLED_REPORT else CISubId2.PROFILE_DISABLED_REPORT,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, 0x7F7F7F7F
        )
        midiCIProfile(dst, 13, profile)
        return dst.take(20)
    }

    fun midiCIProfileDetails(
        dst: MutableList<Byte>, address: Byte,
        sourceMUID: Int, destinationMUID: Int,
        profile: MidiCIProfileId, target: Byte): List<Byte> {
        midiCIMessageCommon(
            dst, address,
            CISubId2.PROFILE_DETAILS_INQUIRY,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, destinationMUID
        )
        midiCIProfile(dst, 13, profile)
        dst[18] = target
        return dst.take(19)
    }

    fun midiCIProfileSpecificData(
        dst: MutableList<Byte>, address: Byte,
        sourceMUID: Int, destinationMUID: Int, profile: MidiCIProfileId, dataSize: Int, data: MutableList<Byte>
    ) : List<Byte> {
        midiCIMessageCommon(
            dst, address,
            CISubId2.PROFILE_SPECIFIC_DATA,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, destinationMUID
        )
        midiCIProfile(dst, 13, profile)
        midiCiDirectUint32At(dst, 18, dataSize)
        memcpy(dst, 22, data, dataSize)
        return dst.take(22 + dataSize)
    }


    // Property Exchange
    fun midiCIPropertyGetCapabilities(
        dst: MutableList<Byte>, address: Byte, isReply: Boolean,
        sourceMUID: Int, destinationMUID: Int, maxSimulutaneousRequests: Byte
    ) : List<Byte> {
        midiCIMessageCommon(
            dst, address,
            if (isReply) CISubId2.PROPERTY_CAPABILITIES_REPLY else CISubId2.PROPERTY_CAPABILITIES_INQUIRY,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, destinationMUID
        )
        dst[13] = maxSimulutaneousRequests
        // since MIDI-CI 1.2
        dst[14] = MidiCIConstants.PROPERTY_EXCHANGE_MAJOR_VERSION
        dst[15] = MidiCIConstants.PROPERTY_EXCHANGE_MINOR_VERSION
        return dst.take(16)
    }

    // common to all of: has data & reply, get data & reply, set data & reply, subscribe & reply, notify
    fun midiCIPropertyCommon(
        dst: MutableList<Byte>, address: Byte, messageTypeSubId2: Byte,
        sourceMUID: Int, destinationMUID: Int,
        requestId: Byte, header: List<Byte>,
        numChunks: Short, chunkIndex: Short, data: List<Byte>
    ) {
        midiCIMessageCommon(dst, address, messageTypeSubId2,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, destinationMUID)
        dst[13] = requestId
        midiCI7bitInt14At(dst, 14, header.size.toShort())
        memcpy(dst, 16, header, header.size)
        midiCI7bitInt14At(dst, 16 + header.size, numChunks)
        midiCI7bitInt14At(dst, 18 + header.size, chunkIndex)
        midiCI7bitInt14At(dst, 20 + header.size, data.size.toShort())
        memcpy(dst, 22 + header.size, data, data.size)
    }

    private fun memcpy(dst: MutableList<Byte>, dstOffset: Int, src: List<Byte>, size: Int) {
        for (i in 0 until size)
            dst[i + dstOffset] = src[i]
    }

    fun midiCIPropertyPacketCommon(dst: MutableList<Byte>, subId: Byte, sourceMUID: Int, destinationMUID: Int,
                                    requestId: Byte, header: List<Byte>,
                                    numChunks: Short, chunkIndex1Based: Short,
                                    data: List<Byte>) : List<Byte> {
        midiCIPropertyCommon(dst, MidiCIConstants.WHOLE_FUNCTION_BLOCK, subId,
            sourceMUID, destinationMUID, requestId, header, numChunks, chunkIndex1Based, data)
        return dst.take(16 + header.size + 6 + data.size)
    }

    fun midiCIPropertyChunks(dst: MutableList<Byte>, maxDataLengthInPacket: Int, subId: Byte, sourceMUID: Int, destinationMUID: Int,
        requestId: Byte, header: List<Byte>, data: List<Byte>) : List<List<Byte>> {
        if (data.isEmpty())
            return listOf(midiCIPropertyPacketCommon(dst, subId, sourceMUID, destinationMUID, requestId, header,
                1, 1, data))

        val chunks = data.chunked(maxDataLengthInPacket)
        return chunks.mapIndexed { index, packetData ->
            midiCIPropertyPacketCommon(dst, subId, sourceMUID, destinationMUID, requestId, header,
                chunks.size.toShort(), (index + 1).toShort(), packetData)
        }
    }

    // Process Inquiry

    fun midiCIProcessInquiryCapabilities(
        dst: MutableList<Byte>, sourceMUID: Int, destinationMUID: Int
    ) : List<Byte> {
        midiCIMessageCommon(
            dst, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, CISubId2.PROCESS_INQUIRY_CAPABILITIES,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, destinationMUID
        )
        return dst.take(13)
    }

    fun midiCIProcessInquiryCapabilitiesReply(
        dst: MutableList<Byte>, sourceMUID: Int, destinationMUID: Int, features: Byte
    ) : List<Byte> {
        midiCIMessageCommon(
            dst, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, CISubId2.PROCESS_INQUIRY_CAPABILITIES_REPLY,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, destinationMUID
        )
        dst[13] = features
        return dst.take(14)
    }

    fun midiCIMidiMessageReport(
        dst: MutableList<Byte>, isRequest: Boolean, address: Byte, sourceMUID: Int, destinationMUID: Int,
        messageDataControl: Byte,
        systemMessages: Byte,
        channelControllerMessages: Byte,
        noteDataMessages: Byte
    ) : List<Byte> {
        midiCIMessageCommon(
            dst, address, if (isRequest) CISubId2.PROCESS_INQUIRY_MIDI_MESSAGE_REPORT else CISubId2.PROCESS_INQUIRY_MIDI_MESSAGE_REPORT_REPLY,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, destinationMUID
        )
        dst[13] = messageDataControl
        dst[14] = systemMessages
        dst[15] = 0 // reserved for other System Messages
        dst[16] = channelControllerMessages
        dst[17] = noteDataMessages
        return dst.take(18)
    }

    fun midiCIEndOfMidiMessage(
        dst: MutableList<Byte>, address: Byte, sourceMUID: Int, destinationMUID: Int
    ) : List<Byte> {
        midiCIMessageCommon(dst, address, CISubId2.PROCESS_INQUIRY_END_OF_MIDI_MESSAGE,
            MidiCIConstants.CI_VERSION_AND_FORMAT, sourceMUID, destinationMUID)
        return dst.take(13)
    }

    // ACK/NAK

    fun midiCIAckNak(dst: MutableList<Byte>, isNak: Boolean, data: MidiCIAckNakData) =
        midiCIAckNak(
            dst, isNak, data.source, MidiCIConstants.CI_VERSION_AND_FORMAT,
            data.sourceMUID, data.destinationMUID,
            data.originalSubId, data.statusCode, data.statusData,
            data.nakDetails, data.messageTextData)

    fun midiCIAckNak(
        dst: MutableList<Byte>,
        isNak: Boolean,
        address: Byte,
        versionAndFormat: Byte,
        sourceMUID: Int,
        destinationMUID: Int,
        originalSubId: Byte,
        statusCode: Byte,
        statusData: Byte,
        nakDetails: List<Byte>,
        messageTextData: List<Byte>
    ): List<Byte> {
        midiCIMessageCommon(
            dst, address, if (isNak) CISubId2.NAK else CISubId2.ACK,
            versionAndFormat, sourceMUID, destinationMUID)
        dst[13] = originalSubId
        dst[14] = statusCode
        dst[15] = statusData
        if (nakDetails.size == 5)
            memcpy(dst, 16, nakDetails, 5)
        dst[21] = (messageTextData.size % 0x80).toByte()
        dst[22] = (messageTextData.size / 0x80).toByte()
        if (messageTextData.isNotEmpty())
            memcpy(dst, 23, messageTextData, messageTextData.size)
        return dst.take(23 + messageTextData.size)
    }

}