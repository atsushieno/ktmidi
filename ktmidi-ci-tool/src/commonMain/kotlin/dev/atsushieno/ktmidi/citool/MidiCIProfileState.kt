package dev.atsushieno.ktmidi.citool

import androidx.compose.runtime.MutableState
import dev.atsushieno.ktmidi.ci.MidiCIProfileId

class MidiCIProfileState(
    var group: MutableState<Byte>,
    var address: MutableState<Byte>,
    val profile: MidiCIProfileId,
    val enabled: MutableState<Boolean>,
    val numChannelsRequested: MutableState<Short>
)