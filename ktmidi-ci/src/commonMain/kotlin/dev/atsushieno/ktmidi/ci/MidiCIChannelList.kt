package dev.atsushieno.ktmidi.ci

import kotlinx.serialization.Serializable

@Serializable
class MidiCIChannelList {
    val channels = mutableListOf<MidiCIChannel>()
}

// 1-4 in the spec doc, 0-3 in the data model. They must be adjusted before sending and after receiving.
object ClusterMidiMode {
    const val NONE: Byte = 0
    const val OMNI_OFF: Byte = 0
    const val OMNI_ON: Byte = 1
    const val MONO_MODE: Byte = 0
    const val POLY_MODE: Byte = 2
    const val DEFAULT: Byte = (OMNI_OFF + POLY_MODE).toByte()
}

object ClusterType {
    const val OTHER = "other"
    const val PROFILE = "profile"
    const val MPE1 = "mpe1"
}

object ChannelInfoPropertyNames {
    const val TITLE = "title"
    const val CHANNEL = "channel"
    const val PROGRAM_TITLE = "programTitle"
    const val BANK_PC = "bankPC"
    const val CLUSTER_CHANNEL_START = "clusterChannelStart"
    const val CLUSTER_LENGTH = "clusterLength"
    const val CLUSTER_MIDI_MODE = "clusterMidiMode"
    const val CLUSTER_TYPE = "clusterType"
}

@Serializable
data class MidiCIChannel(
    val title: String,
    // Note that ChannelList property specification expects 1-256, not 0.255
    val channel: Int,
    val programTitle: String? = null,
    val bankMSB: Byte = 0,
    val bankLSB: Byte = 0,
    val program: Byte = 0,
    // Note that ChannelList property specification expects 1-256, not 0.255
    val clusterChannelStart: Int = 1,
    val clusterLength: Int = 1,
    val isOmniOn: Boolean = true,
    val isPolyMode: Boolean = true,
    val clusterType: String? = ClusterType.OTHER
) {
    // value range is 1-4, while it is 0-3 in code.
    val clusterMidiMode: Byte = ((if (isOmniOn) 1 else 0) + (if (isPolyMode) 2 else 0) + 1).toByte()

    val bankPC = arrayOf(bankMSB, bankLSB, program)
}
