package dev.atsushieno.ktmidi.ci.propertycommonrules

import dev.atsushieno.ktmidi.ci.ChannelInfoPropertyNames
import dev.atsushieno.ktmidi.ci.ClusterType
import dev.atsushieno.ktmidi.ci.MidiCIChannel
import dev.atsushieno.ktmidi.ci.MidiCIChannelList
import dev.atsushieno.ktmidi.ci.MidiCIConverter
import dev.atsushieno.ktmidi.ci.MidiCIDeviceInfo
import dev.atsushieno.ktmidi.ci.ObservablePropertyList
import dev.atsushieno.ktmidi.ci.PropertyMetadata
import dev.atsushieno.ktmidi.ci.PropertyValue
import dev.atsushieno.ktmidi.ci.json.Json
import dev.atsushieno.ktmidi.ci.propertycommonrules.FoundationalResources

object FoundationalResources {

    // JSON bytes to strongly-typed info

    private fun convertApplicationJsonBytesToJson(data: List<Byte>) =
        Json.parse(MidiCIConverter.decodeASCIIToString(data.toByteArray().decodeToString()))

    fun parseResourceList(data: List<Byte>): List<PropertyMetadata> =
        getMetadataListForBody(data)

    private fun getMetadataListForBody(data: List<Byte>): List<PropertyMetadata> {
        val json = convertApplicationJsonBytesToJson(data)
        return getMetadataListForBody(json)
    }

    private fun getMetadataListForBody(body: Json.JsonValue): List<PropertyMetadata> {
        // list of entries (name: value such as {"resource": "foo", "canSet": "partial"})
        val list = body.token.seq.toList()
        return list.map { entry ->
            val res = CommonRulesPropertyMetadata()
            entry.token.map.forEach {
                val v = it.value
                when (it.key.stringValue) {
                    PropertyResourceFields.RESOURCE -> res.resource = v.stringValue
                    PropertyResourceFields.CAN_GET -> res.canGet = v.token.type == Json.TokenType.True
                    PropertyResourceFields.CAN_SET -> res.canSet = v.stringValue
                    PropertyResourceFields.CAN_SUBSCRIBE -> res.canSubscribe = v.token.type == Json.TokenType.True
                    PropertyResourceFields.REQUIRE_RES_ID -> res.requireResId = v.token.type == Json.TokenType.True
                    PropertyResourceFields.ENCODINGS -> res.encodings = v.token.seq.map { e -> e.stringValue }.toList()
                    PropertyResourceFields.MEDIA_TYPE -> res.mediaTypes = v.token.seq.map { e -> e.stringValue }.toList()
                    PropertyResourceFields.SCHEMA -> res.schema = Json.getUnescapedString(v)
                    PropertyResourceFields.CAN_PAGINATE -> res.canPaginate = v.token.type == Json.TokenType.True
                    PropertyResourceFields.COLUMNS -> res.columns = v.token.seq.map { c ->
                        val col = PropertyResourceColumn()
                        c.token.map.forEach { prc ->
                            val cv = prc.value
                            when (prc.key.stringValue) {
                                PropertyResourceColumnFields.PROPERTY -> col.property = cv.stringValue
                                PropertyResourceColumnFields.LINK -> col.link = cv.stringValue
                                PropertyResourceColumnFields.TITLE -> col.title = cv.stringValue
                            }
                        }
                        col
                    }.toList()
                }
            }
            res
        }.toList()
    }

    fun parseDeviceInfo(data: List<Byte>): MidiCIDeviceInfo {
        val json = convertApplicationJsonBytesToJson(data)
        return MidiCIDeviceInfo(
            json.getObjectValue(DeviceInfoPropertyNames.MANUFACTURER_ID)?.numberValue?.toInt() ?: 0,
            json.getObjectValue(DeviceInfoPropertyNames.FAMILY_ID)?.numberValue?.toShort() ?: 0,
            json.getObjectValue(DeviceInfoPropertyNames.MODEL_ID)?.numberValue?.toShort() ?: 0,
            json.getObjectValue(DeviceInfoPropertyNames.VERSION_ID)?.numberValue?.toInt() ?: 0,
            json.getObjectValue(DeviceInfoPropertyNames.MANUFACTURER)?.stringValue ?: "",
            json.getObjectValue(DeviceInfoPropertyNames.FAMILY)?.stringValue ?: "",
            json.getObjectValue(DeviceInfoPropertyNames.MODEL)?.stringValue ?: "",
            json.getObjectValue(DeviceInfoPropertyNames.VERSION)?.stringValue ?: "",
            json.getObjectValue(DeviceInfoPropertyNames.SERIAL_NUMBER)?.stringValue ?: "",
        )
    }

    fun parseChannelList(data: List<Byte>): MidiCIChannelList {
        val json = convertApplicationJsonBytesToJson(data)
        return MidiCIChannelList().apply {
            json.arrayValue.map {
                val bankPC = json.getObjectValue(ChannelInfoPropertyNames.BANK_PC)?.arrayValue?.toList()
                val midiMode = json.getObjectValue(ChannelInfoPropertyNames.CLUSTER_MIDI_MODE)?.numberValue?.toInt()
                channels.add(
                    MidiCIChannel(
                        json.getObjectValue(ChannelInfoPropertyNames.TITLE)?.stringValue ?: "",
                        (json.getObjectValue(ChannelInfoPropertyNames.CHANNEL)?.numberValue?.toInt() ?: 1) - 1,
                        json.getObjectValue(ChannelInfoPropertyNames.PROGRAM_TITLE)?.stringValue,
                        (if (bankPC == null) 0 else bankPC[0].numberValue).toByte(),
                        (if (bankPC == null) 0 else bankPC[1].numberValue).toByte(),
                        (if (bankPC == null) 0 else bankPC[2].numberValue).toByte(),
                        (json.getObjectValue(ChannelInfoPropertyNames.CLUSTER_CHANNEL_START)?.numberValue?.toInt() ?: 1) - 1,
                        json.getObjectValue(ChannelInfoPropertyNames.CLUSTER_LENGTH)?.numberValue?.toInt() ?: 1,
                        if (midiMode == null) false else ((midiMode - 1) and 1) != 0,
                        if (midiMode == null) false else ((midiMode - 1) and 2) != 0,
                        json.getObjectValue(ChannelInfoPropertyNames.CLUSTER_TYPE)?.stringValue
                    )
                )
            }
        }
    }

    fun parseJsonSchema(data: List<Byte>) = convertApplicationJsonBytesToJson(data)

    // strongly-typed objects to JSON

    fun toJsonValue(resourceList: List<PropertyMetadata>) =
        Json.JsonValue(resourceList.map { (it as CommonRulesPropertyMetadata).toJsonValue() })

    private fun CommonRulesPropertyMetadata.toJsonValue(): Json.JsonValue = Json.JsonValue(
        sequence {
            yield(Pair(Json.JsonValue(PropertyResourceFields.RESOURCE), Json.JsonValue(resource)))
            if (!canGet)
                yield(Pair(Json.JsonValue(PropertyResourceFields.CAN_GET), if (canGet) Json.TrueValue else Json.FalseValue))
            if (canSet != PropertySetAccess.NONE)
                yield(Pair(Json.JsonValue(PropertyResourceFields.CAN_SET), Json.JsonValue(canSet)))
            if (canSubscribe)
                yield(Pair(Json.JsonValue(PropertyResourceFields.CAN_SUBSCRIBE), if (canSubscribe) Json.TrueValue else Json.FalseValue))
            if (requireResId)
                yield(Pair(Json.JsonValue(PropertyResourceFields.REQUIRE_RES_ID), if (requireResId) Json.TrueValue else Json.FalseValue))
            if (mediaTypes.size != 1 || mediaTypes[0] != CommonRulesKnownMimeTypes.APPLICATION_JSON)
                yield(Pair(
                    Json.JsonValue(PropertyResourceFields.MEDIA_TYPE),
                    Json.JsonValue(mediaTypes.map { s -> Json.JsonValue(s) })
                ))
            if (encodings.size != 1 || encodings[0] != PropertyDataEncoding.ASCII)
                yield(Pair(
                    Json.JsonValue(PropertyResourceFields.ENCODINGS),
                    Json.JsonValue(encodings.map { s -> Json.JsonValue(s) })
                ))
            if (schema != null)
                yield(Pair(Json.JsonValue(PropertyResourceFields.SCHEMA), Json.parse(schema!!)))
            if (canPaginate)
                yield(Pair(Json.JsonValue(PropertyResourceFields.CAN_PAGINATE), if (canPaginate) Json.TrueValue else Json.FalseValue))
            if (columns.any())
                yield(Pair(
                    Json.JsonValue(PropertyResourceFields.COLUMNS),
                    Json.JsonValue(columns.map { c -> c.toJsonValue() })
                ))
        }.toMap()
    )

    private fun bytesToJsonArray(list: List<Byte>) = list.map { Json.JsonValue(it.toDouble()) }
    fun toJsonValue(deviceInfo: MidiCIDeviceInfo): Json.JsonValue {
        return Json.JsonValue(
            mapOf(
                Pair(
                    Json.JsonValue(DeviceInfoPropertyNames.MANUFACTURER_ID),
                    Json.JsonValue(bytesToJsonArray(deviceInfo.manufacturerIdBytes()))
                ),
                Pair(
                    Json.JsonValue(DeviceInfoPropertyNames.FAMILY_ID),
                    Json.JsonValue(bytesToJsonArray(deviceInfo.familyIdBytes()))
                ),
                Pair(
                    Json.JsonValue(DeviceInfoPropertyNames.MODEL_ID),
                    Json.JsonValue(bytesToJsonArray(deviceInfo.modelIdBytes()))
                ),
                Pair(
                    Json.JsonValue(DeviceInfoPropertyNames.VERSION_ID),
                    Json.JsonValue(bytesToJsonArray(deviceInfo.versionIdBytes()))
                ),
                Pair(Json.JsonValue(DeviceInfoPropertyNames.MANUFACTURER), Json.JsonValue(deviceInfo.manufacturer)),
                Pair(Json.JsonValue(DeviceInfoPropertyNames.FAMILY), Json.JsonValue(deviceInfo.family)),
                Pair(Json.JsonValue(DeviceInfoPropertyNames.MODEL), Json.JsonValue(deviceInfo.model)),
                Pair(Json.JsonValue(DeviceInfoPropertyNames.VERSION), Json.JsonValue(deviceInfo.version)),
            ) + if (deviceInfo.serialNumber != null) mapOf(
                Pair(Json.JsonValue(DeviceInfoPropertyNames.SERIAL_NUMBER), Json.JsonValue(deviceInfo.serialNumber!!)),
            ) else mapOf()
        )
    }

    fun toJsonValue(channelList: MidiCIChannelList) =
        if (channelList.channels.isEmpty()) null else Json.JsonValue(channelList.channels.map { it.toJson() })

    private fun MidiCIChannel.toJson() = Json.JsonValue(mapOf(
        Json.JsonValue(ChannelInfoPropertyNames.TITLE) to Json.JsonValue(title),
        Json.JsonValue(ChannelInfoPropertyNames.CHANNEL) to Json.JsonValue(channel.toDouble()),
        Json.JsonValue(ChannelInfoPropertyNames.PROGRAM_TITLE) to
                if (programTitle == null) null else Json.JsonValue(programTitle),
        Json.JsonValue(ChannelInfoPropertyNames.BANK_PC) to
                if (bankPC.all { it?.toInt() == 0 }) null else Json.JsonValue(bankPC.map { Json.JsonValue(it?.toDouble() ?: 0.0) }),
        Json.JsonValue(ChannelInfoPropertyNames.CLUSTER_CHANNEL_START) to
                if ((clusterChannelStart ?: 0) <= 1) null else Json.JsonValue(clusterChannelStart?.toDouble() ?: 0.0),
        Json.JsonValue(ChannelInfoPropertyNames.CLUSTER_LENGTH) to
                if ((clusterChannelStart ?: 0) <= 1) null else Json.JsonValue(clusterLength?.toDouble() ?: 0.0),
        Json.JsonValue(ChannelInfoPropertyNames.CLUSTER_MIDI_MODE) to
                if (clusterMidiMode.toInt() == 3) null else Json.JsonValue(clusterMidiMode.toDouble()),
        Json.JsonValue(ChannelInfoPropertyNames.CLUSTER_TYPE) to
                if (clusterType == null || clusterType == ClusterType.OTHER) null else Json.JsonValue(clusterType)
    ).filterValues { it != null }.map { Pair(it.key, it.value!!) }.toMap())
}

val ObservablePropertyList.resourceList
    get() = values.firstOrNull { it.id == PropertyResourceNames.RESOURCE_LIST }?.let { FoundationalResources.parseResourceList(it.body ) }
val ObservablePropertyList.deviceInfo
    get() = values.firstOrNull { it.id == PropertyResourceNames.DEVICE_INFO }?.let { FoundationalResources.parseDeviceInfo(it.body) }
val ObservablePropertyList.channelList
    get() = values.firstOrNull { it.id == PropertyResourceNames.CHANNEL_LIST }?.let { FoundationalResources.parseChannelList(it.body) }
val ObservablePropertyList.jsonSchema
    get() = values.firstOrNull { it.id == PropertyResourceNames.JSON_SCHEMA }?.let { FoundationalResources.parseJsonSchema(it.body) }
