package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.MidiCC


object CommonProfileDetailsStandardTarget {
    const val NUM_MIDI_CHANNELS: Byte = 0
}

object ProfileSupportLevel {
    const val PARTIAL: Byte = 0
    const val MINIMUM_REQUIRED: Byte = 1
    const val HIGHEST_POSSIBLE: Byte = 0x7F
}

object DefaultControlChangesProfile {
    val profileIdForPartial = MidiCIProfileId(
        MidiCIConstants.STANDARD_DEFINED_PROFILE,
        0x21,
        0,
        1,
        0)

    val profileId = MidiCIProfileId(
        MidiCIConstants.STANDARD_DEFINED_PROFILE,
        0x21,
        0,
        1,
        1)

    val recommendedCCDefaults by lazy {
        mapOf(
            Pair(MidiCC.PORTAMENTO_TIME, 0),
            Pair(MidiCC.VOLUME, 100),
            Pair(MidiCC.PAN, 0x40),
            Pair(MidiCC.EXPRESSION, 127),
            Pair(MidiCC.HOLD, 0),
            Pair(MidiCC.PORTAMENTO_SWITCH, 0),
            Pair(MidiCC.SOSTENUTO, 0),
            Pair(MidiCC.LEGATO, 0),
        )
    }
}