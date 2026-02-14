package dev.atsushieno.alsakt
import dev.atsushieno.panama.alsa.alsa_seq_h
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment

class AlsaPortSubscription {
    class Address(val handle: MemorySegment) {

        var client: Byte
            get() = handle.asByteBuffer()?.get(0) ?: 0
            set(value) {
                handle.asByteBuffer()?.put(value)
            }


        var port: Byte
            get() = handle.asByteBuffer()?.get(1) ?: 0
            set(value) {
                handle.asByteBuffer()?.put(1, value)
            }


        override fun toString(): String {
            return "ch${client}_port${port}"
        }
    }

    companion object {
        fun malloc(): MemorySegment {
            val outHandle = Arena.ofShared().allocate(alsa_seq_h.snd_seq_port_subscribe_sizeof())
            alsa_seq_h.snd_seq_port_subscribe_malloc(outHandle)
            return outHandle
        }

        fun free(handle: MemorySegment?) {
            if (handle != null)
                alsa_seq_h.snd_seq_port_subscribe_free(handle)
        }
    }


    constructor () : this(malloc(), { handle -> free(handle) })

    constructor (handle: MemorySegment, free: (MemorySegment?) -> Unit) {
        this.handle = handle
        this.freeFunc = free
    }

    var handle: MemorySegment? // Pointer<snd_seq_port_subscribe_t>
    private val freeFunc: (MemorySegment?) -> Unit

    fun close() {
        if (handle != null)
            freeFunc(handle)
        handle = null
    }

    var sender: Address
        get() = Address(alsa_seq_h.snd_seq_port_subscribe_get_sender(handle))
        set(value) = alsa_seq_h.snd_seq_port_subscribe_set_sender(handle, value.handle)

    var destination: Address
        get() = Address(alsa_seq_h.snd_seq_port_subscribe_get_dest(handle))
        set(value) = alsa_seq_h.snd_seq_port_subscribe_set_dest(handle, value.handle)


    var queue: Int
        get() = alsa_seq_h.snd_seq_port_subscribe_get_queue(handle)
        set(value) = alsa_seq_h.snd_seq_port_subscribe_set_queue(handle, value)

    var exclusive: Boolean
        get() = alsa_seq_h.snd_seq_port_subscribe_get_exclusive(handle) != 0
        set(value) = alsa_seq_h.snd_seq_port_subscribe_set_exclusive(handle, if (value) 1 else 0)

    var updateTime: Boolean
        get() = alsa_seq_h.snd_seq_port_subscribe_get_time_update(handle) != 0
        set(value) = alsa_seq_h.snd_seq_port_subscribe_set_time_update(handle, if (value) 1 else 0)

    var isRealTimeUpdateMode: Boolean
        get() = alsa_seq_h.snd_seq_port_subscribe_get_time_real(handle) != 0
        set(value) = alsa_seq_h.snd_seq_port_subscribe_set_time_real(handle, if (value) 1 else 0)
}
