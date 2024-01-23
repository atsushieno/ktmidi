package dev.atsushieno.ktmidi.ci.profilecommonrules

import dev.atsushieno.ktmidi.ci.MidiCIConstants
import dev.atsushieno.ktmidi.ci.MidiCIProfileId
import dev.atsushieno.ktmidi.ci.MidiCIProfileService


object CommonProfileDetailsStandardTarget {
    const val NUM_MIDI_CHANNELS: Byte = 0
}

object ProfileSupportLevel {
    const val PARTIAL: Byte = 0
    const val MINIMUM_REQUIRED: Byte = 1
    const val HIGHEST_POSSIBLE: Byte = 0x7F
}

object DefaultControlChangesProfile {
    val profileIdForPartial = MidiCIProfileId(listOf(MidiCIConstants.STANDARD_DEFINED_PROFILE, 0x21, 0, 1, 0))

    val profileId = MidiCIProfileId(listOf(MidiCIConstants.STANDARD_DEFINED_PROFILE, 0x21, 0, 1, 1))
}

class CommonRulesProfileService : MidiCIProfileService {
    override fun getProfileDetails(profile: MidiCIProfileId, target: Byte): List<Byte>? {
        if (target != CommonProfileDetailsStandardTarget.NUM_MIDI_CHANNELS)
            return null
        // FIXME: implement
        return listOf()
    }

}