package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.shl

object CIRetrieval {
    fun midiCIGetDeviceDetails(sysex: ByteArray, offset: Int) = DeviceDetails(
        sysex[offset + 14] + (sysex[offset + 15] shl 8) + (sysex[offset + 16] shl 16),
        (sysex[offset + 17] + (sysex[offset + 18] shl 8)).toShort(),
        (sysex[offset + 19] + (sysex[offset + 20] shl 8)).toShort(),
        sysex[offset + 21] + (sysex[offset + 22] shl 8) + (sysex[offset + 23] shl 16) + (sysex[offset + 24] shl 24))

    fun midiCIGetSourceMUID(sysex: ByteArray, offset: Int) =
        sysex[offset + 6] + (sysex[offset + 7] shl 8) + (sysex[offset + 8] shl 16) + (sysex[offset + 9] shl 24)

    private fun readSingleProtocol(sysex: ByteArray, offsetToProtocol: Int) =
        MidiCIProtocolTypeInfo(sysex[offsetToProtocol + 16], sysex[offsetToProtocol + 17], sysex[offsetToProtocol + 18], 0, 0)

    // common to "Initiate Protocol Negotiation" and "Reply To Initiate Protocol Negotiation"
    fun midiCIGetSupportedProtocols(sysex: ByteArray, offset: Int) : List<MidiCIProtocolTypeInfo> {
        val l = mutableListOf<MidiCIProtocolTypeInfo>()
        val n = sysex[offset + 15]
        (0 until n).forEach { i ->
            l.add(readSingleProtocol(sysex, offset + 16 + i * 5))
        }
        return l
    }

    fun midiCIGetNewProtocol(sysex: ByteArray, offset: Int) = readSingleProtocol(sysex, offset + 15)

    fun midiCIGetTestData(sysex: ByteArray, offset: Int) = sysex.drop(offset + 16).take(48)
}
