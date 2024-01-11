package dev.atsushieno.ktmidi.ci

/**
 * Observable list of MIDI-CI Profiles
 */
class ObservableProfileList(private val pl: MutableList<MidiCIProfile>) {
    enum class ProfilesChange { Added, Removed }
    val profiles: List<MidiCIProfile>
        get() = pl

    fun add(profile: MidiCIProfile) {
        pl.removeAll { it.profile == profile.profile }
        pl.add(profile)
        profilesChanged.forEach { it(ProfilesChange.Added, profile) }
    }
    fun remove(profile: MidiCIProfile) {
        val items = profiles.filter { it.profile == profile.profile && it.address == profile.address }
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

    // Used by local profile, which could be updated to change the target channel (address)
    fun update(profile: MidiCIProfile, enabled: Boolean, address: Byte, numChannelsRequested: Short) {
        if (numChannelsRequested > 1)
            TODO("FIXME: implement")
        val oldAddress = profile.address
        profile.enabled = enabled
        profile.address = address
        profileUpdated.forEach { it(profile.profile, oldAddress, enabled, address, numChannelsRequested) }
    }

    fun getMatchingProfiles(address: Byte, enabled: Boolean) =
        profiles.filter { it.address == address && it.enabled == enabled }.map { it.profile }

    val profilesChanged = mutableListOf<(change: ProfilesChange, profile: MidiCIProfile) -> Unit>()
    val profileEnabledChanged = mutableListOf<(profile: MidiCIProfile, numChannelsRequested: Short) -> Unit>()
    val profileUpdated = mutableListOf<(profileId: MidiCIProfileId, oldAddress: Byte, newEnabled: Boolean, newAddress: Byte, numChannelsRequested: Short) -> Unit>()
}