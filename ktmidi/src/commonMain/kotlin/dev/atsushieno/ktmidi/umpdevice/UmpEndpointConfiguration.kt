package dev.atsushieno.ktmidi.umpdevice

import dev.atsushieno.ktmidi.FunctionBlockDirection
import dev.atsushieno.ktmidi.FunctionBlockMidi1Bandwidth
import dev.atsushieno.ktmidi.FunctionBlockUiHint

class UmpEndpointConfiguration(
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

    fun addFunctionBlock(
        name: String = "",
        midi1: Byte = FunctionBlockMidi1Bandwidth.NO_LIMITATION,
        direction: Byte = FunctionBlockDirection.BIDIRECTIONAL,
        uiHint: Byte = FunctionBlockUiHint.UNKNOWN,
        groupCount: Byte = 1,
        isActive: Boolean = true,
        ciVersionFormat: Byte = 0x11,
        maxSysEx8Streams: UByte = 255u) {
        functionBlocks.add(FunctionBlock(functionBlocks.size.toByte(), name, midi1, direction, uiHint,
            functionBlocks.sumOf { it.groupCount.toInt() }.toByte(), groupCount, isActive,
            ciVersionFormat, maxSysEx8Streams))
    }
}