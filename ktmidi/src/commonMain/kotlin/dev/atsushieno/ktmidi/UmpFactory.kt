@file:Suppress("unused")

package dev.atsushieno.ktmidi

import dev.atsushieno.ktmidi.ci.DeviceDetails
import io.ktor.utils.io.core.*
import kotlin.experimental.and

internal infix fun Byte.shl(n: Int): Int = this.toInt() shl n
internal infix fun Byte.shr(n: Int): Int = this.toInt() shr n
internal infix fun Short.shl(n: Int): Int = this.toInt() shl n
internal infix fun Short.shr(n: Int): Int = this.toInt() shr n

const val JR_TIMESTAMP_TICKS_PER_SECOND = 31250
const val MIDI_2_0_RESERVED: Byte = 0

typealias UmpMdsHandler = (Long, Long, Int, Int, Any?) -> Unit

object UmpFactory {

    fun umpGetNumBytes(data: UInt): Int {
        when ((((data and 0xFFFFFFFFu) shr 28) and 0xFu).toInt()) {
            MidiMessageType.UTILITY, MidiMessageType.SYSTEM, MidiMessageType.MIDI1 -> return 4
            MidiMessageType.MIDI2, MidiMessageType.SYSEX7 -> return 8
            MidiMessageType.SYSEX8_MDS -> return 16
        }
        return 0 /* wrong */
    }

    // 4.8 Utility Messages (note that they became groupless since 2023 June updates)
    fun noop(): Int {
        return 0
    }

    @Deprecated("group has vanished in UMP June 2023 updates", replaceWith = ReplaceWith("noop()"))
    fun noop(group: Int) = noop()

    fun jrClock(senderClockTime16: Int): Int {
        return (MidiUtilityStatus.JR_CLOCK shl 16) + senderClockTime16
    }
    @Deprecated("group has vanished in UMP June 2023 updates", replaceWith = ReplaceWith("jrClock(senderClockTime16)"))
    fun jrClock(group: Int, senderClockTime16: Int) = jrClock(senderClockTime16)

    fun jrClock(senderClockTimeSeconds: Double): Int {
        val value = (senderClockTimeSeconds * JR_TIMESTAMP_TICKS_PER_SECOND).toInt()
        return (MidiUtilityStatus.JR_CLOCK shl 16) + value
    }
    @Deprecated("group has vanished in UMP June 2023 updates", replaceWith = ReplaceWith("jrClock(senderClockTimeSeconds)"))
    fun jrClock(group: Int, senderClockTimeSeconds: Double) = jrClock(senderClockTimeSeconds)

    fun jrTimestamp(senderClockTimestamp16: Int): Int {
        if (senderClockTimestamp16 > 0xFFFF)
            throw IllegalArgumentException("Argument timestamp value must be less than 65536. If you need multiple JR timestamps, use umpJRTimestamps() instead.")
        return (MidiUtilityStatus.JR_TIMESTAMP shl 16) + senderClockTimestamp16
    }
    @Deprecated("group has vanished in UMP June 2023 updates", replaceWith = ReplaceWith("jrTimestamp(senderClockTimestamp16)"))
    fun jrTimestamp(group: Int, senderClockTimestamp16: Int) = jrTimestamp(senderClockTimestamp16)

    fun jrTimestamp(senderClockTimestampSeconds: Double) =
        jrTimestamp(((senderClockTimestampSeconds * JR_TIMESTAMP_TICKS_PER_SECOND).toInt()))
    @Deprecated("group has vanished in UMP June 2023 updates", replaceWith = ReplaceWith("jrTimestamp(senderClockTimestampSeconds)"))
    fun jrTimestamp(group: Int, senderClockTimestampSeconds: Double) = jrTimestamp(senderClockTimestampSeconds)

    fun jrTimestamps(senderClockTimestampTicks: Long): Sequence<Int> = sequence {
        for (i in 0 until senderClockTimestampTicks / 0x10000)
            yield(jrTimestamp(0xFFFF))
        yield(jrTimestamp((senderClockTimestampTicks % 0x10000).toInt()))
    }
    @Deprecated("group has vanished in UMP June 2023 updates", replaceWith = ReplaceWith("jrTimestamps(senderClockTimestampTicks)"))
    fun jrTimestamps(group: Int, senderClockTimestampTicks: Long): Sequence<Int> = jrTimestamps(senderClockTimestampTicks)

    fun jrTimestamps(senderClockTimestampSeconds: Double) =
        jrTimestamps((senderClockTimestampSeconds * JR_TIMESTAMP_TICKS_PER_SECOND).toLong())
    @Deprecated("group has vanished in UMP June 2023 updates", replaceWith = ReplaceWith("jrTimestamps(senderClockTimestampSeconds)"))
    fun jrTimestamps(group: Int, senderClockTimestampSeconds: Double) = jrTimestamps(senderClockTimestampSeconds)

    // June 2023 updates
    fun dctpq(numberOfTicksPerQuarterNote: UShort) = (MidiUtilityStatus.DCTPQ shl 16) + numberOfTicksPerQuarterNote.toInt()

    // June 2023 updates
    fun deltaClockstamp(ticks20: Int) = (MidiUtilityStatus.DELTA_CLOCKSTAMP shl 16) + (ticks20 and 0xFFFFF) // ticks20(bits) - 0..1048575

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

    private fun getPacketCountCommon(numBytes: Int, radix: Int): Int {
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
            status = Midi2BinaryChunkStatus.COMPLETE_PACKET
            size = numBytes8 // single packet message
        } else if (index == 0) {
            status = Midi2BinaryChunkStatus.START
            size = radix
        } else {
            val isEnd = index == getPacketCountCommon(numBytes8, radix) - 1
            if (isEnd) {
                size = if (numBytes8 % radix != 0) numBytes8 % radix else radix
                status = Midi2BinaryChunkStatus.END
            } else {
                size = radix
                status = Midi2BinaryChunkStatus.CONTINUE
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

    fun sysex7GetPacketCount(numSysex7Bytes: Int): Int = getPacketCountCommon(numSysex7Bytes, 6)

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
        context: Any? = null,
        sendUMP64: (Long, Any?) -> Unit = { _, _ -> },
    ) {
        val length: Int = sysex7GetSysexLength(sysex)
        val numPackets: Int = sysex7GetPacketCount(length)
        for (p in 0 until numPackets) {
            val ump = sysex7GetPacketOf(group, length, sysex, p)
            sendUMP64(ump, context)
        }
    }

    fun sysex7(
        group: Int,
        sysex: List<Byte>,
    ): List<Ump> {
        val ret = mutableListOf<Ump>()
        sysex7Process(group, sysex) { l, _ -> ret.add(Ump(l)) }
        return ret
    }

    // 4.5 System Exclusive 8-Bit Messages
    fun sysex8GetPacketCount(numBytes: Int): Int {
        return getPacketCountCommon(numBytes, 13)
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

    @Deprecated("Use another sysex8Process overload that has sendUMP64 as the last parameter")
    fun sysex8Process(
        group: Int,
        sysex: List<Byte>,
        streamId: Byte,
        sendUMP128: (Long, Long, Any?) -> Unit,
        context: Any?
    ) {
        val numPackets: Int = sysex8GetPacketCount(sysex.size)
        for (p in 0 until numPackets) {
            val result = sysex8GetPacketOf(group, streamId, if (sysex.size >= 13) 13 else sysex.size % 13, sysex, p)
            sendUMP128(result.first, result.second, context)
        }
    }

    fun sysex8Process(
        group: Int,
        sysex: List<Byte>,
        streamId: Byte = 0,
        context: Any? = null,
        sendUMP128: (Long, Long, Any?) -> Unit = { _, _, _ -> }
    ) {
        val numPackets: Int = sysex8GetPacketCount(sysex.size)
        for (p in 0 until numPackets) {
            val result = sysex8GetPacketOf(group, streamId, sysex.size, sysex, p)
            sendUMP128(result.first, result.second, context)
        }
    }

    fun sysex8(
        group: Int,
        sysex: List<Byte>,
        streamId: Byte = 0
    ): List<Ump> {
        val ret = mutableListOf<Ump>()
        sysex8Process(group, sysex, streamId) { l1, l2, _ -> ret.add(Ump(l1, l2)) }
        return ret
    }

    // Mixed Data Sets
    fun mdsGetChunkCount(numTotalBytesInMDS: Int): Int {
        val radix = 14 * 0x10000
        return numTotalBytesInMDS / radix + if (numTotalBytesInMDS % radix != 0) 1 else 0
    }

    fun mdsGetPayloadCount(numTotalBytesInChunk: Int): Int {
        return numTotalBytesInChunk / 14 + if (numTotalBytesInChunk % 14 != 0) 1 else 0
    }

    private fun fillShort(dst8: ByteArray, offset: Int, v16: Int) {
        dst8[offset] = (v16 / 0x100).toByte()
        dst8[offset + 1] = (v16 % 0x100).toByte()
    }

    fun mdsGetHeader(
        group: Byte, mdsId: Byte, numBytesInChunk16: Int, numChunks16: Int, chunkIndex16: Int,
        manufacturerId16: Int, deviceId16: Int, subId16: Int, subId2_16: Int
    ): Pair<Long, Long> {
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

    fun mdsGetPayloadOf(group: Byte, mdsId: Byte, numBytes16: Int, srcData: List<Byte>, offset: Int): Pair<Long, Long> {
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

    fun mdsProcess(group: Byte, mdsId: Byte, data: List<Byte>, context: Any? = null, sendUmp: UmpMdsHandler) {
        val numChunks = mdsGetChunkCount(data.size)
        for (c in 0 until numChunks) {
            val maxChunkSize = 14 * 65535
            val chunkSize = if (c + 1 == numChunks) data.size % maxChunkSize else maxChunkSize
            val numPayloads = mdsGetPayloadCount(chunkSize)
            for (p in 0 until numPayloads) {
                val offset = 14 * (65536 * c + p)
                val result = mdsGetPayloadOf(group, mdsId, chunkSize, data, offset)
                sendUmp(result.first, result.second, c, p, context)
            }
        }
    }

    fun mds(
        group: Byte,
        data: List<Byte>,
        mdsId: Byte = 0
    ): List<Ump> {
        val ret = mutableListOf<Ump>()
        mdsProcess(group, mdsId, data) { l1, l2, _, _, _ -> ret.add(Ump(l1, l2)) }
        return ret
    }
    @Deprecated("Use group as Byte", ReplaceWith("mds(group.toByte(), data, mdsId)"))
    fun mds(group: Int, data: List<Byte>, mdsId: Byte = 0) = mds(group.toByte(), data, mdsId)

    // Some common functions for UMP Stream and Flex Data

    private fun textBytesToUmp(text: List<Byte>): Int = // the text can be empty (we call drop() unchecked)
        (if (text.isEmpty()) 0 else text[0] shl 24) +
                (if (text.size < 2) 0 else (text[1] shl 16)) +
                (if (text.size < 3) 0 else (text[2] shl 8)) +
                if (text.size < 4) 0 else text[3]

    // UMP Stream (0xFn) - new in June 2023 updates

    // text split by 14 bytes
    private fun umpStreamTextPacket(format: Byte, status: Byte, text: ByteArray, index: Int, dataPrefix: Byte?): Ump {
        val common = ((MidiMessageType.UMP_STREAM shl 28) + (format shl 26) + (status shl 16)).toUnsigned()
        val first = if (text.size <= index) 0 else text[index]
        return if (dataPrefix != null)
            Ump(
                (common + (dataPrefix shl 8) + (first)).toInt(),
                textBytesToUmp(text.drop(index + 1)),
                textBytesToUmp(text.drop(index + 5)),
                textBytesToUmp(text.drop(index + 9))
            )
        else
            Ump(
                (common + (first shl 8) + if (text.size < index + 2) 0 else text[index + 1]).toInt(),
                textBytesToUmp(text.drop(index + 2)),
                textBytesToUmp(text.drop(index + 6)),
                textBytesToUmp(text.drop(index + 10))
            )
    }

    private fun umpStreamTextProcessCommon(status: Byte, text: ByteArray,
                                           context: Any? = null,
                                           capacity: Int = 14,
                                           dataPrefix: Byte? = null,
                                           sendUMP128: (Ump, Any?) -> Unit = { _, _ -> }
    ) {
        if (text.size <= capacity)
            sendUMP128(umpStreamTextPacket(0, status, text, 0, dataPrefix), context)
        else {
            sendUMP128(umpStreamTextPacket(1, status, text, 0, dataPrefix), context)
            val numPackets = text.size / capacity + if (text.size % capacity > 0) 1 else 0
            (1 until text.size / capacity - if (text.size % capacity != 0) 0 else 1).forEach {
                sendUMP128(umpStreamTextPacket(2, status, text, it * capacity, dataPrefix), context)
            }
            sendUMP128(umpStreamTextPacket(3, status, text, (numPackets - 1) * capacity, dataPrefix), context)
        }
    }

    private fun umpStreamTextCommon(status: Byte, text: ByteArray) : List<Ump> {
        val ret = mutableListOf<Ump>()
        umpStreamTextProcessCommon(status, text) { ump, _ -> ret.add(ump) }
        return ret
    }

    fun endpointDiscovery(umpVersionMajor: Byte, umpVersionMinor: Byte, filterBitmap: Byte) =
        Ump(((umpVersionMajor * 0x100 + umpVersionMinor) + 0xF000_0000L).toInt(),
            filterBitmap.toInt() and 0x1F,
            0, 0)

    fun endpointInfoNotification(umpVersionMajor: Byte, umpVersionMinor: Byte,
                                 isStaticFunctionBlock: Boolean, functionBlockCount: Byte,
                                 midi2Capable: Boolean, midi1Capable: Boolean,
                                 supportsRxJR: Boolean, supportsTxJR: Boolean): Ump =
        Ump((0xF001_0000L + umpVersionMajor * 0x100 + umpVersionMinor).toInt(),
            (functionBlockCount * 0x1_00_0000 +
                    (if (isStaticFunctionBlock) 0x80000000 else 0) +
                    (if (midi2Capable) 0x200 else 0) +
                    (if (midi1Capable) 0x100 else 0) +
                    (if (supportsRxJR) 2 else 0) +
                    if (supportsTxJR) 1 else 0
                    ).toInt(),
            0, 0)

    fun deviceIdentityNotification(device: DeviceDetails) =
        Ump(0xF002_0000L.toInt(),
            device.manufacturer,
            ((device.family.toUnsigned() shl 16) + device.familyModelNumber.toUnsigned()),
            device.softwareRevisionLevel)

    fun endpointNameNotification(name: String) = endpointNameNotification(name.toByteArray())
    fun endpointNameNotification(name: ByteArray) = umpStreamTextCommon(3, name)

    fun productInstanceNotification(id: String) = productInstanceNotification(id.toByteArray())
    fun productInstanceNotification(id: ByteArray) = umpStreamTextCommon(4, id)

    fun streamConfigRequest(protocol: Byte, rxJRTimestamp: Boolean, txJRTimestamp: Boolean) =
        Ump((0xF005_0000L + (protocol.toUnsigned() shl 8) + (if (rxJRTimestamp) 2 else 0) + if (txJRTimestamp) 1 else 0).toInt(),
            0, 0, 0)

    fun streamConfigNotification(protocol: Byte, rxJRTimestamp: Boolean, txJRTimestamp: Boolean) =
        Ump((0xF006_0000L + (protocol.toUnsigned() shl 8) + (if (rxJRTimestamp) 2 else 0) + if (txJRTimestamp) 1 else 0).toInt(),
            0, 0, 0)

    fun functionBlockDiscovery(fbNumber: Byte, filter: Byte) =
        Ump((0xF010_0000L + (fbNumber.toUnsigned() shl 8) + filter.toUnsigned()).toInt(),
            0, 0, 0)

    fun functionBlockInfoNotification(isFbActive: Boolean, fbNumber: Byte, uiHint: Byte, midi1: Byte, direction: Byte,
                                      firstGroup: Byte, numberOfGroupsSpanned: Byte,
                                      midiCIMessageVersionFormat: Byte, maxSysEx8Streams: Int) =
        Ump((0xF011_0000L +
                (if (isFbActive) 0x8000 else 0) + (fbNumber.toUnsigned() shl 8) +
                ((uiHint and 3) shl 4) + ((midi1 and 3) shl 2) + (direction and 3).toUnsigned()).toInt(),
            (firstGroup.toUnsigned() shl 24) + (numberOfGroupsSpanned.toUnsigned() shl 16) +
                    (midiCIMessageVersionFormat.toUnsigned() shl 8) + (maxSysEx8Streams and 0xFF),
            0, 0)

    fun functionBlockNameNotification(blockNumber: Byte, name: String): List<Ump> {
        val ret = mutableListOf<Ump>()
        umpStreamTextProcessCommon(0x12, name.toByteArray(), capacity = 13, dataPrefix = blockNumber) { ump, _ -> ret.add(ump) }
        return ret
    }

    fun startOfClip() = Ump(0xF020_0000L.toInt(), 0, 0, 0)
    fun endOfClip() = Ump(0xF021_0000L.toInt(), 0, 0, 0)

    // Flex Data (new in June 2023 updates)

    private fun flexDataPacket(group: Byte,
                       format: Byte,
                       address: Byte, channel: Byte, statusBank: Byte, status: Byte, text: ByteArray, index: Int) = Ump(
        (MidiMessageType.FLEX_DATA shl 28) + (group shl 24) + (format shl 22) + (address shl 20) +
                (channel shl 16) + (statusBank shl 8) + status,
        textBytesToUmp(text.drop(index)),
        textBytesToUmp(text.drop(index + 4)),
        textBytesToUmp(text.drop(index + 8))
    )

    fun flexDataProcess(
        group: Byte, address: Byte, channel: Byte, statusBank: Byte, status: Byte, text: ByteArray,
        context: Any? = null,
        sendUMP128: (Ump, Any?) -> Unit = { _, _ -> }
    ) {
        if (text.size < 13)
            sendUMP128(flexDataPacket(group, 0, address, channel, statusBank, status, text, 0), context)
        else {
            sendUMP128(flexDataPacket(group, 1, address, channel, statusBank, status, text, 0), context)
            val numPackets = text.size / 12 + if (text.size % 12 > 0) 1 else 0
            (1 until text.size / 12 - if (text.size % 12 != 0) 0 else 1).forEach {
                sendUMP128(flexDataPacket(group, 2, address, channel, statusBank, status, text, it * 12), context)
            }
            sendUMP128(flexDataPacket(group, 3, address, channel, statusBank, status, text, (numPackets - 1) * 12), context)
        }
    }

    fun flexDataText(group: Byte, address: Byte, channel: Byte, statusBank: Byte, status: Byte, text: String) =
        flexDataText(group, address, channel, statusBank, status, text.toByteArray())

    fun flexDataText(group: Byte, address: Byte, channel: Byte, statusBank: Byte, status: Byte, text: ByteArray) : List<Ump> {
        val ret = mutableListOf<Ump>()
        flexDataProcess(group, address, channel, statusBank, status, text) { ump, _ -> ret.add(ump) }
        return ret
    }

    fun flexDataCompleteBinary(group: Byte, address: Byte, channel: Byte, statusByte: Byte, int2: Int, int3: Int = 0, int4: Int = 0): Ump {
        val int1 = (MidiMessageType.FLEX_DATA shl 28) + (group shl 24) + (address shl 20) + (channel shl 16) + statusByte
        return Ump(int1, int2, int3, int4)
    }

    fun tempo(group: Byte, channel: Byte, numberOf10NanosecondsPerQuarterNote: Int) =
        flexDataCompleteBinary(group, 1, channel, 0, numberOf10NanosecondsPerQuarterNote)

    fun timeSignatureDirect(group: Byte, channel: Byte, numerator: UByte, rawDenominator: UByte, numberOf32Notes: Byte) =
        flexDataCompleteBinary(group, 1, channel, 1, (numerator.toInt() shl 24) + (rawDenominator.toInt() shl 16) + (numberOf32Notes shl 8))

    fun metronome(group: Byte, channel: Byte, numClocksPerPrimeryClick: Byte, barAccent1: Byte, barAccent2: Byte, barAccent3: Byte, numSubdivisionClick1: Byte, numSubdivisionClick2: Byte) =
        flexDataCompleteBinary(group, 1, channel, 2,
            (numClocksPerPrimeryClick shl 24) + (barAccent1 shl 16) + (barAccent2 shl 8) + barAccent3,
            (numSubdivisionClick1 shl 24) + (numSubdivisionClick2 shl 16))

    private fun sharpOrFlatsToInt(v: Byte) = if (v < 0) v + 0x10 else v.toInt()

    fun keySignature(group: Byte, address: Byte, channel: Byte, sharpsOrFlats: Byte, tonicNote: Byte) =
        flexDataCompleteBinary(group, address, channel, 5,
            (sharpOrFlatsToInt(sharpsOrFlats) shl 28) + (tonicNote shl 24))

    // Those "alteration" arguments are set to UInt as it will involve additions
    fun chordName(group: Byte, address: Byte, channel: Byte,
                  tonicSharpsFlats: Byte, chordTonic: Byte, chordType: Byte,
                  alter1: UInt, alter2: UInt, alter3: UInt, alter4: UInt,
                  bassSharpsFlats: Byte, bassNote: Byte, bassChordType: Byte,
                  bassAlter1: UInt,
                  bassAlter2: UInt
                  ) =
        flexDataCompleteBinary(group, address, channel, 6,
            (sharpOrFlatsToInt(tonicSharpsFlats) shl 28) + (chordTonic shl 24) + (chordType shl 16) + (alter1 shl 8).toInt() + alter2.toInt(),
            ((alter3 shl 24) + (alter4 shl 16)).toInt(),
            (sharpOrFlatsToInt(bassSharpsFlats) shl 28) + (bassNote shl 24) + (bassChordType shl 16) + (bassAlter1 shl 8).toInt() + bassAlter2.toInt())

    fun metadataText(group: Byte, address: Byte, channel: Byte, status: Byte, text: String) =
        flexDataText(group, address, channel, 1, status, text)

    fun metadataText(group: Byte, address: Byte, channel: Byte, status: Byte, text: ByteArray) =
        flexDataText(group, address, channel, 1, status, text)

    fun performanceText(group: Byte, address: Byte, channel: Byte, status: Byte, text: String) =
        flexDataText(group, address, channel, 2, status, text)

    fun performanceText(group: Byte, address: Byte, channel: Byte, status: Byte, text: ByteArray) =
        flexDataText(group, address, channel, 2, status, text)

    // Bytes conversions

    fun fromPlatformBytes(byteOrder: ByteOrder, bytes: List<Byte>) : Iterable<Ump> =
        sequence {
            val reader = UmpStreamReader(Reader(bytes, 0), byteOrder)
            while (reader.reader.canRead())
                yield(reader.readUmp())
        }.asIterable()

    fun fromPlatformNativeBytes(bytes: List<Byte>) = this.fromPlatformBytes(ByteOrder.nativeOrder(), bytes)
}
