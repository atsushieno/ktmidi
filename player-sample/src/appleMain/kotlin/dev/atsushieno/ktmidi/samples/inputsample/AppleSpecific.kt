package dev.atsushieno.ktmidi.samples.inputsample

import dev.atsushieno.ktmidi.MidiAccess
import dev.atsushieno.ktmidi.RtMidiNativeAccess

actual fun getNativeMidiAccessApi(): MidiAccess = RtMidiNativeAccess() //CoreMidiAccess()

