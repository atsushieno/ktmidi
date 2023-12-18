package dev.atsushieno.ktmidi.citool

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import dev.atsushieno.ktmidi.*

object AppModel {
    var midiProtocol = mutableStateOf(MidiCIProtocolType.MIDI1)
    var midiOutputPorts = mutableStateListOf<MidiPortDetails>()

    val midiDeviceManager = MidiDeviceManager()

    private fun sendToAll(bytes: ByteArray, timestamp: Long) {
        midiDeviceManager.sendToAll(bytes, timestamp)
    }

    fun setOutputDevice(id: String) {
        midiDeviceManager.midiOutputDeviceId = id
    }

    fun updateMidiDeviceList() {
        midiOutputPorts.clear()
        midiOutputPorts.addAll(midiDeviceManager.midiOutputPorts)
    }
}
