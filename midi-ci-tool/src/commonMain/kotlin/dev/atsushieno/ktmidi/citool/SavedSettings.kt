package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.ci.MidiCIDeviceConfiguration
import dev.atsushieno.ktmidi.ci.MidiCIDeviceInfo
import dev.atsushieno.ktmidi.ci.MidiCIInitiatorConfiguration
import dev.atsushieno.ktmidi.ci.MidiCIResponderConfiguration
import kotlinx.serialization.Serializable

@Serializable
class SavedSettings {
    private val defaultUnifiedConfig =
        MidiCIDeviceInfo(0x123456,0x1234,0x5678,0x00000001,
            "atsushieno", "KtMidi", "KtMidi-CI-Tool", "0.1")
    val common = MidiCIDeviceConfiguration(defaultUnifiedConfig)
    val initiator = MidiCIInitiatorConfiguration(common)
    val recipient = MidiCIResponderConfiguration(common)
}
