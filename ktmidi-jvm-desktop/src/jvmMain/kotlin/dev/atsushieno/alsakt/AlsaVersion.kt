package dev.atsushieno.alsakt

import dev.atsushieno.alsa.javacpp.global.Alsa

object AlsaVersion {
    val versionString = Alsa.snd_asoundlib_version().string
    private val versionTokens = versionString.split('.')
    val major = if (versionTokens.size > 0) versionTokens[0].toInt() else 0
    val minor = if (versionTokens.size > 1) versionTokens[1].toInt() else 0
    val revision = if (versionTokens.size > 2) versionTokens[2].toInt() else 0
}
