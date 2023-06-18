package dev.atsushieno.ktmidi

import dev.atsushieno.ktmidi.ci.DeviceDetails
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
        MidiMessageType.SYSEX8_MDS, MidiMessageType.FLEX_DATA, MidiMessageType.UMP_STREAM -> 16
        MidiMessageType.SYSEX7, MidiMessageType.MIDI2 -> 8
        else -> 4
    }

private fun toPlatformBytes(bytes: ByteArray, value: Int, offset: Int, byteOrder: ByteOrder) {
    if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
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

fun Ump.toPlatformBytes(bytes: ByteArray, offset: Int, byteOrder: ByteOrder) {
    val size = sizeInBytes
    toPlatformBytes(bytes, int1, offset, byteOrder)
    if (size != 4)
        toPlatformBytes(bytes, int2, offset + 4, byteOrder)
    if (size == 16) {
        toPlatformBytes(bytes, int3, offset + 8, byteOrder)
        toPlatformBytes(bytes, int4, offset + 12, byteOrder)
    }
}

fun Ump.toPlatformBytes(bytes: ByteArray, offset: Int) = this.toPlatformBytes(bytes, offset, ByteOrder.nativeOrder())

fun Ump.toPlatformBytes(byteOrder: ByteOrder) : ByteArray {
    val bytes = ByteArray(this.sizeInBytes) {0}
    toPlatformBytes(bytes, int1, 0, byteOrder)
    if (messageType > 2)
        toPlatformBytes(bytes, int2, 4, byteOrder)
    if (messageType > 4) {
        toPlatformBytes(bytes, int3, 8, byteOrder)
        toPlatformBytes(bytes, int4, 12, byteOrder)
    }
    return bytes
}

fun Ump.toPlatformNativeBytes() = this.toPlatformBytes(ByteOrder.nativeOrder())

// Second half of the 1st. byte
val Ump.group: Int
    get() = (int1 shr 24) and 0xF

// 2nd. byte
val Ump.statusByte: Int
    get() = (int1 shr 16) and 0xFF

// First half of the 2nd. byte. (for MIDI1, MIDI2, Sysex7, Sysex8, System Messages)
val Ump.statusCode: Int
    get() = statusByte and 0xF0

// Second half of the 2nd. byte
val Ump.channelInGroup: Int // 0..15
    get() = statusByte and 0xF

val Ump.groupAndChannel: Int // 0..255
    get() = group shl 4 and channelInGroup

val Ump.isJRClock
    get() = messageType == MidiMessageType.UTILITY && statusCode == MidiUtilityStatus.JR_CLOCK
val Ump.jrClock
    get() = if (isJRClock) int1 and 0xFFFF else 0

val Ump.isJRTimestamp
    get()= messageType == MidiMessageType.UTILITY && statusCode == MidiUtilityStatus.JR_TIMESTAMP
val Ump.jrTimestamp
    get() = if(isJRTimestamp) int1 and 0xFFFF else 0

val Ump.isDCTPQ
    get() = messageType == MidiMessageType.UTILITY && statusCode == MidiUtilityStatus.DCTPQ
val Ump.dctpq
    get() = if(isDCTPQ) int1 and 0xFFFF else 0

val Ump.isDeltaClockstamp
    get() = messageType == MidiMessageType.UTILITY && statusCode == MidiUtilityStatus.DELTA_CLOCKSTAMP
val Ump.deltaClockstamp
    get() = if(isDeltaClockstamp) int1 and 0xFFFFF else 0

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
val Ump.midi2CAfData
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

// Flex Data message properties

val Ump.isFlexData
    get() = messageType == MidiMessageType.FLEX_DATA

val Ump.flexDataFormat: Int
    get() = (int1 shr 22) and 3

val Ump.flexDataStatus: Byte
    get() = (int1 and 0xFF).toByte()

val Ump.flexDataStatusBank: Byte
    get() = ((int1 and 0xFF00) shr 8).toByte()

val Ump.isTempo
    get() = messageType == MidiMessageType.FLEX_DATA && flexDataStatus == FlexDataStatus.TEMPO
val Ump.tempo
    get() = int2

val Ump.isTimeSignature
    get() = messageType == MidiMessageType.FLEX_DATA && flexDataStatus == FlexDataStatus.TIME_SIGNATURE
val Ump.timeSignatureNumerator
    get() = (int2 shr 24) and 0xFF
val Ump.timeSignatureDenominator
    get() = (int2 shr 16) and 0xFF
val Ump.timeSignatureNumberOf32thNotes
    get() = (int2 shr 8) and 0xFF

val Ump.isMetronome
    get() = messageType == MidiMessageType.FLEX_DATA && flexDataStatus == FlexDataStatus.METRONOME
val Ump.metronomeClocksPerPrimaryClick
    get() = (int2 shr 24) and 0xFF
val Ump.metronomeBarAccent1
    get() = (int2 shr 16) and 0xFF
val Ump.metronomeBarAccent2
    get() = (int2 shr 8) and 0xFF
val Ump.metronomeBarAccent3
    get() = int2 and 0xFF
val Ump.metronomeSubDivisionClick1
    get() = (int3 shr 24) and 0xFF
val Ump.metronomeSubDivisionClick2
    get() = (int3 shr 16) and 0xFF

private fun rawBitsToSharpsFlats(value: Int): Byte {
    val v = value and 0xF
    return (if (v > 7) -16 + v else v).toByte()
}

val Ump.isKeySignature
    get() = messageType == MidiMessageType.FLEX_DATA && flexDataStatus == FlexDataStatus.KEY_SIGNATURE
val Ump.keySignatureSharpsFlats
    get() = rawBitsToSharpsFlats(int2 shr 28) // Note that we have to return negative values for any value more than 0x8
val Ump.keySignatureTonicNote
    get() = ((int2 shr 24) and 0xF).toByte()

val Ump.isChordName
    get() = messageType == MidiMessageType.FLEX_DATA && flexDataStatus == FlexDataStatus.CHORD_NAME
val Ump.chordNameSharpsFlats
    get() = rawBitsToSharpsFlats(int2 shr 28) // Note that we have to return negative values for any value more than 0x8
val Ump.chordNameChordTonic
    get() = ((int2 shr 24) and 0xF).toByte()
val Ump.chordNameChordType
    get() = ((int2 shr 16) and 0xFF).toByte()
val Ump.chordNameAlter1
    get() = ((int2 shr 8) and 0xFF).toUInt()
val Ump.chordNameAlter2
    get() = (int2 and 0xFF).toUInt()
val Ump.chordNameAlter3
    get() = ((int3 shr 24) and 0xFF).toUInt()
val Ump.chordNameAlter4
    get() = ((int3 shr 16) and 0xFF).toUInt()
val Ump.chordNameBassSharpsFlats
    get() = rawBitsToSharpsFlats(int4 shr 28) // Note that we have to return negative values for any value more than 0x8
val Ump.chordNameBassNote
    get() = ((int4 shr 24) and 0xF).toByte()
val Ump.chordNameBassChordType
    get() = ((int4 shr 16) and 0xFF).toByte()
val Ump.chordNameBassAlter1
    get() = ((int4 shr 8) and 0xFF).toUInt()
val Ump.chordNameBassAlter2
    get() = (int4 and 0xFF).toUInt()

// Ump Stream message properties

val Ump.isUmpStream
    get() = messageType == MidiMessageType.UMP_STREAM

val Ump.umpStreamFormat
    get() = (int1 shr 26) and 3

val Ump.endpointDiscoveryUmpVersionMajor
    get() = (int1 shr 8) and 0xFF
val Ump.endpointDiscoveryUmpVersionMinor
    get() = int1 and 0xFF
val Ump.endpointDiscoveryFilterBitmap
    get() = int2 and 0xFF

val Ump.endpointInfoUmpVersionMajor
    get() = (int1 shr 8) and 0xFF
val Ump.endpointInfoUmpVersionMinor
    get() = int1 and 0xFF
val Ump.endpointInfoStaticFunctionBlocks
    get() = int2 < 0
val Ump.endpointInfoFunctionBlockCount
    get() = (int2 shr 24) and 0x7F
val Ump.endpointInfoMidi2Capable
    get() = (int2 and 0x200) != 0
val Ump.endpointInfoMidi1Capable
    get() = (int2 and 0x100) != 0
val Ump.endpointInfoSupportsRxJR
    get() = (int2 and 2) != 0
val Ump.endpointInfoSupportsTxJR
    get() = (int2 and 1) != 0

val Ump.deviceIdentity
    get() = DeviceDetails(manufacturer = int2 and 0xFFFFFF,
        family = ((int3 shr 16) and 0xFFFF).toShort(),
        familyModelNumber = (int3 and 0xFFFF).toShort(),
        softwareRevisionLevel = int4)

val Ump.streamConfigProtocol
    get() = (int1 shr 8) and 0xFF
val Ump.streamConfigSupportsRxJR
    get() = (int1 and 2) != 0
val Ump.streamConfigSupportsTxJR
    get() = (int1 and 1) != 0

val Ump.functionBlockCount
    get() = (int1 shr 8) and 0x7F
val Ump.functionBlockDiscoveryFilter
    get() = int1 and 0xFF

val Ump.functionBlockActive
    get() = (int1 and 0x8000) != 0
val Ump.functionBlockUiHint
    get() = (int1 shr 4) and 0x3
val Ump.functionBlockMidi1Port
    get() = (int1 shr 2) and 0x3
val Ump.functionBlockDirection
    get() = int1 and 0x3
val Ump.functionBlockFirstGroup
    get() = (int2 shr 24) and 0xFF
val Ump.functionBlockGroupCount
    get() = (int2 shr 16) and 0xFF
val Ump.functionBlockCIVersion
    get() = (int2 shr 8) and 0xFF
val Ump.functionBlockMaxSysEx8
    get() = int2 and 0xFF

val Ump.isStartOfClip
    get() = isUmpStream && ((int1 and 0xFF_0000) shr 16).toByte() == UmpStreamStatus.START_OF_CLIP

val Ump.isEndOfClip
    get() = isUmpStream && ((int1 and 0xFF_0000) shr 16).toByte() == UmpStreamStatus.END_OF_CLIP

// Binary/Text chunk retrievers

enum class UmpBinaryRetrieverFallback {
    Break,
    Exception
}

@Deprecated("use UmpBinaryRetrieverFallback", ReplaceWith("UmpBinaryRetrieverFallback"))
typealias UmpSysexBinaryRetrieverFallback = UmpBinaryRetrieverFallback

object UmpRetriever {

    // SysEx7

    private fun takeSysex7Bytes(ump: Ump, outputter: (List<Byte>) -> Unit, sysex7Size: Int) {
        if (sysex7Size < 1)
            return
        // It is hack, but it just reuses toPlatformNativeBytes() and then pick up the appropriate parts per platform
        val src = ump.toPlatformNativeBytes() // NOTE: memory consumptive
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN)
            outputter(src.drop(2).take(sysex7Size))
        else {
            outputter(listOf(src[1]))
            if (sysex7Size > 1)
                outputter(listOf(src[0]))
            src.reverse()
            if (sysex7Size > 2)
                outputter(src.take(sysex7Size - 2))
        }
    }

    fun getSysex7Data(iter: Iterator<Ump>, fallback: UmpBinaryRetrieverFallback = UmpBinaryRetrieverFallback.Break) : List<Byte> {
        val ret = mutableListOf<Byte>()
        val output: (List<Byte>) -> Unit = { ret.addAll(it) }
        getSysex7Data(output, iter, fallback)
        return ret
    }

    fun getSysex7Data(outputter: (List<Byte>) -> Unit, input: Iterator<Ump>, fallback: UmpBinaryRetrieverFallback = UmpBinaryRetrieverFallback.Break) {
        if (!input.hasNext())
            if (fallback == UmpBinaryRetrieverFallback.Break) return else throw UmpException("UMP iterator is empty")
        val pStart = input.next()
        takeSysex7Bytes(pStart, outputter, pStart.sysex7Size)
        when (pStart.statusCode) {
            Midi2BinaryChunkStatus.COMPLETE_PACKET -> return
            Midi2BinaryChunkStatus.CONTINUE, Midi2BinaryChunkStatus.END ->
                if (fallback == UmpBinaryRetrieverFallback.Break) return else throw UmpException("Unexpected sysex7 non-starter packet appeared")
        }
        while (input.hasNext()) {
            val pCont = input.next()
            takeSysex7Bytes(pCont, outputter, pCont.sysex7Size)
            when (pCont.statusCode) {
                Midi2BinaryChunkStatus.END -> break
                Midi2BinaryChunkStatus.CONTINUE -> continue
                else ->
                    if (fallback == UmpBinaryRetrieverFallback.Break) return else throw UmpException("Unexpected sysex7 non-continue packet appeared")
            }
        }
    }

    // SysEx8

    private fun takeSysex8Bytes(ump: Ump, outputter: (List<Byte>) -> Unit, sysex8Size: Int) {
        if (sysex8Size < 2)
            return
        // It is hack, but it just reuses toPlatformNativeBytes() and then pick up the appropriate parts per platform
        val src = ump.toPlatformNativeBytes() // NOTE: memory consumptive

        // Note that sysex8Size always contains streamID which should NOT be part of the result.
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN)
            outputter(src.drop(3).take(sysex8Size))
        else {
            outputter(listOf(src[0]))
            src.reverse()
            if (sysex8Size > 2)
                outputter(src.drop(8).take(if (sysex8Size > 6) 4 else sysex8Size - 2)) // NOTE: memory consumptive
            if (sysex8Size > 6)
                outputter(src.drop(4).take(if (sysex8Size > 10) 4 else sysex8Size - 6)) // NOTE: memory consumptive
            if (sysex8Size > 10)
                outputter(src.take(sysex8Size - 10)) // NOTE: memory consumptive
        }
    }

    fun getSysex8Data(iter: Iterator<Ump>, fallback: UmpBinaryRetrieverFallback = UmpBinaryRetrieverFallback.Break) : List<Byte> {
        val ret = mutableListOf<Byte>()
        val output: (List<Byte>) -> Unit = { ret.addAll(it) }
        getSysex8Data(output, iter, fallback)
        return ret
    }

    fun getSysex8Data(outputter: (List<Byte>) -> Unit, input: Iterator<Ump>, fallback: UmpBinaryRetrieverFallback = UmpBinaryRetrieverFallback.Break) {
        if (!input.hasNext())
            if (fallback == UmpBinaryRetrieverFallback.Break) return else throw UmpException("UMP iterator is empty")
        val pStart = input.next()
        takeSysex8Bytes(pStart, outputter, pStart.sysex8Size)
        when (pStart.statusCode) {
            Midi2BinaryChunkStatus.COMPLETE_PACKET -> return
            Midi2BinaryChunkStatus.CONTINUE, Midi2BinaryChunkStatus.END ->
                if (fallback == UmpBinaryRetrieverFallback.Break) return else throw UmpException("Unexpected sysex8 non-starter packet appeared")
        }
        while (input.hasNext()) {
            val pCont = input.next()
            takeSysex8Bytes(pCont, outputter, pCont.sysex8Size)
            when (pCont.statusCode) {
                Midi2BinaryChunkStatus.END -> break
                Midi2BinaryChunkStatus.CONTINUE -> continue
                else ->
                    if (fallback == UmpBinaryRetrieverFallback.Break) return else throw UmpException("Unexpected sysex8 non-continue packet appeared")
            }
        }
    }

    // Flex Data Text

    private fun takeFlexDataText(ump: Ump, outputter: (List<Byte>) -> Unit) {
        // It is hack, but it just reuses toPlatformNativeBytes() and then pick up the appropriate parts per platform
        // Note that unlike other binary messages we have to treat the bytes in BIG ENDIAN.
        val src = ump.toPlatformBytes(ByteOrder.BIG_ENDIAN) // NOTE: memory consumptive

        // CONTINUE packets may contain \0 as "melisma".
        // When they appear in format 3 then it indicates the end of text.
        //
        // LAMESPEC: There is no description on what happens if a lyric could fit in 12 bytes *and* contains melisma.
        //  Therefore, this implementation assumes that a format 0 packet may contain melisma.
        val bytes = src.drop(4).takeWhile { it != 0.toByte() || ump.flexDataFormat != Midi2BinaryChunkFormat.CONTINUE }
        // If it is a complete packet then those '\0's are non-data (i.e. not melisma).
        if (ump.flexDataFormat != Midi2BinaryChunkFormat.CONTINUE)
            outputter(bytes.dropLastWhile { it == 0.toByte() })
        else
            outputter(bytes)
    }

    fun getFlexDataTextBytes(input: Iterator<Ump>, fallback: UmpBinaryRetrieverFallback = UmpBinaryRetrieverFallback.Break) : List<Byte> {
        val ret = mutableListOf<Byte>()
        val output: (List<Byte>) -> Unit = { ret.addAll(it) }
        getFlexDataTextBytes(output, input, fallback)
        return ret
    }
    fun getFlexDataText(input: Iterator<Ump>, fallback: UmpBinaryRetrieverFallback = UmpBinaryRetrieverFallback.Break) : String =
        getFlexDataTextBytes(input, fallback).toByteArray().decodeToString()

    fun getFlexDataTextBytes(outputter: (List<Byte>) -> Unit, input: Iterator<Ump>, fallback: UmpBinaryRetrieverFallback = UmpBinaryRetrieverFallback.Break) {
        if (!input.hasNext())
            if (fallback == UmpBinaryRetrieverFallback.Break) return else throw UmpException("UMP iterator is empty")
        val pStart = input.next()
        takeFlexDataText(pStart, outputter)
        when (pStart.flexDataFormat) {
            Midi2BinaryChunkFormat.COMPLETE_PACKET -> return
            Midi2BinaryChunkFormat.CONTINUE, Midi2BinaryChunkFormat.END ->
                if (fallback == UmpBinaryRetrieverFallback.Break) return else throw UmpException("Unexpected flex data text non-starter packet appeared")
        }
        while (input.hasNext()) {
            val pCont = input.next()
            takeFlexDataText(pCont, outputter)
            when (pCont.flexDataFormat) {
                Midi2BinaryChunkFormat.END -> break
                Midi2BinaryChunkFormat.CONTINUE -> continue
                else ->
                    if (fallback == UmpBinaryRetrieverFallback.Break) return else throw UmpException("Unexpected flex data text non-continue packet appeared")
            }
        }
    }

    // UMP Stream Text

    private fun takeUmpStreamText(ump: Ump, outputter: (List<Byte>) -> Unit, dropSize: Int) {
        // It is hack, but it just reuses toPlatformNativeBytes() and then pick up the appropriate parts per platform
        // Note that unlike other binary messages we have to treat the bytes in BIG ENDIAN.
        val src = ump.toPlatformBytes(ByteOrder.BIG_ENDIAN) // NOTE: memory consumptive

        val bytes = src.drop(dropSize).takeWhile { it != 0.toByte() }
        outputter(bytes)
    }

    private fun getUmpStreamTextBytes(input: Iterator<Ump>, dropSize: Int, fallback: UmpBinaryRetrieverFallback = UmpBinaryRetrieverFallback.Break) : List<Byte> {
        val ret = mutableListOf<Byte>()
        val output: (List<Byte>) -> Unit = { ret.addAll(it) }
        getUmpStreamTextBytes(output, input, dropSize, fallback)
        return ret
    }
    private fun getUmpStreamTextBytes(outputter: (List<Byte>) -> Unit, input: Iterator<Ump>, dropSize: Int, fallback: UmpBinaryRetrieverFallback = UmpBinaryRetrieverFallback.Break) {
        if (!input.hasNext())
            if (fallback == UmpBinaryRetrieverFallback.Break) return else throw UmpException("UMP iterator is empty")
        val pStart = input.next()
        takeUmpStreamText(pStart, outputter, dropSize)
        when (pStart.umpStreamFormat) {
            Midi2BinaryChunkFormat.COMPLETE_PACKET -> return
            Midi2BinaryChunkFormat.CONTINUE, Midi2BinaryChunkFormat.END ->
                if (fallback == UmpBinaryRetrieverFallback.Break) return else throw UmpException("Unexpected flex data text non-starter packet appeared")
        }
        while (input.hasNext()) {
            val pCont = input.next()
            takeUmpStreamText(pCont, outputter, dropSize)
            when (pCont.umpStreamFormat) {
                Midi2BinaryChunkFormat.END -> break
                Midi2BinaryChunkFormat.CONTINUE -> continue
                else ->
                    if (fallback == UmpBinaryRetrieverFallback.Break) return else throw UmpException("Unexpected flex data text non-continue packet appeared")
            }
        }
    }

    fun getEndpointNameBytes(input: Iterator<Ump>, fallback: UmpBinaryRetrieverFallback = UmpBinaryRetrieverFallback.Break) =
        getUmpStreamTextBytes(input, 2, fallback)

    fun getEndpointName(input: Iterator<Ump>, fallback: UmpBinaryRetrieverFallback = UmpBinaryRetrieverFallback.Break) : String =
        getUmpStreamTextBytes(input, 2, fallback).toByteArray().decodeToString()

    fun getProductInstanceIdBytes(input: Iterator<Ump>, fallback: UmpBinaryRetrieverFallback = UmpBinaryRetrieverFallback.Break) =
        getUmpStreamTextBytes(input, 2, fallback)

    fun getProductInstanceId(input: Iterator<Ump>, fallback: UmpBinaryRetrieverFallback = UmpBinaryRetrieverFallback.Break) : String =
        getUmpStreamTextBytes(input, 2, fallback).toByteArray().decodeToString()

    fun getFunctionBlockNameBytes(input: Iterator<Ump>, fallback: UmpBinaryRetrieverFallback = UmpBinaryRetrieverFallback.Break) =
        getUmpStreamTextBytes(input, 3, fallback)

    fun getFunctionBlockName(input: Iterator<Ump>, fallback: UmpBinaryRetrieverFallback = UmpBinaryRetrieverFallback.Break) : String =
        getUmpStreamTextBytes(input, 3, fallback).toByteArray().decodeToString()

}
