package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.Midi1Status
import dev.atsushieno.ktmidi.MidiInput
import dev.atsushieno.ktmidi.ci.MessageDirection
import dev.atsushieno.ktmidi.ci.MidiCIConstants
import dev.atsushieno.ktmidi.ci.MidiCIDeviceConfiguration
import dev.atsushieno.ktmidi.citool.view.ViewHelper
import kotlin.experimental.and

class CIDeviceManager(config: MidiCIDeviceConfiguration, private val midiDeviceManager: MidiDeviceManager) {
    val device by lazy {
        CIDeviceModel(this, AppModel.muid, config,
            ciOutputSender = { data ->
                val midi1Bytes = listOf(Midi1Status.SYSEX.toByte()) + data + listOf(Midi1Status.SYSEX_END.toByte())
                midiDeviceManager.sendToAll(midi1Bytes.toByteArray(), 0)
            },
            midiMessageReportOutputSender = { data -> midiDeviceManager.sendToAll(data.toByteArray(), 0) }
        )
    }

    val initiator by lazy { device.initiator }

    private fun setupInputEventListener(input: MidiInput) {
        // FIXME: support UMP input
        input.setMessageReceivedListener { data, start, length, _ ->
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
                        AppModel.log("[received MIDI] " + data.drop(start).take(length), MessageDirection.In)
                }
            }
        }
    }

    internal fun logMidiMessageReportChunk(data: List<Byte>) {
        AppModel.log("[received MIDI (buffered)] " +
                data.joinToString { it.toUByte().toString(16) },
            MessageDirection.In)
    }

    init {
        midiDeviceManager.midiInputOpened.add {
            setupInputEventListener(it)
        }
    }
}