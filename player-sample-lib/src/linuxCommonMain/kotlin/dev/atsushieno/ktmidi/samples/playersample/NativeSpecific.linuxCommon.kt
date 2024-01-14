package dev.atsushieno.ktmidi.samples.playersample

import dev.atsushieno.ktmidi.MidiAccess
import dev.atsushieno.ktmidi.RtMidiNativeAccess

actual fun getNativeMidiAccessApi(): MidiAccess = RtMidiNativeAccess()
