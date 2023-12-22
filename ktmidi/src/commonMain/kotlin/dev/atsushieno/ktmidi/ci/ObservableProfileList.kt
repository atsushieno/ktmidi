package dev.atsushieno.ktmidi.ci

/**
 * Observable list of MIDI-CI Profiles
 */
class ObservableProfileList {
    enum class ProfilesChange { Added, Removed }
    private val profiles = mutableListOf<Pair<MidiCIProfileId,Boolean>>()
    val enabledProfiles: List<MidiCIProfileId>
        get() = profiles.filter { it.second }.map {it.first }
    val disabledProfiles: List<MidiCIProfileId>
        get() = profiles.filter { !it.second }.map {it.first }

    fun add(profile: MidiCIProfileId, enabled: Boolean) {
        profiles.removeAll { it.first.toString() == profile.toString() }
        profiles.add(Pair(profile, enabled))
        profilesChanged.forEach { it(ProfilesChange.Added, profile, enabled) }
    }
    fun remove(profile: MidiCIProfileId) {
        // FIXME: there may be better equality comparison...
        val items = profiles.filter { it.first.toString() == profile.toString() }
        profiles.removeAll(items)
        items.forEach { p ->
            profilesChanged.forEach { it(ProfilesChange.Removed, p.first, p.second) }
        }
    }

    val profilesChanged = mutableListOf<(change: ProfilesChange, profile: MidiCIProfileId, enabled: Boolean) -> Unit>()
}