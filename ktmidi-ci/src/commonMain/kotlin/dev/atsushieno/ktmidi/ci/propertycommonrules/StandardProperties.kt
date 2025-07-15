package dev.atsushieno.ktmidi.ci.propertycommonrules

import dev.atsushieno.ktmidi.ci.json.Json
import kotlinx.serialization.Serializable

// StateList entries
@Serializable
data class MidiCIState(
    val title: String,
    val stateId: String,
    val stateRev: String?,
    val timestamp: Long?,
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
    val default: UInt = 0u,
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

object StatePropertyNames {
    const val TITLE = "title"
    const val STATE_ID = "stateId"
    const val STATE_REV = "stateRev"
    const val TIMESTAMP = "timestamp"
    const val DESCRIPTION = "description"
    const val SIZE = "size"
}

object ControlPropertyNames {
    const val TITLE = "title"
    const val DESCRIPTION = "description"
    const val CTRL_TYPE = "ctrlType"
    const val CTRL_INDEX = "ctrlIndex"
    const val CHANNEL = "channel"
    const val PRIORITY = "priority"
    const val DEFAULT = "default"
    const val TRANSMIT = "transmit"
    const val RECOGNIZE = "recognize"
    const val NUM_SIG_BITS = "numSigBits"
    const val PARAM_PATH = "paramPath"
    const val TYPE_HINT = "typeHint"
    const val CTRL_MAP_ID = "ctrlMapId"
    const val STEP_COUNT = "stepCount"
    const val MIN_MAX = "minMax"
    const val DEFAULT_CC_MAP = "defaultCCMap"
}

object StandardProperties {
    fun parseStateList(data: List<Byte>): List<MidiCIState> {
        val json = convertApplicationJsonBytesToJson(data)
        return json.arrayValue.map {
            val title = json.getObjectValue(StatePropertyNames.TITLE)?.stringValue ?: ""
            val stateId = json.getObjectValue(StatePropertyNames.STATE_ID)?.stringValue ?: ""
            val stateRev = json.getObjectValue(StatePropertyNames.STATE_REV)?.stringValue
            val timestamp = json.getObjectValue(StatePropertyNames.TIMESTAMP)?.numberValue?.toLong()
            val description = json.getObjectValue(StatePropertyNames.DESCRIPTION)?.stringValue
            val size = json.getObjectValue(StatePropertyNames.SIZE)?.numberValue?.toInt()
            MidiCIState(title, stateId, stateRev, timestamp, description, size)
        }.toList()
    }

    fun parseControlList(data: List<Byte>): List<MidiCIControl> {
        val jsonArray = convertApplicationJsonBytesToJson(data)
        return jsonArray.arrayValue.map { json ->
            val title = json.getObjectValue(ControlPropertyNames.TITLE)?.stringValue ?: ""
            val description = json.getObjectValue(ControlPropertyNames.DESCRIPTION)?.stringValue ?: ""
            val ctrlType = json.getObjectValue(ControlPropertyNames.CTRL_TYPE)?.stringValue ?: ""
            val ctrlIndex = json.getObjectValue(ControlPropertyNames.CTRL_INDEX)
                ?.arrayValue?.map { it.numberValue.toByte() }?.toList()?.toTypedArray() ?: arrayOf()
            val channel = json.getObjectValue(ControlPropertyNames.CHANNEL)?.numberValue?.toByte()
            val priority = json.getObjectValue(ControlPropertyNames.PRIORITY)?.numberValue?.toByte()
            val default = json.getObjectValue(ControlPropertyNames.DEFAULT)?.numberValue?.toLong()?.toUInt() ?: 0u
            val transmit = json.getObjectValue(ControlPropertyNames.TRANSMIT)?.stringValue ?: MidiCIControlTransmit.ABSOLUTE
            val recognize = json.getObjectValue(ControlPropertyNames.RECOGNIZE)?.stringValue ?: MidiCIControlTransmit.ABSOLUTE
            val numSigBits = json.getObjectValue(ControlPropertyNames.NUM_SIG_BITS)?.numberValue?.toInt() ?: 32
            val paramPath = json.getObjectValue(ControlPropertyNames.PARAM_PATH)?.stringValue
            val typeHint = json.getObjectValue(ControlPropertyNames.TYPE_HINT)?.stringValue
            val ctrlMapId = json.getObjectValue(ControlPropertyNames.CTRL_MAP_ID)?.stringValue
            val stepCount = json.getObjectValue(ControlPropertyNames.STEP_COUNT)?.numberValue?.toInt()
            val minMax = json.getObjectValue(ControlPropertyNames.MIN_MAX)
                ?.arrayValue?.map { it.numberValue.toLong().toUInt() }?.toList()?.toTypedArray() ?: arrayOf()
            val defaultCCMap = json.getObjectValue(ControlPropertyNames.DEFAULT_CC_MAP)?.isBooleanTrue == true
            MidiCIControl(title, ctrlType, description, ctrlIndex, channel, priority, default, transmit, recognize, numSigBits, paramPath, typeHint, ctrlMapId, stepCount, minMax, defaultCCMap)
        }.toList()
    }

    fun toJson(stateList: List<MidiCIState>): Json.JsonValue {
        return Json.JsonValue(stateList.map {
            val map = mapOf(
                Json.JsonValue(StatePropertyNames.TITLE) to Json.JsonValue(it.title),
                Json.JsonValue(StatePropertyNames.STATE_ID) to Json.JsonValue(it.stateId),
                Json.JsonValue(StatePropertyNames.STATE_REV) to Json.JsonValue(it.stateRev ?: ""),
                Json.JsonValue(StatePropertyNames.TIMESTAMP) to Json.JsonValue(it.timestamp?.toDouble() ?: 0.0),
                Json.JsonValue(StatePropertyNames.DESCRIPTION) to Json.JsonValue(it.description ?: ""),
                Json.JsonValue(StatePropertyNames.SIZE) to Json.JsonValue(it.size?.toDouble() ?: 0.0),
            )
            Json.JsonValue(map)
        })
    }
}