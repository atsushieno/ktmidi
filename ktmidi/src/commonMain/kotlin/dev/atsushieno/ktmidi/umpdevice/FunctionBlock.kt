package dev.atsushieno.ktmidi.umpdevice

import dev.atsushieno.ktmidi.FunctionBlockDirection
import dev.atsushieno.ktmidi.FunctionBlockMidi1Bandwidth
import dev.atsushieno.ktmidi.FunctionBlockUiHint

class FunctionBlock(
    var functionBlockIndex: Byte,
    var name: String = "",
    var midi1: Byte = FunctionBlockMidi1Bandwidth.NO_LIMITATION,
    var direction: Byte = FunctionBlockDirection.BIDIRECTIONAL,
    var uiHint: Byte = FunctionBlockUiHint.UNKNOWN,
    var groupIndex: Byte = 0,
    var groupCount: Byte = 1,
    var isActive: Boolean = true,
    var ciVersionFormat: Byte = 0x11,
    var maxSysEx8Streams: UByte = 255u
)