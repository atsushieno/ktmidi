package dev.atsushieno.ktmidi.musicdevice

import dev.atsushieno.ktmidi.*
import dev.atsushieno.ktmidi.ci.*
import kotlin.experimental.and
import kotlin.random.Random

// A pair of MidiInput and MidiOutput to configure a MidiCISession.
data class MidiCISessionSource(
    val input: MidiInput,
    val output: MidiOutput
)

fun MidiCISessionSource.createMidiCISession(
    muid: Int = Random.nextInt() and 0x7F7F7F7F,
    config: MidiCIDeviceConfiguration = MidiCIDeviceConfiguration()
) = MidiCISession(
    input.details.midiTransportProtocol,
    {listener: OnMidiReceivedEventListener -> input.setMessageReceivedListener(listener) },
    MidiCIDevice(muid, config,
        sendCIOutput = { group: Byte, data: List<Byte> ->
            if (output.details.midiTransportProtocol == MidiTransportProtocol.UMP)
                UmpFactory.sysex7Process(group.toInt(), data, null, { v, _ ->
                    val bytes = Ump(v).toPlatformNativeBytes()
                    output.send(bytes, 0, bytes.size, 0)
                })
            else
                output.send(byteArrayOf(0xF0.toByte()) + data, 0, data.size + 1, 0)
        },
        sendMidiMessageReport = { group: Byte, protocol: MidiMessageReportProtocol, data: List<Byte> ->
            // send as is, regardless of the transport protocol
            output.send(data.toByteArray(), 0, data.size, 0)
        }
    )
)

class MidiCISession(
    inputMidiTransportProtocol: Int,
    addMidiReceivedEventListener: (OnMidiReceivedEventListener) -> Unit,
    val device: MidiCIDevice
) {
    private var receivingMidiMessageReports = false
    private var lastChunkedMessageChannel: Byte = -1 // will never match at first.
    private val chunkedMessages = mutableListOf<Byte>()
    private val midiMessageReportModeChanged = mutableListOf<() -> Unit>()

    private fun logMidiMessageReportChunk(data: List<Byte>) {
        device.logger.logMessage("[received MIDI (buffered)] " +
                data.joinToString { it.toUByte().toString(16) },
            MessageDirection.In)
    }

    private fun processCIMessage(group: Byte, data: List<Byte>) {
        if (data.isEmpty()) return
        device.logger.logMessage("[received CI SysEx (grp:$group)] " + data.joinToString { it.toUByte().toString(16) }, MessageDirection.In)
        device.processInput(group, data)
    }

    // It is actually based on ktmidi-ci-tool CIDeviceManager
    private fun processMidi1Input(data: ByteArray, start: Int, length: Int) {
        if (data.size > start + 3 &&
            data[start] == Midi1Status.SYSEX.toByte() &&
            data[start + 1] == MidiCIConstants.UNIVERSAL_SYSEX &&
            data[start + 3] == MidiCIConstants.SYSEX_SUB_ID_MIDI_CI
        ) {
            // it is a MIDI-CI message
            // Here it is MIDI 1.0 bytestream, so group is always 0.
            processCIMessage(0, data.drop(start + 1).take(length - 2))
        }
        else {
            // it may be part of MIDI Message Report.
            // Until it receives End of MIDI Message Report, preserve inputs and output in batch style per channel.
            if (receivingMidiMessageReports) {
                val channel = data[0] and 15
                if (channel != lastChunkedMessageChannel) {
                    if (chunkedMessages.any())
                        logMidiMessageReportChunk(chunkedMessages)
                    chunkedMessages.clear()
                    lastChunkedMessageChannel = channel
                }
                chunkedMessages.addAll(data.toList())
            } else
                // received some message. No idea why, but log anyway.
                device.logger.logMessage("[received MIDI1] " + data.drop(start).take(length), MessageDirection.In)
        }
    }

    private val bufferedSysex7 = mutableListOf<Byte>()
    private val bufferedSysex8 = mutableListOf<Byte>()
    private fun processUmpInput(data: ByteArray, start: Int, length: Int) {
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
                                processCIMessage(ump.group.toByte(), bufferedSysex7)
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
                                processCIMessage(ump.group.toByte(), bufferedSysex8)
                                bufferedSysex8.clear()
                            }
                    }
                    continue
                }
            }

            // it may be part of MIDI Message Report.
            // Until it receives End of MIDI Message Report, preserve inputs and output in batch style per channel.
            if (receivingMidiMessageReports) {
                val channel = ump.channelInGroup.toByte()
                if (channel != lastChunkedMessageChannel) {
                    if (chunkedMessages.any())
                        logMidiMessageReportChunk(chunkedMessages)
                    chunkedMessages.clear()
                    lastChunkedMessageChannel = channel
                }
                chunkedMessages.addAll(ump.toPlatformNativeBytes().toList())
            } else
            // received some message. No idea why, but log anyway.
                device.logger.logMessage("[received UMP] " + data.drop(start).take(length).joinToString(",") { it.toString(16) }, MessageDirection.In)
        }
    }

    init {
        addMidiReceivedEventListener { data, start, length, _ ->
            if (inputMidiTransportProtocol == MidiTransportProtocol.UMP)
                processUmpInput(data, start, length)
            else
                processMidi1Input(data, start, length)
        }

        device.apply {
            midiMessageReportModeChanged.add {
            // if it went normal non-MIDI-Message-Report mode and has saved inputs, flush them to the logger.
            if (!receivingMidiMessageReports && chunkedMessages.any())
                logMidiMessageReportChunk(chunkedMessages)
            }
            device.messageReceived.add {
                if (it is Message.MidiMessageReportReply) {
                    receivingMidiMessageReports = true
                    midiMessageReportModeChanged.forEach { it() }
                }
            }
            device.messageReceived.add {
                if (it is Message.MidiMessageReportNotifyEnd) {
                    receivingMidiMessageReports = false
                    midiMessageReportModeChanged.forEach { it() }
                }
            }

            device.midiMessageReporter = MidiMachineMessageReporter()

            // responder
            device.profileHost.onProfileSet.add { profile -> device.profileHost.profiles.profileEnabledChanged.forEach { it(profile) } }
        }
    }
}
