package dev.atsushieno.ktmidi.citool

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import dev.atsushieno.ktmidi.*

object AppModel {
    var midiProtocol = mutableStateOf(MidiCIProtocolType.MIDI1)
    var midiInputPorts = mutableStateListOf<MidiPortDetails>()
    var midiOutputPorts = mutableStateListOf<MidiPortDetails>()

    val midiDeviceManager = MidiDeviceManager()

    fun setInputDevice(id: String) {
        midiDeviceManager.midiInputDeviceId = id
    }

    fun setOutputDevice(id: String) {
        midiDeviceManager.midiOutputDeviceId = id
    }

    fun updateMidiDeviceList() {
        midiInputPorts.clear()
        midiInputPorts.addAll(midiDeviceManager.midiInputPorts)
        midiOutputPorts.clear()
        midiOutputPorts.addAll(midiDeviceManager.midiOutputPorts)
    }
}
