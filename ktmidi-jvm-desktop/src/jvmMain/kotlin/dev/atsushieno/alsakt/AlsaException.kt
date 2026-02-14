package dev.atsushieno.alsakt

import dev.atsushieno.panama.alsa.alsa_global_h

class AlsaException : Exception {
    constructor() : super("ALSA error")
    constructor(errorCode: Int) : super("ALSA error: ${alsa_global_h.snd_strerror(errorCode)} (error code $errorCode)")
    constructor(msg: String?) : super(msg)

    constructor(msg: String?, innerException: Exception?) : super(msg, innerException)
}