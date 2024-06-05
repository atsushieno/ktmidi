@file:Suppress("unused")

package dev.atsushieno.alsakt
import dev.atsushieno.alsa.javacpp.global.Alsa
import dev.atsushieno.alsa.javacpp.snd_seq_port_info_t
import org.bytedeco.javacpp.BytePointer

class AlsaPortInfo : AutoCloseable {
    companion object {
        const val PortSystemTimer = 0
        const val PortSystemAnnouncement = 1

        private fun malloc(): snd_seq_port_info_t? {
            val outHandle = snd_seq_port_info_t()
            Alsa.snd_seq_port_info_malloc(outHandle)
            return outHandle
        }

        private fun free(handle: snd_seq_port_info_t?) {
            if (handle != null)
                Alsa.snd_seq_port_info_free(handle)
        }
    }

    constructor () : this(malloc(), { handle -> free(handle) })

    constructor (handle: snd_seq_port_info_t?, port: Int) {
        this.handle = handle
        this.freeFunc = {}
    }

    constructor (handle: snd_seq_port_info_t?, free: (snd_seq_port_info_t?) -> Unit) {
        this.handle = handle
        this.freeFunc = free
    }

    internal var handle: snd_seq_port_info_t?//Pointer<snd_seq_port_info_t>
    private val freeFunc: (snd_seq_port_info_t?) -> Unit

    override fun close() {
        namePtr?.deallocate()
        namePtr = null
        if (handle != null)
            freeFunc(handle)
        handle = null
    }

    fun clone(): AlsaPortInfo {
        val ret = AlsaPortInfo()
        Alsa.snd_seq_port_info_copy(ret.handle, handle)
        return ret
    }

    var client: Int
        get() = Alsa.snd_seq_port_info_get_client(handle)
        set(value) = Alsa.snd_seq_port_info_set_client(handle, value)

    var port: Int
        get() = Alsa.snd_seq_port_info_get_port(handle)
        set(value) = Alsa.snd_seq_port_info_set_port(handle, value)

    private var namePtr: BytePointer? = null
    var name: String
        get() = Alsa.snd_seq_port_info_get_name(handle).string
        set(value) {
            namePtr?.deallocate()
            namePtr = BytePointer(value)
            Alsa.snd_seq_port_info_set_name(handle, namePtr)
        }

    var capabilities: Int
        get() = Alsa.snd_seq_port_info_get_capability (handle)
        set(value) = Alsa.snd_seq_port_info_set_capability(handle, value)

    var portType: Int
        get() = Alsa.snd_seq_port_info_get_type (handle)
        set(value) = Alsa.snd_seq_port_info_set_type(handle, value)

    var midiChannels: Int
        get() = Alsa.snd_seq_port_info_get_midi_channels(handle)
        set(value) = Alsa.snd_seq_port_info_set_midi_channels(handle, value)

    var midiVoices: Int
        get() = Alsa.snd_seq_port_info_get_midi_voices(handle)
        set(value) = Alsa.snd_seq_port_info_set_midi_voices(handle, value)

    var synthVoices: Int
        get() = Alsa.snd_seq_port_info_get_synth_voices(handle)
        set(value) = Alsa.snd_seq_port_info_set_synth_voices(handle, value)

    val readSubscriptions
        get() = Alsa.snd_seq_port_info_get_read_use(handle)

    val writeSubscriptions
        get() = Alsa.snd_seq_port_info_get_write_use(handle)

    var portSpecified
        get() = Alsa.snd_seq_port_info_get_port_specified(handle) > 0
        set(value) = Alsa.snd_seq_port_info_set_port_specified(handle, if (value) 1 else 0)

    var timestampQueue: Int
        get() = Alsa.snd_seq_port_info_get_timestamp_queue(handle)
        set(value) = Alsa.snd_seq_port_info_set_timestamp_queue(handle, value)

    var timestampReal: Int
        get() = Alsa.snd_seq_port_info_get_timestamp_real(handle)
        set(value) = Alsa.snd_seq_port_info_set_timestamp_real(handle, value)

    var timestamping: Boolean
        get() = Alsa.snd_seq_port_info_get_timestamping(handle) != 0
        set(value) = Alsa.snd_seq_port_info_set_timestamping(handle, if (value) 1 else 0)

    val id: String
        get() = "${client}_${port}"

    val manufacturer = "" // FIXME: implement

    val version = "" // FIXME: implement

    var direction: Int
        get() = Alsa.snd_seq_port_info_get_direction(handle)
        set(value) = Alsa.snd_seq_port_info_set_direction(handle, value)

    var umpGroup: Int
        get() = Alsa.snd_seq_port_info_get_ump_group(handle)
        set(value) = Alsa.snd_seq_port_info_set_ump_group(handle, value)
}
