package dev.atsushieno.ktmidi.samples.inputsample

import dev.atsushieno.ktmidi.MidiAccess
import dev.atsushieno.ktmidi.TraditionalCoreMidiAccess

actual fun getNativeMidiAccessApi(): MidiAccess = TraditionalCoreMidiAccess()

