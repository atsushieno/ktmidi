package dev.atsushieno.ktmidi

import io.ktor.utils.io.core.*

val Ump.groupByte: Int
    get() = int1 shr 24

// First half of the 1st. byte
// 32bit: UMP message type 0 (NOP / Clock), 1 (System) and 2 (MIDI 1.0)
// 64bit: UMP message type 3 (SysEx7) and 4 (MIDI 2.0)
// 128bit: UMP message type 5 (SysEx8 and Mixed Data Set)
val Ump.messageType: Int
    get() = (int1 shr 28) and 0xF

val Ump.sizeInBytes
    get() = when(messageType) {
        MidiMessageType.SYSEX8_MDS -> 16
        MidiMessageType.SYSEX7, MidiMessageType.MIDI2 -> 8
        else -> 4
    }

private fun Ump.copyInto(bytes: ByteArray, value: Int, offset: Int) {
    if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
        bytes[offset + 3] = (value / 0x1000000).toByte()
        bytes[offset + 2] = ((value / 0x10000) % 0x100).toByte()
        bytes[offset + 1] = ((value / 0x100) % 0x100).toByte()
        bytes[offset] = (value % 0x100).toByte()
    } else {
        bytes[offset] = (value / 0x1000000).toByte()
        bytes[offset + 1] = ((value / 0x10000) % 0x100).toByte()
        bytes[offset + 2] = ((value / 0x100) % 0x100).toByte()
        bytes[offset + 3] = (value % 0x100).toByte()
    }
}

fun Ump.copyInto(bytes: ByteArray, offset: Int) {
    val size = sizeInBytes
    this.copyInto(bytes, int1, offset)
    if (size != 4)
        this.copyInto(bytes, int2, offset + 4)
    if (size == 16) {
        this.copyInto(bytes, int3, offset + 8)
        this.copyInto(bytes, int4, offset + 12)
    }
}

fun Ump.toBytes() : ByteArray {
    val bytes = ByteArray(this.sizeInBytes) {0}
    copyInto(bytes, this.int1, 0)
    if (messageType > 2)
        copyInto(bytes, this.int2, 4)
    if (messageType > 4) {
        copyInto(bytes, this.int3, 8)
        copyInto(bytes, this.int4, 12)
    }
    return bytes
}

// Second half of the 1st. byte
val Ump.group: Int
    get() = (int1 shr 24) and 0xF

// 2nd. byte
val Ump.statusByte: Int
    get() = (int1 shr 16) and 0xFF

// First half of the 2nd. byte.
// This makes sense only for MIDI 1.0, MIDI 2.0, and System messages
val Ump.eventType: Int
    get() =
        when (messageType) {
            MidiMessageType.MIDI1, MidiMessageType.MIDI2 -> statusByte and 0xF0
            else -> statusByte
        }

// Second half of the 2nd. byte
val Ump.channelInGroup: Int // 0..15
    get() = statusByte and 0xF

val Ump.groupAndChannel: Int // 0..255
    get() = group shl 4 and channelInGroup

val Ump.isJRClock
    get() = messageType == MidiMessageType.UTILITY && eventType == MidiUtilityStatus.JR_CLOCK
val Ump.jrClock
    get() = if (isJRClock) (midi1Msb shl 8) + midi1Lsb else 0

val Ump.isJRTimestamp
    get()= messageType == MidiMessageType.UTILITY && eventType == MidiUtilityStatus.JR_TIMESTAMP
val Ump.jrTimestamp
    get() = if(isJRTimestamp) (midi1Msb shl 8) + midi1Lsb else 0

// 3rd. byte for MIDI 1.0 message
val Ump.midi1Msb: Int
    get() = (int1 and 0xFF00) shr 8

// 4th. byte for MIDI 1.0 message
val Ump.midi1Lsb: Int
    get() = int1 and 0xFF

val Ump.midi1Note
    get() = midi1Msb
val Ump.midi1Velocity
    get() = midi1Lsb
val Ump.midi1PAfData
    get() = midi1Lsb
val Ump.midi1CCIndex
    get() = midi1Msb
val Ump.midi1CCData
    get() = midi1Lsb
val Ump.midi1Program
    get() = midi1Msb
val Ump.midi1CAf
    get() = midi1Msb
val Ump.midi1PitchBendData
    get() = midi1Msb + midi1Lsb * 0x80
val Ump.sysex7Size
    get() = channelInGroup // same bits
val Ump.midi2Note
    get() = midi1Msb
val Ump.midi2NoteAttributeType
    get() = midi1Lsb
val Ump.midi2Velocity16
    get() = (int2.toUnsigned() / 0x10000).toInt()
val Ump.midi2NoteAttributeData
    get() = (int2.toUnsigned() % 0x10000).toInt()
val Ump.midi2PAfData
    get() = int2.toUInt()
val Ump.midi2PerNoteRCCIndex
    get() = midi1Lsb
val Ump.midi2PerNoteRCCData
    get() = int2.toUInt()
val Ump.midi2PerNoteACCIndex
    get() = midi1Lsb
val Ump.midi2PerNoteACCData
    get() = int2.toUInt()
val Ump.midi2PerNoteManagementOptions
    get() = midi1Lsb
val Ump.midi2CCIndex
    get() = midi1Msb
val Ump.midi2CCData
    get() = int2.toUInt()
val Ump.midi2RpnMsb
    get() = midi1Msb
val Ump.midi2RpnLsb
    get() = midi1Lsb
val Ump.midi2RpnData
    get() = int2.toUInt()
val Ump.midi2NrpnMsb
    get() = midi1Msb
val Ump.midi2NrpnLsb
    get() = midi1Lsb
val Ump.midi2NrpnData
    get() = int2.toUInt()
val Ump.midi2ProgramOptions
    get() = midi1Lsb
val Ump.midi2ProgramProgram
    get() = (int2.toUnsigned() / 0x1000000).toInt()
val Ump.midi2ProgramBankMsb
    get() = ((int2.toUnsigned() / 0x100) % 0x100).toInt()
val Ump.midi2ProgramBankLsb
    get() = (int2.toUnsigned() % 0x100).toInt()
val Ump.midi2CAf
    get() = int2.toUInt()
val Ump.midi2PitchBendData
    get() = int2.toUInt()
val Ump.midi2Sysex8Size
    get() = channelInGroup // same bits
val Ump.midi2Sysex8StreamId
    get() = midi1Msb
val Ump.midi2MdsId
    get() = channelInGroup // same bits
val Ump.midi2MdsChunkByteSize
    get() = (midi1Msb shl 8) + midi1Lsb
val Ump.midi2MdsChunkCount
    get() = (int2.toUnsigned() / 0x10000).toInt()
val Ump.midi2MdsChunkIndex
    get() = (int2.toUnsigned() % 0x10000).toInt()
val Ump.midi2MdsManufacturerId
    get() = (int3.toUnsigned() / 0x10000).toInt()
val Ump.midi2MdsDeviceId
    get() = (int3.toUnsigned() % 0x10000).toInt()
val Ump.midi2MdsSubId1
    get() = (int4.toUnsigned() / 0x10000).toInt()
val Ump.midi2MdsSubId2
    get() = (int4.toUnsigned() % 0x10000).toInt()

