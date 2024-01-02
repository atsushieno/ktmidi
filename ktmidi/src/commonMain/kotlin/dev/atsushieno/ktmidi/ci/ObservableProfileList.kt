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
        val items = profiles.filter { it.profile.toString() == profile.toString() }
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

    // local profile could be updated to change the target channel (address)
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

    fun removeProfileTarget(address: Byte, profile: MidiCIProfileId) {
        val list = pl.filter { it.address == address && it.profile == profile }
        pl.removeAll(list)
        list.forEach { p ->
            profilesChanged.forEach { it(ProfilesChange.Removed, p) }
        }
    }

    val profilesChanged = mutableListOf<(change: ProfilesChange, profile: MidiCIProfile) -> Unit>()
    val profileEnabledChanged = mutableListOf<(profile: MidiCIProfile, numChannelsRequested: Short) -> Unit>()
    val profileUpdated = mutableListOf<(profileId: MidiCIProfileId, oldAddress: Byte, newEnabled: Boolean, newAddress: Byte, numChannelsRequested: Short) -> Unit>()
}