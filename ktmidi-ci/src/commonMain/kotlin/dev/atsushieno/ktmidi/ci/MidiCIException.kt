package dev.atsushieno.ktmidi.ci

class MidiCIException: Exception {
    constructor() : this("MIDI-CI error")
    constructor(message: String) : this(message, null)
    constructor(message: String, innerException: Exception?) : super(message, innerException)
}
