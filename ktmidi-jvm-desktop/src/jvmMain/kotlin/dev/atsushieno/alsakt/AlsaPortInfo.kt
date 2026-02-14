@file:Suppress("unused")

package dev.atsushieno.alsakt
import dev.atsushieno.panama.alsa.alsa_global_h
import dev.atsushieno.panama.alsa.alsa_seq_h
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment

class AlsaPortInfo : AutoCloseable {
    companion object {
        const val PortSystemTimer = 0
        const val PortSystemAnnouncement = 1

        private fun malloc(): MemorySegment {
            val outHandle = Arena.ofShared().allocate(alsa_seq_h.snd_seq_port_info_sizeof())
            alsa_seq_h.snd_seq_port_info_malloc(outHandle)
            return outHandle
        }

        private fun free(handle: MemorySegment?) {
            if (handle != null)
                alsa_seq_h.snd_seq_port_info_free(handle)
        }
    }

    constructor () : this(malloc(), { handle -> free(handle) })

    constructor (handle: MemorySegment?, port: Int) {
        this.handle = handle
        this.freeFunc = {}
    }

    constructor (handle: MemorySegment?, free: (MemorySegment?) -> Unit) {
        this.handle = handle
        this.freeFunc = free
    }

    internal var handle: MemorySegment?//Pointer<snd_seq_port_info_t>
    private val freeFunc: (MemorySegment?) -> Unit

    override fun close() {
        if (handle != null)
            freeFunc(handle)
        handle = null
    }

    fun clone(): AlsaPortInfo {
        val ret = AlsaPortInfo()
        alsa_seq_h.snd_seq_port_info_copy(ret.handle, handle)
        return ret
    }

    var client: Int
        get() = alsa_seq_h.snd_seq_port_info_get_client(handle)
        set(value) = alsa_seq_h.snd_seq_port_info_set_client(handle, value)

    var port: Int
        get() = alsa_seq_h.snd_seq_port_info_get_port(handle)
        set(value) = alsa_seq_h.snd_seq_port_info_set_port(handle, value)

    private var namePtr = Arena.ofShared().allocate(256)
    var name: String
        get() = alsa_seq_h.snd_seq_port_info_get_name(handle).getString(0)
        set(value) {
            namePtr.setString(0, value)
            alsa_seq_h.snd_seq_port_info_set_name(handle, namePtr)
        }

    var capabilities: Int
        get() = alsa_seq_h.snd_seq_port_info_get_capability (handle)
        set(value) = alsa_seq_h.snd_seq_port_info_set_capability(handle, value)

    var portType: Int
        get() = alsa_seq_h.snd_seq_port_info_get_type (handle)
        set(value) = alsa_seq_h.snd_seq_port_info_set_type(handle, value)

    var midiChannels: Int
        get() = alsa_seq_h.snd_seq_port_info_get_midi_channels(handle)
        set(value) = alsa_seq_h.snd_seq_port_info_set_midi_channels(handle, value)

    var midiVoices: Int
        get() = alsa_seq_h.snd_seq_port_info_get_midi_voices(handle)
        set(value) = alsa_seq_h.snd_seq_port_info_set_midi_voices(handle, value)

    var synthVoices: Int
        get() = alsa_seq_h.snd_seq_port_info_get_synth_voices(handle)
        set(value) = alsa_seq_h.snd_seq_port_info_set_synth_voices(handle, value)

    val readSubscriptions
        get() = alsa_seq_h.snd_seq_port_info_get_read_use(handle)

    val writeSubscriptions
        get() = alsa_seq_h.snd_seq_port_info_get_write_use(handle)

    var portSpecified
        get() = alsa_seq_h.snd_seq_port_info_get_port_specified(handle) > 0
        set(value) = alsa_seq_h.snd_seq_port_info_set_port_specified(handle, if (value) 1 else 0)

    var timestampQueue: Int
        get() = alsa_seq_h.snd_seq_port_info_get_timestamp_queue(handle)
        set(value) = alsa_seq_h.snd_seq_port_info_set_timestamp_queue(handle, value)

    var timestampReal: Int
        get() = alsa_seq_h.snd_seq_port_info_get_timestamp_real(handle)
        set(value) = alsa_seq_h.snd_seq_port_info_set_timestamp_real(handle, value)

    var timestamping: Boolean
        get() = alsa_seq_h.snd_seq_port_info_get_timestamping(handle) != 0
        set(value) = alsa_seq_h.snd_seq_port_info_set_timestamping(handle, if (value) 1 else 0)

    val id: String
        get() = "${client}_${port}"

    val manufacturer = "" // FIXME: implement

    val version = "" // FIXME: implement

    var direction: Int
        get() = alsa_seq_h.snd_seq_port_info_get_direction(handle)
        set(value) = alsa_seq_h.snd_seq_port_info_set_direction(handle, value)

    var umpGroup: Int
        get() = alsa_seq_h.snd_seq_port_info_get_ump_group(handle)
        set(value) = alsa_seq_h.snd_seq_port_info_set_ump_group(handle, value)
}
