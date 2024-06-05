@file:Suppress("unused")

package dev.atsushieno.alsakt
import dev.atsushieno.alsa.javacpp.global.Alsa
import dev.atsushieno.alsa.javacpp.snd_seq_query_subscribe_t

class AlsaSubscriptionQuery(var handle: snd_seq_query_subscribe_t?, val freeFunc: (snd_seq_query_subscribe_t?) -> Unit) {
    companion object {
        private fun malloc(): snd_seq_query_subscribe_t? {
            val ptr = snd_seq_query_subscribe_t()
            Alsa.snd_seq_query_subscribe_malloc(ptr)
            return ptr
        }

        private fun free(handle: snd_seq_query_subscribe_t?) {
            if (handle != null)
                Alsa.snd_seq_query_subscribe_free(handle)
        }
    }

    constructor() : this (malloc(), { handle -> free(handle) })

     fun close () {
        if (handle != null)
            freeFunc (handle)
        handle = null
    }

    var client: Int
        get() = Alsa.snd_seq_query_subscribe_get_client (handle)
        set(value) = Alsa.snd_seq_query_subscribe_set_client (handle, value)

     var port: Int
        get() = Alsa.snd_seq_query_subscribe_get_port (handle)
         set(value) = Alsa.snd_seq_query_subscribe_set_port (handle, value)


     var index: Int
        get() = Alsa.snd_seq_query_subscribe_get_index (handle)
         set(value) = Alsa.snd_seq_query_subscribe_set_index (handle, value)

     var type: Int
        get() = Alsa.snd_seq_query_subscribe_get_type (handle)
         set(value) = Alsa.snd_seq_query_subscribe_set_type (handle, value)

     val address : AlsaPortSubscription.Address
         get() = AlsaPortSubscription.Address(Alsa.snd_seq_query_subscribe_get_addr (handle))

     val exclusive : Boolean
         get() = Alsa.snd_seq_query_subscribe_get_exclusive (handle) != 0

     val queue : Int
        get() = Alsa.snd_seq_query_subscribe_get_queue (handle)
 }

