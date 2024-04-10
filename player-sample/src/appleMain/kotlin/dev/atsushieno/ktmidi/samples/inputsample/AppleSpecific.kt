package dev.atsushieno.ktmidi.samples.inputsample

import dev.atsushieno.ktmidi.*

actual fun getNativeMidiAccessApi(midiTransportProtocol: Int): MidiAccess =
    if (midiTransportProtocol == MidiTransportProtocol.UMP)
        UmpCoreMidiAccess()
    else
        TraditionalCoreMidiAccess()

