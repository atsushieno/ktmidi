package dev.atsushieno.ktmidi.ci

import kotlinx.serialization.Serializable

// manufacture ID1,2,3 + manufacturer specific 1,2 ... or ... 0x7E, bank, number, version, level.
@Serializable
data class MidiCIProfileId(val bytes: List<Byte>) {
    init {
        if (bytes.size != 5)
            throw IllegalArgumentException("bytes.size must be 5")
    }
    override fun toString() = bytes.map { it.toString(16) }.joinToString(":")
}

@Serializable
// Do not use data class. Comparing enabled and numChannelsRequested does not make sense.
class MidiCIProfile(val profile: MidiCIProfileId, var group: Byte, var address: Byte, var enabled: Boolean, var numChannelsRequested: Short)

// Used for Profile Details Inquiry
data class MidiCIProfileDetails(val profile: MidiCIProfileId, val target: Byte, val data: List<Byte>)
