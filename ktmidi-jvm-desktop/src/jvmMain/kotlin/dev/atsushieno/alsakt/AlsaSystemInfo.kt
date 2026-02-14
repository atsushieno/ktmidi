package dev.atsushieno.alsakt

import dev.atsushieno.panama.alsa.alsa_seq_h
import dev.atsushieno.panama.alsa.snd_seq_system_info_t
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment


class AlsaSystemInfo() : AutoCloseable {

    fun setContextSequencer (seq: AlsaSequencer) {
        alsa_seq_h.snd_seq_system_info (seq.sequencerHandle, handle)
    }

    val maxQueueCount: Int
        get() = alsa_seq_h.snd_seq_system_info_get_queues (handle)
    val maxClientCount : Int
        get() =  alsa_seq_h.snd_seq_system_info_get_clients (handle)
    val portCount : Int
        get() = alsa_seq_h.snd_seq_system_info_get_ports (handle)
    val channelCount : Int
        get() =  alsa_seq_h.snd_seq_system_info_get_channels (handle)
    val currentQueueCount : Int
        get() =  alsa_seq_h.snd_seq_system_info_get_cur_queues (handle)
    val currentClientCount : Int
        get() =  alsa_seq_h.snd_seq_system_info_get_cur_clients (handle)

    private val handle: MemorySegment

    override fun close () {
        alsa_seq_h.snd_seq_system_info_free (handle)
    }

    init {
        val ptr = snd_seq_system_info_t.allocate(Arena.ofShared())
        alsa_seq_h.snd_seq_system_info_malloc (ptr)
        handle = ptr
    }
}