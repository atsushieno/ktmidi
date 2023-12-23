package dev.atsushieno.ktmidi.ci

/**
 * Observable list of MIDI-CI Profiles
 */
class ObservableProfileList {
    enum class ProfilesChange { Added, Removed }
    private val pl = mutableListOf<Pair<MidiCIProfileId,Boolean>>()
    val profiles: List<Pair<MidiCIProfileId,Boolean>>
        get() = pl

    fun add(profile: MidiCIProfileId, enabled: Boolean) {
        pl.removeAll { it.first.toString() == profile.toString() }
        pl.add(Pair(profile, enabled))
        profilesChanged.forEach { it(ProfilesChange.Added, profile, enabled) }
    }
    fun remove(profile: MidiCIProfileId) {
        // FIXME: there may be better equality comparison...
        val items = profiles.filter { it.first.toString() == profile.toString() }
        pl.removeAll(items)
        items.forEach { p ->
            profilesChanged.forEach { it(ProfilesChange.Removed, p.first, p.second) }
        }
    }

    val profilesChanged = mutableListOf<(change: ProfilesChange, profile: MidiCIProfileId, enabled: Boolean) -> Unit>()
}