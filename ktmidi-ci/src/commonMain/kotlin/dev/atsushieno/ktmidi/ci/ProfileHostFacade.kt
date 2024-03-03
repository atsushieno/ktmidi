package dev.atsushieno.ktmidi.ci

/**
 * This class provides Profile Configuration features primarily to end-user app developers,
 * You can add or remove profiles, as well as enable or disable each of them.
 *
 * Some Profile Configuration features such as Profile Specific Data messaging is offered
 * at `MidiCIDevice`.
 *
 * Request handlers also invoke these members.
 */
class ProfileHostFacade(device: MidiCIDevice) {
    private val config by device::config
    private val messenger by device::messenger

    val profiles = ObservableProfileList(config.localProfiles)

    val profileDetailsEntries = mutableListOf<MidiCIProfileDetails>()

    // These events are invoked when it received Set Profile On/Off request from Initiator.
    val onProfileSet = mutableListOf<(profile: MidiCIProfile) -> Unit>()

    private fun setProfile(group: Byte, address: Byte, profile: MidiCIProfileId, numChannelsRequested: Short, enabled: Boolean): Pair<Boolean,Short> {
        val newEntry = MidiCIProfile(profile, group, address, enabled, numChannelsRequested)
        val existing = profiles.profiles.firstOrNull { it.profile == profile && it.address == address }
        var oldNumChannels: Short = 0
        if (existing != null) {
            if (existing.enabled == enabled)
                return Pair(enabled, existing.numChannelsRequested) // do not perform anything and return current state
            oldNumChannels = existing.numChannelsRequested
            profiles.remove(existing)
        }
        profiles.add(newEntry)
        onProfileSet.forEach { it(newEntry) }
        return Pair(enabled, oldNumChannels)
    }

    fun enableProfile(group: Byte, address: Byte, profile: MidiCIProfileId, numChannels: Short) {
        if (setProfile(group, address, profile, numChannels, true).first)
            messenger.sendSetProfileEnabled(group, address, profile, numChannels)
    }

    fun disableProfile(group: Byte, address: Byte, profile: MidiCIProfileId) {
        val result = setProfile(group, address, profile, 0, false)
        if (!result.first)
            messenger.sendSetProfileDisabled(group, address, profile, result.second)
    }

    fun addProfile(profile: MidiCIProfile) {
        profiles.add(profile)
        messenger.sendProfileAddedReport(profile)
        // MIDI-CI section 7.4:
        // > If the newly added Profile is enabled in the Device, then the Device shall then send a Profile Enabled Report
        // > message for that Profile immediately following the Profile Added Report.
        if (profile.enabled)
            messenger.sendSetProfileEnabled(profile.group, profile.address, profile.profile, profile.numChannelsRequested)
    }

    fun removeProfile(group: Byte, address: Byte, profileId: MidiCIProfileId) {
        // create a dummy entry...
        val profile = MidiCIProfile(profileId, group, address, false, 0)
        profiles.remove(profile)
        messenger.sendProfileRemovedReport(profile)
    }

    fun updateProfileTarget(
        profileId: MidiCIProfileId,
        oldAddress: Byte,
        newAddress: Byte,
        enabled: Boolean,
        numChannelsRequested: Short
    ) {
        val profile = profiles.profiles.first { it.address == oldAddress && it.profile == profileId }
        profiles.update(profile, enabled, newAddress, numChannelsRequested)
    }

    fun getProfileDetails(profile: MidiCIProfileId, target: Byte): List<Byte>? {
        val entry = profileDetailsEntries.firstOrNull { it.profile == profile && it.target == target }
        return entry?.data
    }
}