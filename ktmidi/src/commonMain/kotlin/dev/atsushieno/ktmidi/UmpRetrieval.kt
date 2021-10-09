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

private fun Ump.toPlatformNativeBytes(bytes: ByteArray, value: Int, offset: Int) {
    if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
        bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[offset] = (value and 0xFF).toByte()
    } else {
        bytes[offset] = ((value shr 24) and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 16) and 0xFF).toByte()
        bytes[offset + 2] = ((value shr 8) and 0xFF).toByte()
        bytes[offset + 3] = (value and 0xFF).toByte()
    }
}

fun Ump.toPlatformNativeBytes(bytes: ByteArray, offset: Int) {
    val size = sizeInBytes
    this.toPlatformNativeBytes(bytes, int1, offset)
    if (size != 4)
        this.toPlatformNativeBytes(bytes, int2, offset + 4)
    if (size == 16) {
        this.toPlatformNativeBytes(bytes, int3, offset + 8)
        this.toPlatformNativeBytes(bytes, int4, offset + 12)
    }
}

fun Ump.toPlatformNativeBytes() : ByteArray {
    val bytes = ByteArray(this.sizeInBytes) {0}
    toPlatformNativeBytes(bytes, int1, 0)
    if (messageType > 2)
        toPlatformNativeBytes(bytes, int2, 4)
    if (messageType > 4) {
        toPlatformNativeBytes(bytes, int3, 8)
        toPlatformNativeBytes(bytes, int4, 12)
    }
    return bytes
}

// Second half of the 1st. byte
val Ump.group: Int
    get() = (int1 shr 24) and 0xF

// 2nd. byte
val Ump.statusByte: Int
    get() = (int1 shr 16) and 0xFF

// First half of the 2nd. byte. (for MIDI1, MIDI2, Sysex7, Sysex8, System Messages)
val Ump.eventType: Int
    get() = statusByte and 0xF0

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
val Ump.sysex8Size
    get() = channelInGroup // same bits
val Ump.sysex8StreamId
    get() = midi1Msb
val Ump.mdsId
    get() = channelInGroup // same bits
val Ump.mdsChunkByteSize
    get() = (midi1Msb shl 8) + midi1Lsb
val Ump.mdsChunkCount
    get() = (int2.toUnsigned() / 0x10000).toInt()
val Ump.mdsChunkIndex
    get() = (int2.toUnsigned() % 0x10000).toInt()
val Ump.mdsManufacturerId
    get() = (int3.toUnsigned() / 0x10000).toInt()
val Ump.mdsDeviceId
    get() = (int3.toUnsigned() % 0x10000).toInt()
val Ump.mdsSubId1
    get() = (int4.toUnsigned() / 0x10000).toInt()
val Ump.mdsSubId2
    get() = (int4.toUnsigned() % 0x10000).toInt()

enum class UmpSysexBinaryRetrieverFallback {
    Break,
    Exception
}

class UmpException : Exception {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(message: String, innerException: Exception) : super(message, innerException)
}

object UmpRetriever {
    private fun takeSysex7Bytes(ump: Ump, destinationBytes: MutableList<Byte>, sysex7Size: Int) {
        if (sysex7Size < 1)
            return
        // It is hack, but it just reuses toPlatformNativeBytes() and then pick up the appropriate parts per platform
        val src = ump.toPlatformNativeBytes() // NOTE: memory consumptive
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN)
            destinationBytes.addAll(src.drop(2).take(sysex7Size))
        else {
            destinationBytes.add(src[1])
            if (sysex7Size > 1)
                destinationBytes.add(src[0])
            src.reverse()
            if (sysex7Size > 2)
                destinationBytes.addAll(src.take(sysex7Size - 2))
        }
    }

    fun getSysex7Data(iter: Iterator<Ump>, fallback: UmpSysexBinaryRetrieverFallback = UmpSysexBinaryRetrieverFallback.Break) : List<Byte> {
        val ret = mutableListOf<Byte>()
        if (!iter.hasNext())
            if (fallback == UmpSysexBinaryRetrieverFallback.Break) return ret else throw UmpException("UMP iterator is empty")
        val pStart = iter.next()
        takeSysex7Bytes(pStart, ret, pStart.sysex7Size)
        when (pStart.eventType) {
            Midi2BinaryChunkStatus.SYSEX_IN_ONE_UMP -> return ret
            Midi2BinaryChunkStatus.SYSEX_CONTINUE, Midi2BinaryChunkStatus.SYSEX_END ->
                if (fallback == UmpSysexBinaryRetrieverFallback.Break) return ret else throw UmpException("Unexpected sysex7 non-starter packet appeared")
        }
        while (iter.hasNext()) {
            val pCont = iter.next()
            takeSysex7Bytes(pCont, ret, pCont.sysex7Size)
            when (pCont.eventType) {
                Midi2BinaryChunkStatus.SYSEX_END -> break
                Midi2BinaryChunkStatus.SYSEX_CONTINUE -> continue
                else ->
                    if (fallback == UmpSysexBinaryRetrieverFallback.Break) return ret else throw UmpException("Unexpected sysex7 non-continue packet appeared")
            }
        }
        return ret
    }

    private fun takeSysex8Bytes(ump: Ump, destinationBytes: MutableList<Byte>, sysex8Size: Int) {
        if (sysex8Size < 2)
            return
        // It is hack, but it just reuses toPlatformNativeBytes() and then pick up the appropriate parts per platform
        val src = ump.toPlatformNativeBytes() // NOTE: memory consumptive

        // Note that sysex8Size always contains streamID which should NOT be part of the result.
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN)
            destinationBytes.addAll(src.drop(3).take(sysex8Size))
        else {
            destinationBytes.add(src[0])
            src.reverse()
            if (sysex8Size > 2)
                destinationBytes.addAll(src.drop(8).take(if (sysex8Size > 6) 4 else sysex8Size - 2)) // NOTE: memory consumptive
            if (sysex8Size > 6)
                destinationBytes.addAll(src.drop(4).take(if (sysex8Size > 10) 4 else sysex8Size - 6)) // NOTE: memory consumptive
            if (sysex8Size > 10)
                destinationBytes.addAll(src.take(sysex8Size - 10)) // NOTE: memory consumptive
        }
    }

    fun getSysex8Data(iter: Iterator<Ump>, fallback: UmpSysexBinaryRetrieverFallback = UmpSysexBinaryRetrieverFallback.Break) : List<Byte> {
        val ret = mutableListOf<Byte>()
        if (!iter.hasNext())
            if (fallback == UmpSysexBinaryRetrieverFallback.Break) return ret else throw UmpException("UMP iterator is empty")
        val pStart = iter.next()
        takeSysex8Bytes(pStart, ret, pStart.sysex8Size)
        when (pStart.eventType) {
            Midi2BinaryChunkStatus.SYSEX_IN_ONE_UMP -> return ret
            Midi2BinaryChunkStatus.SYSEX_CONTINUE, Midi2BinaryChunkStatus.SYSEX_END ->
                if (fallback == UmpSysexBinaryRetrieverFallback.Break) return ret else throw UmpException("Unexpected sysex8 non-starter packet appeared")
        }
        while (iter.hasNext()) {
            val pCont = iter.next()
            takeSysex8Bytes(pCont, ret, pCont.sysex8Size)
            when (pCont.eventType) {
                Midi2BinaryChunkStatus.SYSEX_END -> break
                Midi2BinaryChunkStatus.SYSEX_CONTINUE -> continue
                else ->
                    if (fallback == UmpSysexBinaryRetrieverFallback.Break) return ret else throw UmpException("Unexpected sysex8 non-continue packet appeared")
            }
        }
        return ret
    }
}
