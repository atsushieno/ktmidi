package dev.atsushieno.alsakt
import dev.atsushieno.alsa.javacpp.global.Alsa
import dev.atsushieno.alsa.javacpp.snd_seq_addr_t
import dev.atsushieno.alsa.javacpp.snd_seq_port_subscribe_t

class AlsaPortSubscription {
    class Address(val handle: snd_seq_addr_t?) {

        var client: Byte
            get() = handle?.asByteBuffer()?.get(0) ?: 0
            set(value) {
                handle?.asByteBuffer()?.put(value)
            }


        var port: Byte
            get() = handle?.asByteBuffer()?.get(1) ?: 0
            set(value) {
                handle?.asByteBuffer()?.put(1, value)
            }


        override fun toString(): String {
            return "ch${client}_port${port}"
        }
    }

    companion object {
        fun malloc(): snd_seq_port_subscribe_t? {
            val outHandle = snd_seq_port_subscribe_t()
            Alsa.snd_seq_port_subscribe_malloc(outHandle)
            return outHandle
        }

        fun free(handle: snd_seq_port_subscribe_t?) {
            if (handle != null)
                Alsa.snd_seq_port_subscribe_free(handle)
        }
    }


    constructor () : this(malloc(), { handle -> free(handle) })

    constructor (handle: snd_seq_port_subscribe_t?, free: (snd_seq_port_subscribe_t?) -> Unit) {
        this.handle = handle
        this.freeFunc = free
    }

    var handle: snd_seq_port_subscribe_t? // Pointer<snd_seq_port_subscribe_t>
    private val freeFunc: (snd_seq_port_subscribe_t?) -> Unit

    fun close() {
        if (handle != null)
            freeFunc(handle)
        handle = null
    }

    var sender: Address
        get() = Address(Alsa.snd_seq_port_subscribe_get_sender(handle))
        set(value) = Alsa.snd_seq_port_subscribe_set_sender(handle, value.handle)

    var destination: Address
        get() = Address(Alsa.snd_seq_port_subscribe_get_dest(handle))
        set(value) = Alsa.snd_seq_port_subscribe_set_dest(handle, value.handle)


    var queue: Int
        get() = Alsa.snd_seq_port_subscribe_get_queue(handle)
        set(value) = Alsa.snd_seq_port_subscribe_set_queue(handle, value)

    var exclusive: Boolean
        get() = Alsa.snd_seq_port_subscribe_get_exclusive(handle) != 0
        set(value) = Alsa.snd_seq_port_subscribe_set_exclusive(handle, if (value) 1 else 0)

    var updateTime: Boolean
        get() = Alsa.snd_seq_port_subscribe_get_time_update(handle) != 0
        set(value) = Alsa.snd_seq_port_subscribe_set_time_update(handle, if (value) 1 else 0)

    var isRealTimeUpdateMode: Boolean
        get() = Alsa.snd_seq_port_subscribe_get_time_real(handle) != 0
        set(value) = Alsa.snd_seq_port_subscribe_set_time_real(handle, if (value) 1 else 0)
}
