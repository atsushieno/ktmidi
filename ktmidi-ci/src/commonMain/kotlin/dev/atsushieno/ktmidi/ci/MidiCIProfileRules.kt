package dev.atsushieno.ktmidi.ci

interface MidiCIProfileRules {
    fun getProfileDetails(profile: MidiCIProfileId, target: Byte): List<Byte>?
}
