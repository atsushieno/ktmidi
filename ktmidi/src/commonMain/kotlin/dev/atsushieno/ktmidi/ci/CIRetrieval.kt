package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.shl

object CIRetrieval {
    /** retrieves destination channel or "whole MIDI Port" from a MIDI-CI sysex7 chunk.
     * The argument sysex bytestream is NOT specific to MIDI 1.0 bytestream and thus does NOT contain F0 and F7 (i.e. starts with 0xFE, xx, 0x0D...)
     */
    fun midiCIGetDestination(sysex: List<Byte>) = sysex[1]

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
        sysex[5] + (sysex[6] shl 8) + (sysex[7] shl 16) + (sysex[8] shl 24)

    /** retrieves destination MUID from a MIDI-CI sysex7 chunk.
     * The argument sysex bytestream is NOT specific to MIDI 1.0 bytestream and thus does NOT contain F0 and F7 (i.e. starts with 0xFE, xx, 0x0D...)
     */
    fun midiCIGetDestinationMUID(sysex: List<Byte>) =
        sysex[9] + (sysex[10] shl 8) + (sysex[11] shl 16) + (sysex[12] shl 24)

    /** retrieves a protocol type info from a MIDI-CI sysex7 chunk partial (from offset 0). */
    private fun readSingleProtocol(sysex: List<Byte>) =
        MidiCIProtocolTypeInfo(sysex[0], sysex[1], sysex[2], 0, 0)

    /** retrieves the list of supported protocol type infos from a MIDI-CI Initiate or Reply-To-Initiate Protocol Negotiation sysex7 chunk.
     * common to "Initiate Protocol Negotiation" and "Reply To Initiate Protocol Negotiation"
     * The argument sysex bytestream is NOT specific to MIDI 1.0 bytestream and thus does NOT contain F0 and F7 (i.e. starts with 0xFE, xx, 0x0D...)
     */
    fun midiCIGetSupportedProtocols(sysex: List<Byte>) : List<MidiCIProtocolTypeInfo> {
        val l = mutableListOf<MidiCIProtocolTypeInfo>()
        val n = sysex[14]
        (0 until n).forEach { i ->
            l.add(readSingleProtocol(sysex.drop(15 + i * 5)))
        }
        return l
    }

    /** retrieves the protocol type info from a MIDI-CI Set New Protocol sysex7 chunk.
     * common to "Initiate Protocol Negotiation" and "Reply To Initiate Protocol Negotiation"
     * The argument sysex bytestream is NOT specific to MIDI 1.0 bytestream and thus does NOT contain F0 and F7 (i.e. starts with 0xFE, xx, 0x0D...)
     */
    fun midiCIGetNewProtocol(sysex: List<Byte>) = readSingleProtocol(sysex.drop(14))

    /** retrieves the test data (of 48 bytes) from a MIDI-CI Test New Protocol sysex7 chunk.
     * The argument sysex bytestream is NOT specific to MIDI 1.0 bytestream and thus does NOT contain F0 and F7 (i.e. starts with 0xFE, xx, 0x0D...)
     */
    fun midiCIGetTestData(sysex: List<Byte>) = sysex.drop(14).take(48)

    /** retrieves enabled profiles and disabled profiles from a MIDI-CI Replt to Profile Inquiry chunk.
     * The argument sysex bytestream is NOT specific to MIDI 1.0 bytestream and thus does NOT contain F0 and F7 (i.e. starts with 0xFE, xx, 0x0D...)
     */
    fun midiCIGetProfileSet(sysex: List<Byte>): List<Pair<MidiCIProfileId, Boolean>> = mutableListOf<Pair<MidiCIProfileId, Boolean>>().apply {
        val numEnabled = sysex[7] + (sysex[8] shl 7)
        (0 until numEnabled).forEach { i ->
            this.add(Pair(midiCIGetProfileIdEntry(sysex, 9 + i * 5), true))
        }
        val pos = 9 + numEnabled * 5
        val numDisabled = sysex[pos] + (sysex[pos + 1] shl 7)
        (0 until numDisabled).forEach { i ->
            this.add(Pair(midiCIGetProfileIdEntry(sysex, pos + 2 + i * 5), false))
        }
    }

    /** retrieves the profile ID from a MIDI-CI Set Profile On/Off, Profile Enabled/Disabled Report, or Profile Specific Data chunk.
     * The argument sysex bytestream is NOT specific to MIDI 1.0 bytestream and thus does NOT contain F0 and F7 (i.e. starts with 0xFE, xx, 0x0D...)
     */
    fun midiCIGetProfileId(sysex: List<Byte>) = midiCIGetProfileIdEntry(sysex, 13)

    private fun midiCIGetProfileIdEntry(sysex: List<Byte>, offset: Int) =
        MidiCIProfileId(sysex[offset], sysex[offset + 1], sysex[offset + 2], sysex[offset + 3], sysex[offset + 4])

    fun midiCIGetProfileSpecificDataSize(sysex: List<Byte>) =
        sysex[17] + (sysex[18] shl 8) + (sysex[19] shl 16) + (sysex[20] shl 24)

    fun midiCIGetMaxPropertyRequests(sysex: List<Byte>) = sysex[13]
}
