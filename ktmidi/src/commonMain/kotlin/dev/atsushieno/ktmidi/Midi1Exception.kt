package dev.atsushieno.ktmidi

class Midi1Exception : Exception {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(message: String, innerException: Exception) : super(message, innerException)
}
