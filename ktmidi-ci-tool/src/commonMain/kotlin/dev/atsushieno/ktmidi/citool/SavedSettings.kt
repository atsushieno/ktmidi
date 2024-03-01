package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.ci.*
import kotlinx.serialization.Serializable

@Serializable
class SavedSettings {
    val device = MidiCIDeviceConfiguration()
}
