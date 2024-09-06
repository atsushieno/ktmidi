package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.*
import dev.atsushieno.ktmidi.ci.MessageDirection
import dev.atsushieno.ktmidi.ci.MidiCIConstants
import dev.atsushieno.ktmidi.ci.MidiCIDeviceConfiguration
import kotlin.experimental.and

internal infix fun Byte.shl(n: Int): Int = this.toInt() shl n

class CIDeviceManager(val owner: CIToolRepository, config: MidiCIDeviceConfiguration, private val midiDeviceManager: MidiDeviceManager) {
    val device by lazy {
        CIDeviceModel(this, owner.muid, config,
            ciOutputSender = { group, data ->
                val midi1Bytes = listOf(Midi1Status.SYSEX.toByte()) + data + listOf(Midi1Status.SYSEX_END.toByte())
                midiDeviceManager.sendToAll(group, midi1Bytes.toByteArray(), 0)
            },
            midiMessageReportOutputSender = { group, data -> midiDeviceManager.sendToAll(group, data.toByteArray(), 0) }
        )
    }

    private fun setupInputEventListener(input: MidiInput) {
        input.setMessageReceivedListener { data, start, length, _ ->
            if (input.details.midiTransportProtocol == MidiTransportProtocol.UMP)
                processUmpInput(data, start, length)
            else
                processMidi1Input(data, start, length)
        }
    }

    fun processMidi1Input(data: ByteArray, start: Int, length: Int) {
        ViewHelper.runInUIContext {
            if (data.size > start + 3 &&
                data[start] == Midi1Status.SYSEX.toByte() &&
                data[start + 1] == MidiCIConstants.UNIVERSAL_SYSEX &&
                data[start + 3] == MidiCIConstants.SYSEX_SUB_ID_MIDI_CI
            ) {
                // it is a MIDI-CI message
                // Here it is MIDI 1.0 bytestream, so group is always 0.
                device.processCIMessage(0, data.drop(start + 1).take(length - 2))
            }
            else {
                // it may be part of MIDI Message Report.
                // Until it receives End of MIDI Message Report, preserve inputs and output in batch style per channel.
                if (device.receivingMidiMessageReports) {
                    val channel = data[0] and 15
                    if (channel != device.lastChunkedMessageChannel) {
                        if (device.chunkedMessages.any())
                            logMidiMessageReportChunk(device.chunkedMessages)
                        device.chunkedMessages.clear()
                        device.lastChunkedMessageChannel = channel
                    }
                    device.chunkedMessages.addAll(data.toList())
                } else
                // received some message. No idea why, but log anyway.
                    owner.log("[received MIDI1] " + data.drop(start).take(length), MessageDirection.In)
            }
        }
    }

    private val bufferedSysex7 = mutableListOf<Byte>()
    private val bufferedSysex8 = mutableListOf<Byte>()
    fun processUmpInput(data: ByteArray, start: Int, length: Int) {
        ViewHelper.runInUIContext {
            val umpList = Ump.fromBytes(data, start, length).iterator()
            while (umpList.hasNext()) {
                val ump = umpList.next()
                when (ump.messageType) {
                    MidiMessageType.SYSEX7 -> {
                        if (ump.statusCode == Midi2BinaryChunkStatus.START)
                            bufferedSysex7.clear() // It is a beginning of MIDI-CI SysEx7 message

                        UmpRetriever.getSysex7Data({ bufferedSysex7.addAll(it) }, iterator { yield(ump) })

                        when (ump.statusCode) {
                            Midi2BinaryChunkStatus.END,
                            Midi2BinaryChunkStatus.COMPLETE_PACKET ->
                            if (bufferedSysex7.size > 2 &&
                                bufferedSysex7[0] == MidiCIConstants.UNIVERSAL_SYSEX &&
                                bufferedSysex7[2] == MidiCIConstants.SYSEX_SUB_ID_MIDI_CI) {
                                device.processCIMessage(ump.group.toByte(), bufferedSysex7)
                                bufferedSysex7.clear()
                            }
                        }
                        continue
                    }
                    MidiMessageType.SYSEX8_MDS -> {
                        // FIXME: we need message chunking mechanism, which should be rather implemented in ktmidi itself
                        if (ump.statusCode == Midi2BinaryChunkStatus.START)
                            bufferedSysex8.clear() // It is a beginning of MIDI-CI SysEx8 message

                        UmpRetriever.getSysex8Data({ bufferedSysex8.addAll(it) }, iterator { yield(ump) })

                        when (ump.statusCode) {
                            Midi2BinaryChunkStatus.END,
                            Midi2BinaryChunkStatus.COMPLETE_PACKET ->
                                if (bufferedSysex8.size > 2 &&
                                    bufferedSysex8[0] == MidiCIConstants.UNIVERSAL_SYSEX &&
                                    bufferedSysex8[2] == MidiCIConstants.SYSEX_SUB_ID_MIDI_CI) {
                                    device.processCIMessage(ump.group.toByte(), bufferedSysex8)
                                    bufferedSysex8.clear()
                                }
                        }
                        continue
                    }
                }

                // it may be part of MIDI Message Report.
                // Until it receives End of MIDI Message Report, preserve inputs and output in batch style per channel.
                if (device.receivingMidiMessageReports) {
                    val channel = ump.channelInGroup.toByte()
                    if (channel != device.lastChunkedMessageChannel) {
                        if (device.chunkedMessages.any())
                            logMidiMessageReportChunk(device.chunkedMessages)
                        device.chunkedMessages.clear()
                        device.lastChunkedMessageChannel = channel
                    }
                    device.chunkedMessages.addAll(ump.toPlatformNativeBytes().toList())
                } else
                // received some message. No idea why, but log anyway.
                    owner.log("[received UMP] " + data.drop(start).take(length).joinToString(",") { it.toString(16) }, MessageDirection.In)
            }
        }
    }

    internal fun logMidiMessageReportChunk(data: List<Byte>) {
        owner.log("[received MIDI (buffered)] " +
                data.joinToString { it.toUByte().toString(16) },
            MessageDirection.In)
    }

    init {
        midiDeviceManager.midiInputOpened.add {
            setupInputEventListener(it)
        }
    }
}