package dev.atsushieno.ktmidi.ci.propertycommonrules

import kotlinx.serialization.Serializable

// StateList entries
data class MidiCIState(
    val title: String,
    val stateId: String,
    val stateRev: String?,
    val timestamp: Int?,
    val description: String?,
    val size: Int?,
)

object MidiCIControlType {
    const val CC = "cc"
    const val CH_PRESS = "chPress"
    const val P_PRESS = "pPress"
    const val NRPN = "nrpn"
    const val RPN = "rpn"
    const val P_BEND = "pBend"
    const val PNRC = "pnrc"
    const val PNAC = "pnac"
    const val PNP = "pnp"
}

object MidiCIControlTransmit {
    const val ABSOLUTE = "absolute"
    const val RELATIVE = "relative"
    const val BOTH = "both"
    const val NONE = "none"
}

object MidiCIControlTypeHint {
    const val CONTINUOUS = "continuous"
    const val MOMENTARY = "momentary"
    const val TOGGLE = "toggle"
    const val RELATIVE = "relative"
    const val VALUE_SELECT = "valueSelect"
}

// AllCtrlList and ChCtrlList entries
@Serializable
class MidiCIControl(
    val title: String,
    val ctrlType: String, // MidiCIControlType
    val description: String? = "", // format: commonmark
    val ctrlIndex: Array<Byte>? = arrayOf(0), // array of 0-127, min length 1, max length 2
    val channel: Byte?,  // 1-16
    // LAMESPEC: lack of the default value will cause significant implementation incompatiblity
    val priority: Byte?, // 1-5
    val default: Int = 0,
    val transmit: String = MidiCIControlTransmit.ABSOLUTE,
    val recognize: String = MidiCIControlTransmit.ABSOLUTE,
    val numSigBits: Int = 32, // 1-32
    // LAMESPEC: missing this field in M2-117-S_v1-0_AllCtrlList.json
    val paramPath: String? = null,
    val typeHint: String? = null, // MidiCIControlTypeHint
    val ctrlMapId: String? = null, // resId
    val stepCount: Int? = null,
    val minMax: Array<UInt>? = arrayOf(0u, UInt.MAX_VALUE),
    // LAMESPEC: it's missing from AllCtrlList
    val defaultCCMap: Boolean = false
)
