package dev.atsushieno.ktmidi.umpdevice

data class UmpDeviceIdentity(
    val manufacturer: Int,
    val family: Short,
    val modelNumber: Short,
    val softwareRevisionLevel: Int) {
    companion object {
        val empty = UmpDeviceIdentity(0, 0, 0, 0)
    }
}