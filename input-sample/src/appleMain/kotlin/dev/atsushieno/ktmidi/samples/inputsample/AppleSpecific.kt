package dev.atsushieno.ktmidi.samples.inputsample

import dev.atsushieno.ktmidi.MidiAccess
import dev.atsushieno.ktmidi.CoreMidiAccess

actual fun getNativeMidiAccessApi(): MidiAccess = CoreMidiAccess()

