package dev.atsushieno.ktmidi.umpdevice

data class UmpEndpointConfiguration(
    var name: String,
    var productInstanceId: String,
    var deviceIdentity: UmpDeviceIdentity,
    var streamConfiguration: UmpStreamConfiguration,
    var isStaticFunctionBlock: Boolean,
    val functionBlocks: MutableList<FunctionBlock>,
) {
    data class UmpStreamConfiguration(
        val supportsMidi1: Boolean,
        val supportsMidi2: Boolean,
        val rxJR: Boolean,
        val txJR: Boolean
    )
}