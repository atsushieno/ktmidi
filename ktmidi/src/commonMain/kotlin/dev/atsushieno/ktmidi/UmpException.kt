package dev.atsushieno.ktmidi

class UmpException : Exception {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(message: String, innerException: Exception) : super(message, innerException)
}
