package dev.atsushieno.ktmidi

import platform.darwin.OSStatus

class CoreMidiException private constructor (status: OSStatus, message: String?) : Exception(message ?: "CoreMIDI error: $status") {
    constructor(message: String) : this(0, message)
    constructor(status: OSStatus) : this(status, null)
}
