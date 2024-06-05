package dev.atsushieno.alsakt

import dev.atsushieno.alsa.javacpp.global.Alsa

class AlsaException : Exception {
    constructor() : super("ALSA error")
    constructor(errorCode: Int) : super("ALSA error: ${Alsa.snd_strerror(errorCode).string} (error code $errorCode)")
    constructor(msg: String?) : super(msg)

    constructor(msg: String?, innerException: Exception?) : super(msg, innerException)
}