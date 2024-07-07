package dev.atsushieno.ktmidi.samples.playersample

import dev.atsushieno.ktmidi.*

actual fun getNativeMidiAccessApi(midiTransportProtocol: Int): MidiAccess =
    if (midiTransportProtocol == MidiTransportProtocol.UMP)
        UmpCoreMidiAccess()
    else
        TraditionalCoreMidiAccess()

