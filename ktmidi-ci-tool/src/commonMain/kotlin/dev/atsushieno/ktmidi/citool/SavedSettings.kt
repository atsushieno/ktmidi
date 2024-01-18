package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.ci.*
import kotlinx.serialization.Serializable

@Serializable
class SavedSettings {
    private val defaultUnifiedConfig =
        MidiCIDeviceInfo(0x123456,0x1234,0x5678,0x00000001,
            "atsushieno", "KtMidi", "KtMidi-CI-Tool", "0.1")
    val device = MidiCIDeviceConfiguration(defaultUnifiedConfig)
}
