package dev.atsushieno.ktmidi

import platform.darwin.OSStatus

class CoreMidiException(status: OSStatus) : Exception("CoreMIDI error: $status")