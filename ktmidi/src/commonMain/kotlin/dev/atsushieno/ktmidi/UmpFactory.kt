@file:Suppress("unused")

package dev.atsushieno.ktmidi

import kotlin.experimental.and

private infix fun Byte.shl(n: Int): Int = this.toInt() shl n
private infix fun Byte.shr(n: Int): Int = this.toInt() shr n
private infix fun Short.shl(n: Int): Int = this.toInt() shl n
private infix fun Short.shr(n: Int): Int = this.toInt() shr n

const val JR_TIMESTAMP_TICKS_PER_SECOND = 31250
const val MIDI_2_0_RESERVED: Byte = 0

typealias UmpMdsHandler = (Long, Long, Int, Int, Any) -> Unit

object UmpFactory {

fun umpGetNumBytes(data: UInt): Int {
    when ((((data and 0xFFFFFFFFu) shr 28) and 0xFu).toInt()) {
        MidiMessageType.UTILITY, MidiMessageType.SYSTEM, MidiMessageType.MIDI1 -> return 4
        MidiMessageType.MIDI2, MidiMessageType.SYSEX7 -> return 8
        MidiMessageType.SYSEX8_MDS -> return 16
    }
    return 0 /* wrong */
}

// 4.8 Utility Messages
fun noop(group: Int): Int {
    return group and 0xF shl 24
}

fun jrClock(group: Int, senderClockTime16: Int): Int {
    return noop(group) + (MidiUtilityStatus.JR_CLOCK shl 16) + senderClockTime16
}

fun jrClock(group: Int, senderClockTimeSeconds: Double): Int {
    val value = (senderClockTimeSeconds * JR_TIMESTAMP_TICKS_PER_SECOND).toInt()
    return noop(group) + (MidiUtilityStatus.JR_CLOCK shl 16) + value
}

fun jrTimestamp(group: Int, senderClockTimestamp16: Int): Int {
    if (senderClockTimestamp16 > 0xFFFF)
        throw IllegalArgumentException("Argument timestamp value must be less than 65536. If you need multiple JR timestamps, use umpJRTimestamps() instead.")
    return noop(group) + (MidiUtilityStatus.JR_TIMESTAMP shl 16) + senderClockTimestamp16
}

fun jrTimestamp(group: Int, senderClockTimestampSeconds: Double) =
    jrTimestamp(group, ((senderClockTimestampSeconds * JR_TIMESTAMP_TICKS_PER_SECOND).toInt()))

fun jrTimestamps(group: Int, senderClockTimestampTicks: Long) : Sequence<Int> = sequence {
    for (i in 0 until senderClockTimestampTicks / 0x10000)
        yield(jrTimestamp(group, 0xFFFF))
    yield(jrTimestamp(group, (senderClockTimestampTicks % 0x10000).toInt()))
}

fun jrTimestamps(group: Int, senderClockTimestampSeconds: Double) =
     jrTimestamps(group, (senderClockTimestampSeconds * JR_TIMESTAMP_TICKS_PER_SECOND).toLong())

// 4.3 System Common and System Real Time Messages
fun systemMessage(group: Int, status: Byte, midi1Byte2: Byte, midi1Byte3: Byte): Int {
    return (MidiMessageType.SYSTEM shl 28) + (group and 0xF shl 24) + (status.toUnsigned() shl 16) + (midi1Byte2.toUnsigned() and 0x7F shl 8) + (midi1Byte3.toUnsigned() and 0x7F)
}

// 4.1 MIDI 1.0 Channel Voice Messages
fun midi1Message(group: Int, code: Byte, channel: Int, byte3: Byte, byte4: Byte): Int {
    return (MidiMessageType.MIDI1 shl 28) + (group and 0xF shl 24) + ((code.toUnsigned() and 0xF0) + (channel and 0xF) shl 16) + (byte3.toUnsigned() and 0x7F shl 8) + (byte4.toUnsigned() and 0x7F)
}

fun midi1NoteOff(group: Int, channel: Int, note: Byte, velocity: Byte): Int {
    return midi1Message(group, MidiChannelStatus.NOTE_OFF.toByte(), channel, note and 0x7F, velocity and 0x7F)
}

fun midi1NoteOn(group: Int, channel: Int, note: Byte, velocity: Byte): Int {
    return midi1Message(group, MidiChannelStatus.NOTE_ON.toByte(), channel, note and 0x7F, velocity and 0x7F)
}

fun midi1PAf(group: Int, channel: Int, note: Byte, data: Byte): Int {
    return midi1Message(group, MidiChannelStatus.PAF.toByte(), channel, note and 0x7F, data and 0x7F)
}

fun midi1CC(group: Int, channel: Int, index: Byte, data: Byte): Int {
    return midi1Message(group, MidiChannelStatus.CC.toByte(), channel, index and 0x7F, data and 0x7F)
}

fun midi1Program(group: Int, channel: Int, program: Byte): Int {
    return midi1Message(group, MidiChannelStatus.PROGRAM.toByte(), channel, program and 0x7F, MIDI_2_0_RESERVED)
}

fun midi1CAf(group: Int, channel: Int, data: Byte): Int {
    return midi1Message(group, MidiChannelStatus.CAF.toByte(), channel, data and 0x7F, MIDI_2_0_RESERVED)
}

fun midi1PitchBendDirect(group: Int, channel: Int, data: Short): Int {
    return midi1Message(
        group,
        MidiChannelStatus.PITCH_BEND.toByte(),
        channel,
        (data.toInt() and 0x7F).toByte(),
        (data shr 7 and 0x7F).toByte()
    )
}

fun midi1PitchBendSplit(group: Int, channel: Int, dataLSB: Byte, dataMSB: Byte): Int {
    return midi1Message(group, MidiChannelStatus.PITCH_BEND.toByte(), channel, dataLSB and 0x7F, dataMSB and 0x7F)
}

fun midi1PitchBend(group: Int, channel: Int, data: Short): Int {
    val u = data + 8192
    return midi1Message(
        group,
        MidiChannelStatus.PITCH_BEND.toByte(),
        channel,
        (u and 0x7F).toByte(),
        (u shr 7 and 0x7F).toByte()
    )
}

// 4.2 MIDI 2.0 Channel Voice Messages
// They take Int arguments to avoid unexpected negative value calculation.
// Instead, argument names explicitly give their types.

fun midi2ChannelMessage8_8_16_16(
    group: Int, code: Int, channel: Int, byte3: Int, byte4: Int,
    short1: Int, short2: Int
): Long {
    val int1 = ((MidiMessageType.MIDI2 shl 28) +
            ((group and 0xF) shl 24) +
            (((code and 0xF0) + (channel and 0xF)) shl 16) +
            (byte3 shl 8) + byte4
            ).toLong()
    val int2 = (((short1.toUnsigned() and 0xFFFF) shl 16) + (short2.toUnsigned() and 0xFFFF))
    return (int1 shl 32) + int2
}

fun midi2ChannelMessage8_8_32(
    group: Int, code: Int, channel: Int, byte3: Int, byte4: Int,
    rest32: Long
): Long {
    val int1 = ((MidiMessageType.MIDI2 shl 28) +
            (group and 0xF shl 24) +
            ((code and 0xF0) + (channel and 0xF) shl 16) +
            (byte3 shl 8) + byte4
            ).toLong()
    return ((int1 shl 32).toULong() + rest32.toULong()).toLong()
}

fun pitch7_9(pitch: Double): Int {
    val actual = if (pitch < 0.0) 0.0 else if (pitch >= 128.0) 128.0 else pitch
    val semitone = actual.toInt()
    val microtone: Double = actual - semitone
    return (semitone shl 9) + (microtone * 512.0).toInt()
}

fun pitch7_9Split(semitone: Byte, microtone0To1: Double): Int {
    var ret = (semitone and 0x7F) shl 9
    val actual = if (microtone0To1 < 0.0) 0.0 else if (microtone0To1 > 1.0) 1.0 else microtone0To1
    ret += (actual * 512.0).toInt()
    return ret
}

fun midi2NoteOff(
    group: Int,
    channel: Int,
    note: Int,
    attributeType8: Byte,
    velocity16: Int,
    attributeData16: Int
): Long {
    return midi2ChannelMessage8_8_16_16(
        group,
        MidiChannelStatus.NOTE_OFF,
        channel,
        note and 0x7F, attributeType8.toInt(), velocity16, attributeData16
    )
}

fun midi2NoteOn(
    group: Int,
    channel: Int,
    note: Int,
    attributeType8: Byte,
    velocity16: Int,
    attributeData16: Int
): Long {
    return midi2ChannelMessage8_8_16_16(
        group,
        MidiChannelStatus.NOTE_ON,
        channel,
        note and 0x7F, attributeType8.toInt(), velocity16, attributeData16
    )
}

fun midi2PAf(group: Int, channel: Int, note: Int, data32: Long): Long {
    return midi2ChannelMessage8_8_32(
        group,
        MidiChannelStatus.PAF,
        channel,
        note and 0x7F, MIDI_2_0_RESERVED.toInt(), data32
    )
}

fun midi2PerNoteRCC(group: Int, channel: Int, note: Int, index8: Int, data32: Long): Long {
    return midi2ChannelMessage8_8_32(
        group,
        MidiChannelStatus.PER_NOTE_RCC,
        channel,
        note and 0x7F, index8, data32
    )
}

fun midi2PerNoteACC(group: Int, channel: Int, note: Int, index8: Int, data32: Long): Long {
    return midi2ChannelMessage8_8_32(
        group,
        MidiChannelStatus.PER_NOTE_ACC,
        channel,
        note and 0x7F, index8, data32
    )
}

fun midi2PerNoteManagement(group: Int, channel: Int, note: Int, optionFlags: Int): Long {
    return midi2ChannelMessage8_8_32(
        group,
        MidiChannelStatus.PER_NOTE_MANAGEMENT,
        channel,
        note and 0x7F, optionFlags and 3, 0
    )
}

fun midi2CC(group: Int, channel: Int, index8: Int, data32: Long): Long {
    return midi2ChannelMessage8_8_32(
        group,
        MidiChannelStatus.CC,
        channel,
        index8 and 0x7F, MIDI_2_0_RESERVED.toInt(), data32
    )
}

fun midi2RPN(
    group: Int,
    channel: Int,
    bankAkaMSB8: Int,
    indexAkaLSB8: Int,
    dataAkaDTE32: Long
): Long {
    return midi2ChannelMessage8_8_32(
        group,
        MidiChannelStatus.RPN,
        channel,
        bankAkaMSB8 and 0x7F, indexAkaLSB8 and 0x7F, dataAkaDTE32
    )
}

fun midi2NRPN(
    group: Int,
    channel: Int,
    bankAkaMSB8: Int,
    indexAkaLSB8: Int,
    dataAkaDTE32: Long
): Long {
    return midi2ChannelMessage8_8_32(
        group,
        MidiChannelStatus.NRPN,
        channel,
        bankAkaMSB8 and 0x7F, indexAkaLSB8 and 0x7F, dataAkaDTE32
    )
}

fun midi2RelativeRPN(
    group: Int,
    channel: Int,
    bankAkaMSB8: Int,
    indexAkaLSB8: Int,
    dataAkaDTE32: Long
): Long {
    return midi2ChannelMessage8_8_32(
        group,
        MidiChannelStatus.RELATIVE_RPN,
        channel,
        bankAkaMSB8 and 0x7F, indexAkaLSB8 and 0x7F, dataAkaDTE32
    )
}

fun midi2RelativeNRPN(
    group: Int,
    channel: Int,
    bankAkaMSB8: Int,
    indexAkaLSB8: Int,
    dataAkaDTE32: Long
): Long {
    return midi2ChannelMessage8_8_32(
        group,
        MidiChannelStatus.RELATIVE_NRPN,
        channel,
        bankAkaMSB8 and 0x7F, indexAkaLSB8 and 0x7F, dataAkaDTE32
    )
}

fun midi2Program(
    group: Int,
    channel: Int,
    optionFlags: Int,
    program8: Int,
    bankMSB8: Int,
    bankLSB8: Int
): Long {
    return midi2ChannelMessage8_8_32(
        group,
        MidiChannelStatus.PROGRAM,
        channel,
        MIDI_2_0_RESERVED.toInt(),
        optionFlags and 1,
        ((program8 and 0x7F shl 24) + (bankMSB8 shl 8) + bankLSB8).toLong()
    )
}

fun midi2CAf(group: Int, channel: Int, data32: Long): Long {
    return midi2ChannelMessage8_8_32(
        group,
        MidiChannelStatus.CAF,
        channel,
        MIDI_2_0_RESERVED.toInt(),
        MIDI_2_0_RESERVED.toInt(),
        data32
    )
}

fun midi2PitchBendDirect(group: Int, channel: Int, data32: Long): Long {
    return midi2ChannelMessage8_8_32(
        group,
        MidiChannelStatus.PITCH_BEND,
        channel,
        MIDI_2_0_RESERVED.toInt(),
        MIDI_2_0_RESERVED.toInt(),
        data32
    )
}

fun midi2PitchBend(group: Int, channel: Int, data32: Long): Long {
    return midi2PitchBendDirect(group, channel, 0x80000000 + data32)
}

fun midi2PerNotePitchBendDirect(group: Int, channel: Int, note: Int, data32: Long): Long {
    return midi2ChannelMessage8_8_32(
        group,
        MidiChannelStatus.PER_NOTE_PITCH_BEND,
        channel,
        note and 0x7F, MIDI_2_0_RESERVED.toInt(), data32
    )
}

fun midi2PerNotePitchBend(group: Int, channel: Int, note: Int, data32: Long): Long {
    return midi2PerNotePitchBendDirect(group, channel, note, 0x80000000 + data32)
}

// Common utility functions for sysex support
private fun getByteFromUInt32(src: UInt, index: Int): Byte {
    return (src shr (7 - index) * 8 and 0xFFu).toByte()
}

private fun getByteFromUInt64(src: UInt, index: Int): Byte {
    return (src shr (7 - index) * 8 and 0xFFu).toByte()
}

private fun sysexGetPacketCount(numBytes: Int, radix: Int): Int {
    return if (numBytes <= radix) 1 else (numBytes / radix + if (numBytes % radix != 0) 1 else 0)
}

private fun readInt32Bytes(bytes: ByteArray, offset: Int = 0): Int {
    var ret: UInt = 0u
    for (i in 0..3)
        ret += bytes[i + offset].toUnsigned().toUInt() shl (7 - i) * 8
    return ret.toInt()
}

private fun readInt64Bytes(bytes: List<Byte>, offset: Int): Long {
    var ret: ULong = 0u
    for (i in 0..7)
        ret += bytes[i + offset].toUnsigned().toULong() shl ((7 - i) * 8)
    return ret.toLong()
}

private fun sysexGetPacketOf(
    shouldGetResult2: Boolean, group: Int, numBytes8: Int, srcData: List<Byte>, index: Int,
    messageType: Int, radix: Int, hasStreamId: Boolean, streamId: Byte = 0
): Pair<Long, Long> {
    val dst8 = ByteArray(16) { 0 }
    dst8[0] = ((messageType shl 4) + (group and 0xF)).toByte()
    val status: Int
    val size: Int
    if (numBytes8 <= radix) {
        status = Midi2BinaryChunkStatus.SYSEX_IN_ONE_UMP
        size = numBytes8 // single packet message
    } else if (index == 0) {
        status = Midi2BinaryChunkStatus.SYSEX_START
        size = radix
    } else {
        val isEnd = index == sysexGetPacketCount(numBytes8, radix) - 1
        if (isEnd) {
            size = if (numBytes8 % radix != 0) numBytes8 % radix else radix
            status = Midi2BinaryChunkStatus.SYSEX_END
        } else {
            size = radix
            status = Midi2BinaryChunkStatus.SYSEX_CONTINUE
        }
    }
    dst8[1] = (status + size + if (hasStreamId) 1 else 0).toByte()
    if (hasStreamId) dst8[2] = streamId
    val dstOffset = if (hasStreamId) 3 else 2
    var i = 0
    var j = index * radix
    while (i < size) {
        dst8[i + dstOffset] = srcData[j]
        i++
        j++
    }
    val result1 = readInt64Bytes(dst8.asList(), 0)
    val result2 = if (shouldGetResult2) readInt64Bytes(dst8.asList(), 8) else 0
    return Pair(result1, result2)
}

// 4.4 System Exclusive 7-Bit Messages
fun sysex7Direct(
    group: Int,
    status: Byte,
    numBytes: Int,
    data1: Byte,
    data2: Byte,
    data3: Byte,
    data4: Byte,
    data5: Byte,
    data6: Byte
): Long {
    return ((((MidiMessageType.SYSEX7 shl 28) + (group and 0xF shl 24) + (status + numBytes shl 16)).toULong() shl 32) +
            (data1.toULong() shl 40) + (data2.toULong() shl 32) + (data3.toUInt() shl 24) + (data4.toUInt() shl 16) + (data5.toUInt() shl 8) + data6.toUByte())
        .toLong()
}

fun sysex7GetSysexLength(srcData: List<Byte>): Int {
    var i = 0
    while (i < srcData.size && srcData[i] != 0xF7.toByte())
        i++
    /* This function automatically detects if 0xF0 is prepended and reduce length if it is. */
    return i - if (srcData[0] == 0xF0.toByte()) 1 else 0
}

fun sysex7GetPacketCount(numSysex7Bytes: Int): Int {
    return sysexGetPacketCount(numSysex7Bytes, 6)
}

fun sysex7GetPacketOf(group: Int, numBytes: Int, srcData: List<Byte>, index: Int): Long {
    val srcOffset = if (numBytes > 0 && srcData[0] == 0xF0.toByte()) 1 else 0
    val result = sysexGetPacketOf(
        false,
        group,
        numBytes,
        srcData.drop(srcOffset),
        index,
        MidiMessageType.SYSEX7,
        6,
        false,
        0
    )
    return result.first
}

fun sysex7Process(
    group: Int,
    sysex: List<Byte>,
    sendUMP64: (Long, Any?) -> Unit,
    context: Any?
) {
    val length: Int = sysex7GetSysexLength(sysex)
    val numPackets: Int = sysex7GetPacketCount(length)
    for (p in 0 until numPackets) {
        val ump = sysex7GetPacketOf(group, length, sysex, p)
        sendUMP64(ump, context)
    }
}

// 4.5 System Exclusive 8-Bit Messages
fun sysex8GetPacketCount(numBytes: Int): Int {
    return sysexGetPacketCount(numBytes, 13)
}

fun sysex8GetPacketOf(
    group: Int,
    streamId: Byte,
    numBytes: Int,
    srcData: List<Byte>,
    index: Int,
): Pair<Long, Long> {
    return sysexGetPacketOf(
        true,
        group,
        numBytes,
        srcData,
        index,
        MidiMessageType.SYSEX8_MDS,
        13,
        true,
        streamId
    )
}

fun sysex8Process(
    group: Int,
    sysex: List<Byte>,
    sysexLength: Int,
    streamId: Byte,
    sendUMP128: (Long, Long, Any?) -> Unit,
    context: Any?
) {
    val numPackets: Int = sysex8GetPacketCount(sysexLength)
    for (p in 0 until numPackets) {
        val result = sysex8GetPacketOf(group, streamId, sysexLength, sysex, p)
        sendUMP128(result.first, result.second, context)
    }
}

// Mixed Data Sets
fun mdsGetChunkCount(numTotalBytesInMDS: Int) : Int {
    val radix = 14 * 0x10000
    return numTotalBytesInMDS / radix + if (numTotalBytesInMDS % radix != 0) 1 else 0
}

fun mdsGetPayloadCount(numTotalBytesinChunk: Int) : Int {
    return numTotalBytesinChunk / 14 + if (numTotalBytesinChunk % 14 != 0) 1 else 0
}

private fun fillShort(dst8: ByteArray, offset: Int, v16: Int) {
    dst8[offset] = (v16 / 0x100).toByte()
    dst8[offset + 1] = (v16 % 0x100).toByte()
}

fun mdsGetHeader(group: Byte, mdsId: Byte, numBytesInChunk16: Int, numChunks16: Int, chunkIndex16: Int,
manufacturerId16: Int, deviceId16: Int, subId16: Int, subId2_16: Int): Pair<Long,Long> {
    val dst8 = ByteArray(16)

    dst8[0] = ((MidiMessageType.SYSEX8_MDS shl 4) + (group and 0xF)).toByte()
    dst8[1] = (Midi2BinaryChunkStatus.MDS_HEADER + mdsId).toByte()
    fillShort(dst8, 2, numBytesInChunk16)
    fillShort(dst8, 4, numChunks16)
    fillShort(dst8, 6, chunkIndex16)
    fillShort(dst8, 8, manufacturerId16)
    fillShort(dst8, 10, deviceId16)
    fillShort(dst8, 12, subId16)
    fillShort(dst8, 14, subId2_16)

    return Pair(readInt64Bytes(dst8.asList(), 0), readInt64Bytes(dst8.asList(), 8))
}

fun mdsGetPayloadOf(group: Byte, mdsId: Byte, numBytes16: Int, srcData:List<Byte>, offset: Int) : Pair<Long, Long> {
    val dst8 = ByteArray(16)

    dst8[0] = ((MidiMessageType.SYSEX8_MDS shl 4) + (group and 0xF)).toByte()
    dst8[1] = (Midi2BinaryChunkStatus.MDS_PAYLOAD + mdsId).toByte()

    val radix = 14
    val size = if (numBytes16 < radix) numBytes16 % radix else radix

    var i = 0
    var j = offset
    while (i < size) {
        dst8[i + 2] = srcData[j]
        i++
        j++
    }

    return Pair(readInt64Bytes(dst8.asList(), 0), readInt64Bytes(dst8.asList(), 8))
}

fun mdsProcess(group: Byte, mdsId: Byte, data: List<Byte>, length: Int, sendUmp: UmpMdsHandler, context: Any) {
    val numChunks = mdsGetChunkCount(length)
    for (c in 0 until numChunks) {
        val maxChunkSize = 14 * 65535
        val chunkSize = if (c + 1 == numChunks) length % maxChunkSize else maxChunkSize
        val numPayloads = mdsGetPayloadCount(chunkSize)
        for (p in 0 until numPayloads) {
            val offset = 14 * (65536 * c + p)
            val result = mdsGetPayloadOf(group, mdsId, chunkSize, data, offset)
            sendUmp(result.first, result.second, c, p, context)
        }
    }
}

}