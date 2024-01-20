package dev.atsushieno.ktmidi.ci

/**
 * Observable list of MIDI-CI Profiles
 */
class ObservableProfileList(private val pl: MutableList<MidiCIProfile>) {
    enum class ProfilesChange { Added, Removed }
    val profiles: List<MidiCIProfile>
        get() = pl

    fun add(profile: MidiCIProfile) {
        if (pl.any { it.profile == profile.profile && it.address == profile.address })
            return // duplicate entry
        pl.add(profile)
        profilesChanged.forEach { it(ProfilesChange.Added, profile) }
    }
    fun remove(profile: MidiCIProfile) {
        val items = profiles.filter { it.profile == profile.profile && it.group == profile.group && it.address == profile.address }
        pl.removeAll(items)
        items.forEach { p ->
            profilesChanged.forEach { it(ProfilesChange.Removed, p) }
        }
    }

    fun setEnabled(enabled: Boolean, address: Byte, profileId: MidiCIProfileId, numChannelsRequested: Short) {
        val profile = profiles.firstOrNull { it.address == address && it.profile.toString() == profileId.toString() }
        if (profile != null) {
            profile.enabled = enabled
            profile.numChannelsRequested = numChannelsRequested
            profileEnabledChanged.forEach { it(profile) }
        }
    }

    // Used by local profile, which could be updated to change the target channel (address)
    fun update(profile: MidiCIProfile, enabled: Boolean, address: Byte, numChannelsRequested: Short) {
        val oldAddress = profile.address
        profile.enabled = enabled
        profile.address = address
        profile.numChannelsRequested = numChannelsRequested
        profileUpdated.forEach { it(profile.profile, oldAddress, enabled, address, numChannelsRequested) }
    }

    fun getMatchingProfiles(address: Byte, enabled: Boolean) =
        profiles.filter { it.address == address && it.enabled == enabled }.map { it.profile }

    val profilesChanged = mutableListOf<(change: ProfilesChange, profile: MidiCIProfile) -> Unit>()
    val profileEnabledChanged = mutableListOf<(profile: MidiCIProfile) -> Unit>()
    val profileUpdated = mutableListOf<(profileId: MidiCIProfileId, oldAddress: Byte, newEnabled: Boolean, newAddress: Byte, numChannelsRequested: Short) -> Unit>()
}