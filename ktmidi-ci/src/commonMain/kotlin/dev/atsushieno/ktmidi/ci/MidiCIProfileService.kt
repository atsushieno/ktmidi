package dev.atsushieno.ktmidi.ci

interface MidiCIProfileService {
    fun getProfileDetails(profile: MidiCIProfileId, target: Byte): List<Byte>?
}
