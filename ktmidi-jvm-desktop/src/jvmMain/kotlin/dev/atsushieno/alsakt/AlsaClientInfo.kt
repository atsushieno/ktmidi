@file:Suppress("unused")

package dev.atsushieno.alsakt

import dev.atsushieno.alsa.javacpp.global.Alsa
import dev.atsushieno.alsa.javacpp.snd_seq_client_info_t
import org.bytedeco.javacpp.BytePointer

class AlsaClientInfo : AutoCloseable {
    companion object {
        private fun malloc(): snd_seq_client_info_t? {
            val outHandle = snd_seq_client_info_t()
            Alsa.snd_seq_client_info_malloc(outHandle)
            return outHandle
        }

        private fun free(handle: snd_seq_client_info_t?) {
            if (handle != null)
                Alsa.snd_seq_client_info_free(handle)
        }

    }

    constructor () : this (malloc (), { handle -> free(handle) })

    constructor (handle: snd_seq_client_info_t?,  free: (snd_seq_client_info_t?) -> Unit) {
        this.handle = handle
        this.freeFunc = free
    }

    internal var handle: snd_seq_client_info_t?//Pointer<snd_seq_client_info_t>
    private val freeFunc: (snd_seq_client_info_t?) -> Unit

    override fun close () {
        namePtr?.deallocate()
        namePtr = null
        if (handle != null) {
            freeFunc(handle)
            handle = null
        }
    }

    var client: Int
        get() = Alsa.snd_seq_client_info_get_client (handle)
        set(value) = Alsa.snd_seq_client_info_set_client (handle, value)

    val clientType: Int
        get () = Alsa.snd_seq_client_info_get_type (handle)

    private var namePtr: BytePointer? = null
    var name: String
        get() = Alsa.snd_seq_client_info_get_name (handle).string
        set(value) {
            namePtr?.deallocate()
            namePtr = BytePointer(value)
            Alsa.snd_seq_client_info_set_name(handle, namePtr)
        }

    var broadcastFilter: Int
        get() = Alsa.snd_seq_client_info_get_broadcast_filter (handle)
        set(value) = Alsa.snd_seq_client_info_set_broadcast_filter (handle, value)

    var errorBounce: Int
        get() = Alsa.snd_seq_client_info_get_error_bounce (handle)
        set(value) = Alsa.snd_seq_client_info_set_error_bounce (handle, value)

    val card : Int
        get() = Alsa.snd_seq_client_info_get_card (handle)
    val pid :Int
        get() = Alsa.snd_seq_client_info_get_pid (handle)
    val portCount: Int
        get() = Alsa.snd_seq_client_info_get_num_ports (handle)
    val eventLostCount : Int
        get() = Alsa.snd_seq_client_info_get_event_lost (handle)

    private val midiVersionAvailable =
        AlsaVersion.major >=1 &&
        AlsaVersion.minor >=2 &&
        AlsaVersion.revision >= 10
    var midiVersion : Int
        get() = if (midiVersionAvailable) Alsa.snd_seq_client_info_get_midi_version(handle) else 0
        set(value) {
            if (midiVersionAvailable)
                Alsa.snd_seq_client_info_set_midi_version(handle, value)
        }

    val umpConversion : Int
        get() = Alsa.snd_seq_client_info_get_ump_conversion(handle)

    var isUmpGrouplessEnabled : Boolean
        get() = Alsa.snd_seq_client_info_get_ump_groupless_enabled(handle) != 0
        set(value) = Alsa.snd_seq_client_info_set_ump_groupless_enabled(handle, if (value) 1 else 0)

    fun clearEventFilter () = Alsa.snd_seq_client_info_event_filter_clear (handle)
    fun addEventFilter ( eventType: Int) = Alsa.snd_seq_client_info_event_filter_add (handle, eventType)
    fun deleteEventFilter ( eventType: Int) = Alsa.snd_seq_client_info_event_filter_del (handle, eventType)
    fun isEventFiltered ( eventType: Int) = Alsa.snd_seq_client_info_event_filter_check (handle, eventType) > 0
    fun isUmpGroupEnabled(group: Int) = Alsa.snd_seq_client_info_get_ump_group_enabled(handle, group) != 0
    fun setUmpGroupEnabled(group: Int, enabled: Boolean) = Alsa.snd_seq_client_info_set_ump_group_enabled(handle, group, if (enabled) 1 else 0)
}

