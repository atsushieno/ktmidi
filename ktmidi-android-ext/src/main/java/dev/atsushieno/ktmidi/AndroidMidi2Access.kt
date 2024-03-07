package dev.atsushieno.ktmidi

import android.content.Context
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.Build

class AndroidMidi2Access(applicationContext: Context, private val includeMidi1Transport: Boolean = false) : AndroidMidiAccess(applicationContext) {
    override val ports : List<MidiPortDetails>
        get() =
            (if (includeMidi1Transport) ports1 else listOf())
            .flatMap { d -> d.ports.map { port -> Pair(d, port) } }
            .map { pair -> AndroidPortDetails(pair.first, pair.second, 1) } +
            ports2.flatMap { d -> d.ports.map { port -> Pair(d, port) } }
            .map { pair -> AndroidPortDetails(pair.first, pair.second, 2) }
    private val ports2: List<MidiDeviceInfo>
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                manager.getDevicesForTransport(MidiManager.TRANSPORT_UNIVERSAL_MIDI_PACKETS).toList()
            else
                manager.devices.toList()
}