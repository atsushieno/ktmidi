@file:Suppress("unused")

import dev.atsushieno.alsakt.AlsaVersion
import dev.atsushieno.panama.alsa.alsa_global_h
import dev.atsushieno.panama.alsa.alsa_seq_h
import dev.atsushieno.panama.alsa.alsa_seq_midi_event_h
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment

class AlsaClientInfo : AutoCloseable {
    companion object {
        private fun malloc(): MemorySegment {
            val outHandle = Arena.ofShared().allocate(alsa_seq_h.snd_seq_client_info_sizeof())
            alsa_seq_h.snd_seq_client_info_malloc(outHandle)
            return outHandle
        }

        private fun free(handle: MemorySegment?) {
            if (handle != null)
                alsa_seq_h.snd_seq_client_info_free(handle)
        }

    }

    constructor () : this (malloc (), { handle -> free(handle) })

    constructor (handle: MemorySegment?,  free: (MemorySegment?) -> Unit) {
        this.handle = handle
        this.freeFunc = free
    }

    internal var handle: MemorySegment?//Pointer<snd_seq_client_info_t>
    private val freeFunc: (MemorySegment?) -> Unit

    override fun close () {
        if (handle != null) {
            freeFunc(handle)
            handle = null
        }
    }

    var client: Int
        get() = alsa_seq_h.snd_seq_client_info_get_client (handle)
        set(value) = alsa_seq_h.snd_seq_client_info_set_client (handle, value)

    val clientType: Int
        get () = alsa_seq_h.snd_seq_client_info_get_type (handle)

    private var namePtr = Arena.ofShared().allocate(256)
    var name: String
        get() = alsa_seq_h.snd_seq_client_info_get_name (handle).getString(0)
        set(value) {
            namePtr.setString(0, value)
            alsa_seq_h.snd_seq_client_info_set_name(handle, namePtr)
        }

    var broadcastFilter: Int
        get() = alsa_seq_h.snd_seq_client_info_get_broadcast_filter (handle)
        set(value) = alsa_seq_h.snd_seq_client_info_set_broadcast_filter (handle, value)

    var errorBounce: Int
        get() = alsa_seq_h.snd_seq_client_info_get_error_bounce (handle)
        set(value) = alsa_seq_h.snd_seq_client_info_set_error_bounce (handle, value)

    val card : Int
        get() = alsa_seq_h.snd_seq_client_info_get_card (handle)
    val pid :Int
        get() = alsa_seq_h.snd_seq_client_info_get_pid (handle)
    val portCount: Int
        get() = alsa_seq_h.snd_seq_client_info_get_num_ports (handle)
    val eventLostCount : Int
        get() = alsa_seq_h.snd_seq_client_info_get_event_lost (handle)

    private val midiVersionAvailable =
        AlsaVersion.major >=1 &&
        AlsaVersion.minor >=2 &&
        AlsaVersion.revision >= 10
    var midiVersion : Int
        get() = if (midiVersionAvailable) alsa_seq_h.snd_seq_client_info_get_midi_version(handle) else 0
        set(value) {
            if (midiVersionAvailable)
                alsa_seq_h.snd_seq_client_info_set_midi_version(handle, value)
        }

    val umpConversion : Int
        get() = alsa_seq_h.snd_seq_client_info_get_ump_conversion(handle)

    var isUmpGrouplessEnabled : Boolean
        get() = alsa_seq_h.snd_seq_client_info_get_ump_groupless_enabled(handle) != 0
        set(value) = alsa_seq_h.snd_seq_client_info_set_ump_groupless_enabled(handle, if (value) 1 else 0)

    fun clearEventFilter () = alsa_seq_h.snd_seq_client_info_event_filter_clear (handle)
    fun addEventFilter ( eventType: Int) = alsa_seq_h.snd_seq_client_info_event_filter_add (handle, eventType)
    fun deleteEventFilter ( eventType: Int) = alsa_seq_h.snd_seq_client_info_event_filter_del (handle, eventType)
    fun isEventFiltered ( eventType: Int) = alsa_seq_h.snd_seq_client_info_event_filter_check (handle, eventType) > 0
    fun isUmpGroupEnabled(group: Int) = alsa_seq_h.snd_seq_client_info_get_ump_group_enabled(handle, group) != 0
    fun setUmpGroupEnabled(group: Int, enabled: Boolean) = alsa_seq_h.snd_seq_client_info_set_ump_group_enabled(handle, group, if (enabled) 1 else 0)
}

