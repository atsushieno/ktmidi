package dev.atsushieno.ktmidi.samples.inputsample

import dev.atsushieno.ktmidi.MidiAccess
import dev.atsushieno.ktmidi.MidiTransportProtocol
import dev.atsushieno.ktmidi.TraditionalCoreMidiAccess
import dev.atsushieno.ktmidi.UmpCoreMidiAccess

actual fun getNativeMidiAccessApi(midiTransportProtocol: Int): MidiAccess =
    if (midiTransportProtocol == MidiTransportProtocol.UMP)
        UmpCoreMidiAccess()
    else
        TraditionalCoreMidiAccess()
