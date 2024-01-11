package dev.atsushieno.ktmidi.ci

object ImplementationSettings {
    /**
     * JUCE 7.0.9 has a bug that it fills `1` for 'numChannelsRequested' field even for 0x7E (group)
     * and 0x7F (function block) that are supposed to be `0` by MIDI-CI v1.2 specification (section 7.8).
     * It should be already fixed in the next JUCE release.
     */
    var workaroundJUCEProfileNumChannelsIssue: Boolean = false
}