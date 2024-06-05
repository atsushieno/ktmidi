package dev.atsushieno.alsakt

import dev.atsushieno.alsa.javacpp.global.Alsa
import dev.atsushieno.alsa.javacpp.snd_seq_system_info_t

class AlsaSystemInfo() : AutoCloseable {

    fun setContextSequencer (seq: AlsaSequencer) {
        Alsa.snd_seq_system_info (seq.sequencerHandle, handle)
    }

    val maxQueueCount: Int
        get() = Alsa.snd_seq_system_info_get_queues (handle)
    val maxClientCount : Int
        get() =  Alsa.snd_seq_system_info_get_clients (handle)
    val portCount : Int
        get() = Alsa.snd_seq_system_info_get_ports (handle)
    val channelCount : Int
        get() =  Alsa.snd_seq_system_info_get_channels (handle)
    val currentQueueCount : Int
        get() =  Alsa.snd_seq_system_info_get_cur_queues (handle)
    val currentClientCount : Int
        get() =  Alsa.snd_seq_system_info_get_cur_clients (handle)

    private val handle: snd_seq_system_info_t

    override fun close () {
        Alsa.snd_seq_system_info_free (handle)
    }

    init {
        val ptr = snd_seq_system_info_t()
        Alsa.snd_seq_system_info_malloc (ptr)
        handle = ptr
    }
}