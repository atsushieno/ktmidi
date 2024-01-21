package dev.atsushieno.ktmidi.ci

import kotlinx.serialization.Serializable

// manufacture ID1,2,3 + manufacturer specific 1,2 ... or ... 0x7E, bank, number, version, level.
@Serializable
data class MidiCIProfileId(val manuId1OrStandard: Byte = 0x7E, val manuId2OrBank: Byte, val manuId3OrNumber: Byte, val specificInfoOrVersion: Byte, val specificInfoOrLevel: Byte) {
    override fun toString() =
        "${manuId1OrStandard.toString(16)}:${manuId2OrBank.toString(16)}:${manuId3OrNumber.toString(16)}:${specificInfoOrVersion.toString(16)}:${specificInfoOrLevel.toString(16)}"
}

@Serializable
// Do not use data class. Comparing enabled and numChannelsRequested does not make sense.
class MidiCIProfile(val profile: MidiCIProfileId, var group: Byte, var address: Byte, var enabled: Boolean, var numChannelsRequested: Short)