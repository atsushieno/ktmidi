package dev.atsushieno.ktmidi.ci

/**
 * Observable list of MIDI-CI Profiles
 */
class ObservableProfileList {
    enum class ProfilesChange { Added, Removed }
    private val pl = mutableListOf<MidiCIProfile>()
    val profiles: List<MidiCIProfile>
        get() = pl

    fun add(profile: MidiCIProfile) {
        pl.removeAll { it.toString() == profile.toString() }
        pl.add(profile)
        profilesChanged.forEach { it(ProfilesChange.Added, profile) }
    }
    fun remove(profile: MidiCIProfileId) {
        val items = profiles.filter { it.toString() == profile.toString() }
        pl.removeAll(items)
        items.forEach { p ->
            profilesChanged.forEach { it(ProfilesChange.Removed, p) }
        }
    }

    fun setEnabled(enabled: Boolean, address: Byte, profileId: MidiCIProfileId, numChannelsRequested: Short) {
        val profile = profiles.firstOrNull { it.address == address && it.profile.toString() == profileId.toString() }
        if (profile != null) {
            if (numChannelsRequested > 1)
                TODO("FIXME: implement")
            profile.enabled = enabled
            profileEnabledChanged.forEach { it(profile, numChannelsRequested) }
        }
    }

    val profilesChanged = mutableListOf<(change: ProfilesChange, profile: MidiCIProfile) -> Unit>()
    val profileEnabledChanged = mutableListOf<(profile: MidiCIProfile, numChannelsRequested: Short) -> Unit>()
}