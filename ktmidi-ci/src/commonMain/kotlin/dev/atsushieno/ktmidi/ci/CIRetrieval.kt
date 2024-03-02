package dev.atsushieno.ktmidi.ci

object CIRetrieval {
    /** retrieves destination channel, "whole MIDI Port", or "the Function block" from a MIDI-CI sysex7 chunk.
     * The argument sysex bytestream is NOT specific to MIDI 1.0 bytestream and thus does NOT contain F0 and F7 (i.e. starts with 0xFE, xx, 0x0D...)
     */
    fun midiCIGetAddressing(sysex: List<Byte>) = if (sysex.size > 1) sysex[1] else 0

    /** retrieves device details (manufacturer, version, etc.) from a MIDI-CI sysex7 chunk.
     * The argument sysex bytestream is NOT specific to MIDI 1.0 bytestream and thus does NOT contain F0 and F7 (i.e. starts with 0xFE, xx, 0x0D...)
     */
    fun midiCIGetDeviceDetails(sysex: List<Byte>) = DeviceDetails(
        sysex[13] + (sysex[14] shl 8) + (sysex[15] shl 16),
        (sysex[16] + (sysex[17] shl 8)).toShort(),
        (sysex[18] + (sysex[19] shl 8)).toShort(),
        sysex[20] + (sysex[21] shl 8) + (sysex[22] shl 16) + (sysex[23] shl 24))

    /** retrieves source MUID from a MIDI-CI sysex7 chunk.
     * The argument sysex bytestream is NOT specific to MIDI 1.0 bytestream and thus does NOT contain F0 and F7 (i.e. starts with 0xFE, xx, 0x0D...)
     */
    fun midiCIGetSourceMUID(sysex: List<Byte>) =
        if (sysex.size > 8) sysex[5] + (sysex[6] shl 8) + (sysex[7] shl 16) + (sysex[8] shl 24) else 0

    /** retrieves destination MUID from a MIDI-CI sysex7 chunk.
     * The argument sysex bytestream is NOT specific to MIDI 1.0 bytestream and thus does NOT contain F0 and F7 (i.e. starts with 0xFE, xx, 0x0D...)
     */
    fun midiCIGetDestinationMUID(sysex: List<Byte>) =
        if (sysex.size > 12) sysex[9] + (sysex[10] shl 8) + (sysex[11] shl 16) + (sysex[12] shl 24) else 0

    /** retrieves source MUID from a MIDI-CI sysex7 chunk.
     * The argument sysex bytestream is NOT specific to MIDI 1.0 bytestream and thus does NOT contain F0 and F7 (i.e. starts with 0xFE, xx, 0x0D...)
     */
    fun midiCIGetMUIDToInvalidate(sysex: List<Byte>) =
        sysex[13] + (sysex[14] shl 8) + (sysex[15] shl 16) + (sysex[16] shl 24)

    fun midiCIMaxSysExSize(sysex: List<Byte>) =
        sysex[25] + (sysex[26] shl 8) + (sysex[27] shl 16) + (sysex[28] shl 24)

    /** retrieves enabled profiles and disabled profiles from a MIDI-CI Replt to Profile Inquiry chunk.
     * The argument sysex bytestream is NOT specific to MIDI 1.0 bytestream and thus does NOT contain F0 and F7 (i.e. starts with 0xFE, xx, 0x0D...)
     */
    fun midiCIGetProfileSet(sysex: List<Byte>): List<Pair<MidiCIProfileId, Boolean>> = mutableListOf<Pair<MidiCIProfileId, Boolean>>().apply {
        val numEnabled = sysex[13] + (sysex[14] shl 7)
        (0 until numEnabled).forEach { i ->
            this.add(Pair(midiCIGetProfileIdEntry(sysex, 15 + i * 5), true))
        }
        val pos = 15 + numEnabled * 5
        val numDisabled = sysex[pos] + (sysex[pos + 1] shl 7)
        (0 until numDisabled).forEach { i ->
            this.add(Pair(midiCIGetProfileIdEntry(sysex, pos + 2 + i * 5), false))
        }
    }

    /** retrieves the profile ID from a MIDI-CI Set Profile On/Off, Profile Enabled/Disabled Report, or Profile Specific Data chunk.
     * The argument sysex bytestream is NOT specific to MIDI 1.0 bytestream and thus does NOT contain F0 and F7 (i.e. starts with 0xFE, xx, 0x0D...)
     */
    fun midiCIGetProfileId(sysex: List<Byte>) = midiCIGetProfileIdEntry(sysex, 13)

    fun midiCIGetProfileEnabledChannels(sysex: List<Byte>) = sysex[18]  + (sysex[19] shl 7)

    private fun midiCIGetProfileIdEntry(sysex: List<Byte>, offset: Int) =
        MidiCIProfileId(sysex.drop(offset).take(5))

    fun midiCIGetProfileSpecificDataSize(sysex: List<Byte>) =
        sysex[18] + (sysex[19] shl 8) + (sysex[20] shl 16) + (sysex[21] shl 24)

    fun midiCIGetMaxPropertyRequests(sysex: List<Byte>) = sysex[13]
    fun midiCIGetPropertyHeader(sysex: List<Byte>): List<Byte> {
        val size = sysex[14] + (sysex[15] shl 7)
        return sysex.drop(16).take(size)
    }
    fun midiCIGetPropertyBodyInThisChunk(sysex: List<Byte>): List<Byte> {
        val headerSize = sysex[14] + (sysex[15] shl 7)
        val index = 20 + headerSize
        val bodySize = sysex[index] + (sysex[index + 1] shl 7)
        return sysex.drop(22 + headerSize).take(bodySize)
    }

    fun midiCIGetPropertyTotalChunks(sysex: List<Byte>): Short {
        val headerSize = sysex[14] + (sysex[15] shl 7)
        val index = 16 + headerSize
        return (sysex[index] + (sysex[index + 1] shl 7)).toShort()
    }

    fun midiCIGetPropertyChunkIndex(sysex: List<Byte>): Short {
        val headerSize = sysex[14] + (sysex[15] shl 7)
        val index = 18 + headerSize
        return (sysex[index] + (sysex[index + 1] shl 7)).toShort()
    }
}
