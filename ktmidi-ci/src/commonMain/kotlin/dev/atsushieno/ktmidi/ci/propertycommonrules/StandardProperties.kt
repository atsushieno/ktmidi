package dev.atsushieno.ktmidi.ci.propertycommonrules

import dev.atsushieno.ktmidi.ci.MidiCIConverter
import dev.atsushieno.ktmidi.ci.MidiCIDevice
import dev.atsushieno.ktmidi.ci.ObservablePropertyList
import dev.atsushieno.ktmidi.ci.PropertyValue
import dev.atsushieno.ktmidi.ci.json.Json
import dev.atsushieno.ktmidi.ci.toASCIIByteArray
import kotlinx.serialization.Serializable

object StandardPropertyNames {
    const val STATE_LIST = "StateList"
    const val ALL_CTRL_LIST = "AllCtrlList"
    const val CH_CTRL_LIST = "ChCtrlList"
    const val PROGRAM_LIST = "ProgramList"
}

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

// ProgramList entries
@Serializable
class MidiCIProgram(
    val title: String,
    val bankPC: Array<Byte>, // minItems = 3, maxItems = 3
    val category: Array<String>?, // minItems = 1, minLength = 1
    val tags: Array<String>? // minItems = 1, minLength = 1
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

object ProgramPropertyNames {
    const val TITLE = "title"
    const val BANK_PC = "bankPC"
    const val CATEGORY = "category"
    const val TAGS = "tags"
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
            val transmit =
                json.getObjectValue(ControlPropertyNames.TRANSMIT)?.stringValue ?: MidiCIControlTransmit.ABSOLUTE
            val recognize =
                json.getObjectValue(ControlPropertyNames.RECOGNIZE)?.stringValue ?: MidiCIControlTransmit.ABSOLUTE
            val numSigBits = json.getObjectValue(ControlPropertyNames.NUM_SIG_BITS)?.numberValue?.toInt() ?: 32
            val paramPath = json.getObjectValue(ControlPropertyNames.PARAM_PATH)?.stringValue
            val typeHint = json.getObjectValue(ControlPropertyNames.TYPE_HINT)?.stringValue
            val ctrlMapId = json.getObjectValue(ControlPropertyNames.CTRL_MAP_ID)?.stringValue
            val stepCount = json.getObjectValue(ControlPropertyNames.STEP_COUNT)?.numberValue?.toInt()
            val minMax = json.getObjectValue(ControlPropertyNames.MIN_MAX)
                ?.arrayValue?.map { it.numberValue.toLong().toUInt() }?.toList()?.toTypedArray() ?: arrayOf()
            val defaultCCMap = json.getObjectValue(ControlPropertyNames.DEFAULT_CC_MAP)?.isBooleanTrue == true
            MidiCIControl(
                title,
                ctrlType,
                description,
                ctrlIndex,
                channel,
                priority,
                default,
                transmit,
                recognize,
                numSigBits,
                paramPath,
                typeHint,
                ctrlMapId,
                stepCount,
                minMax,
                defaultCCMap
            )
        }.toList()
    }

    fun parseProgramList(data: List<Byte>): List<MidiCIProgram> {
        val jsonArray = convertApplicationJsonBytesToJson(data)
        return jsonArray.arrayValue.map { json ->
            val title = json.getObjectValue(ProgramPropertyNames.TITLE)?.stringValue ?: ""
            val bankPC = json.getObjectValue(ProgramPropertyNames.BANK_PC)
                ?.arrayValue?.map { it.numberValue.toByte() }?.toList()?.toTypedArray() ?: arrayOf()
            val category =
                json.getObjectValue(ProgramPropertyNames.CATEGORY)?.arrayValue?.map { it.stringValue }?.toList()
                    ?.toTypedArray()
            val tags = json.getObjectValue(ProgramPropertyNames.TAGS)?.arrayValue?.map { it.stringValue }?.toList()
                ?.toTypedArray()
            MidiCIProgram(title, bankPC, category, tags)
        }.toList()
    }

    fun stateListToJson(stateList: List<MidiCIState>): Json.JsonValue {
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

    fun controlListToJson(controlList: List<MidiCIControl>): Json.JsonValue {
        return Json.JsonValue(controlList.map { it ->
            val map = sequence {
                yield(Json.JsonValue(ControlPropertyNames.TITLE) to Json.JsonValue(it.title))
                if (it.description != null)
                    yield(Json.JsonValue(ControlPropertyNames.DESCRIPTION) to Json.JsonValue(it.description))
                yield(Json.JsonValue(ControlPropertyNames.CTRL_TYPE) to Json.JsonValue(it.ctrlType))
                if (it.ctrlIndex != null)
                    yield(Json.JsonValue(ControlPropertyNames.CTRL_INDEX) to Json.JsonValue(it.ctrlIndex.map { e ->
                        Json.JsonValue(
                            e.toDouble()
                        )
                    }))
                if (it.channel != null)
                    yield(Json.JsonValue(ControlPropertyNames.CHANNEL) to Json.JsonValue(it.channel.toDouble()))
                if (it.priority != null)
                    yield(Json.JsonValue(ControlPropertyNames.PRIORITY) to Json.JsonValue(it.priority.toDouble()))
                yield(Json.JsonValue(ControlPropertyNames.DEFAULT) to Json.JsonValue(it.default.toDouble()))
                yield(Json.JsonValue(ControlPropertyNames.TRANSMIT) to Json.JsonValue(it.transmit))
                yield(Json.JsonValue(ControlPropertyNames.RECOGNIZE) to Json.JsonValue(it.recognize))
                yield(Json.JsonValue(ControlPropertyNames.NUM_SIG_BITS) to Json.JsonValue(it.numSigBits.toDouble()))
                if (it.paramPath != null)
                    yield(Json.JsonValue(ControlPropertyNames.PARAM_PATH) to Json.JsonValue(it.paramPath))
                if (it.typeHint != null)
                    yield(Json.JsonValue(ControlPropertyNames.TYPE_HINT) to Json.JsonValue(it.typeHint))
                if (it.ctrlMapId != null)
                    yield(Json.JsonValue(ControlPropertyNames.CTRL_MAP_ID) to Json.JsonValue(it.ctrlMapId))
                if (it.stepCount != null)
                    yield(Json.JsonValue(ControlPropertyNames.STEP_COUNT) to Json.JsonValue(it.stepCount.toDouble()))
                if (it.minMax != null)
                    yield(Json.JsonValue(ControlPropertyNames.MIN_MAX) to Json.JsonValue(it.minMax.map { e ->
                        Json.JsonValue(
                            e.toDouble()
                        )
                    }))
                if (it.defaultCCMap)
                    yield(Json.JsonValue(ControlPropertyNames.DEFAULT_CC_MAP) to if (it.defaultCCMap) Json.TrueValue else Json.FalseValue)
            }.toMap()
            Json.JsonValue(map)
        })
    }

    fun programListToJson(programList: List<MidiCIProgram>): Json.JsonValue {
        return Json.JsonValue(programList.map { it ->
            val map = sequence {
                yield(Json.JsonValue(ProgramPropertyNames.TITLE) to Json.JsonValue(it.title))
                yield(Json.JsonValue(ProgramPropertyNames.BANK_PC) to Json.JsonValue(it.bankPC.map { Json.JsonValue(it.toDouble()) }))
                if (it.category != null)
                    yield(Json.JsonValue(ProgramPropertyNames.CATEGORY) to Json.JsonValue(it.category.map {
                        Json.JsonValue(
                            it
                        )
                    }))
                if (it.tags != null)
                    yield(Json.JsonValue(ProgramPropertyNames.TAGS) to Json.JsonValue(it.tags.map { Json.JsonValue(it) }))
            }.toMap()
            Json.JsonValue(map)
        })
    }
}

private fun Json.JsonValue.jsonToASCIIStringBytes() =
    MidiCIConverter.encodeStringToASCII(Json.serialize(this)).toASCIIByteArray().toList()

private val ObservablePropertyList.stateListProperty: PropertyValue?
    get() = values.firstOrNull { it.id == StandardPropertyNames.STATE_LIST }
val ObservablePropertyList.stateList
    get() = stateListProperty?.let { StandardProperties.parseStateList(it.body) }
var MidiCIDevice.stateList: List<MidiCIState>?
    get() = propertyHost.properties.stateList
    set(value) { if (value != null) propertyHost.setPropertyValue(StandardPropertyNames.STATE_LIST, null, StandardProperties.stateListToJson(value).jsonToASCIIStringBytes(), false) }

private val ObservablePropertyList.allCtrlListProperty: PropertyValue?
    get() = values.firstOrNull { it.id == StandardPropertyNames.ALL_CTRL_LIST }
val ObservablePropertyList.allCtrlList: List<MidiCIControl>?
    get() = allCtrlListProperty?.let { StandardProperties.parseControlList(it.body) }
var MidiCIDevice.allCtrlList: List<MidiCIControl>?
    get() = propertyHost.properties.allCtrlList
    set(value) { if (value != null) propertyHost.setPropertyValue(StandardPropertyNames.ALL_CTRL_LIST, null, StandardProperties.controlListToJson(value).jsonToASCIIStringBytes(), false) }

private val ObservablePropertyList.chCtrlListProperty: PropertyValue?
    get() = values.firstOrNull { it.id == StandardPropertyNames.CH_CTRL_LIST }
val ObservablePropertyList.chCtrlList: List<MidiCIControl>?
    get() = chCtrlListProperty?.let { StandardProperties.parseControlList(it.body) }
var MidiCIDevice.chCtrlList: List<MidiCIControl>?
    get() = propertyHost.properties.chCtrlList
    set(value) { if (value != null) propertyHost.setPropertyValue(StandardPropertyNames.CH_CTRL_LIST, null, StandardProperties.controlListToJson(value).jsonToASCIIStringBytes(), false) }

private val ObservablePropertyList.programListProperty: PropertyValue?
    get() = values.firstOrNull { it.id == StandardPropertyNames.PROGRAM_LIST }
val ObservablePropertyList.programList: List<MidiCIProgram>?
    get() = programListProperty?.let { StandardProperties.parseProgramList(it.body) }
var MidiCIDevice.programList: List<MidiCIProgram>?
    get() = propertyHost.properties.programList
    set(value) { if (value != null) propertyHost.setPropertyValue(StandardPropertyNames.PROGRAM_LIST, null, StandardProperties.programListToJson(value).jsonToASCIIStringBytes(), false) }
