package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.shl

object CIRetrieval {
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
}
