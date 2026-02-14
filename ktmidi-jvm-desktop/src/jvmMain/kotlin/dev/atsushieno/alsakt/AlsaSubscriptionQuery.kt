@file:Suppress("unused")

package dev.atsushieno.alsakt

import dev.atsushieno.panama.alsa.alsa_seq_h
import dev.atsushieno.panama.alsa.snd_seq_query_subscribe_t
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment

class AlsaSubscriptionQuery(var handle: MemorySegment?, val freeFunc: (MemorySegment?) -> Unit) {
    companion object {
        private fun malloc(): MemorySegment? {
            val ptr = snd_seq_query_subscribe_t.allocate(Arena.ofShared())
            alsa_seq_h.snd_seq_query_subscribe_malloc(ptr)
            return ptr
        }

        private fun free(handle: MemorySegment?) {
            if (handle != null)
                alsa_seq_h.snd_seq_query_subscribe_free(handle)
        }
    }

    constructor() : this (malloc(), { handle -> free(handle) })

     fun close () {
        if (handle != null)
            freeFunc (handle)
        handle = null
    }

    var client: Int
        get() = alsa_seq_h.snd_seq_query_subscribe_get_client (handle)
        set(value) = alsa_seq_h.snd_seq_query_subscribe_set_client (handle, value)

     var port: Int
        get() = alsa_seq_h.snd_seq_query_subscribe_get_port (handle)
         set(value) = alsa_seq_h.snd_seq_query_subscribe_set_port (handle, value)


     var index: Int
        get() = alsa_seq_h.snd_seq_query_subscribe_get_index (handle)
         set(value) = alsa_seq_h.snd_seq_query_subscribe_set_index (handle, value)

     var type: Int
        get() = alsa_seq_h.snd_seq_query_subscribe_get_type (handle)
         set(value) = alsa_seq_h.snd_seq_query_subscribe_set_type (handle, value)

     val address : AlsaPortSubscription.Address
         get() = AlsaPortSubscription.Address(alsa_seq_h.snd_seq_query_subscribe_get_addr (handle))

     val exclusive : Boolean
         get() = alsa_seq_h.snd_seq_query_subscribe_get_exclusive (handle) != 0

     val queue : Int
        get() = alsa_seq_h.snd_seq_query_subscribe_get_queue (handle)
 }

