package dev.atsushieno.ktmidi.ci

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

    private fun toBytes(v: Int) = listOf(
        (v and 0x7F).toByte(),
        ((v shr 8) and 0x7F).toByte(),
        ((v shr 16) and 0x7F).toByte()
    )
    private fun toBytes(v: Short) = listOf(
        (v.toInt() and 0x7F).toByte(),
        ((v.toInt() shr 8) and 0x7F).toByte()
    )
    fun manufacturerIdBytes() = toBytes(manufacturerId)
    fun familyIdBytes() = toBytes(familyId)
    fun modelIdBytes() = toBytes(modelId)
    fun versionIdBytes() = toBytes(versionId)
}