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

    val profilesChanged = mutableListOf<(change: ProfilesChange, profile: MidiCIProfile) -> Unit>()
}