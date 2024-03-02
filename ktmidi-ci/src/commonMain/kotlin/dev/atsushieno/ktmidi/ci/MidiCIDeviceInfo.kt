package dev.atsushieno.ktmidi.ci

import kotlinx.serialization.Serializable

/**
 * `MidiCIDeviceInfo` represents a `DeviceInfo` resource defined in Common Rules for PE specification.
 *
 * There is another similar class `DeviceDetails` which is based on MIDI-CI specification and
 * only contains integer (`Int` and `Short`) properties.
 * `DeviceInfo` resource also covers String properties.
 */
@Serializable
data class MidiCIDeviceInfo(
    var manufacturerId: Int,
    var familyId: Short,
    var modelId: Short,
    var versionId: Int,
    var manufacturer: String,
    var family: String,
    var model: String,
    var version: String,
    var serialNumber: String? = null
) {

    private fun toBytes3(v: Int) = listOf(
        (v and 0x7F).toByte(),
        ((v shr 8) and 0x7F).toByte(),
        ((v shr 16) and 0x7F).toByte()
    )
    private fun toBytes4(v: Int) = listOf(
        (v and 0x7F).toByte(),
        ((v shr 8) and 0x7F).toByte(),
        ((v shr 16) and 0x7F).toByte(),
        ((v shr 24) and 0x7F).toByte()
    )
    private fun toBytes(v: Short) = listOf(
        (v.toInt() and 0x7F).toByte(),
        ((v.toInt() shr 8) and 0x7F).toByte()
    )
    fun manufacturerIdBytes() = toBytes3(manufacturerId)
    fun familyIdBytes() = toBytes(familyId)
    fun modelIdBytes() = toBytes(modelId)
    fun versionIdBytes() = toBytes4(versionId)
}