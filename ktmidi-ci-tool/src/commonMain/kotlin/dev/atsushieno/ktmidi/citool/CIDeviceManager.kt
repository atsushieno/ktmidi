package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.Midi1Status
import dev.atsushieno.ktmidi.MidiInput
import dev.atsushieno.ktmidi.MidiProtocolVersion
import dev.atsushieno.ktmidi.ci.MessageDirection
import dev.atsushieno.ktmidi.ci.MidiCIConstants
import dev.atsushieno.ktmidi.citool.view.ViewHelper
import kotlin.experimental.and

class CIDeviceManager(private val midiDeviceManager: MidiDeviceManager) {
    var isResponder = false

    val initiator by lazy {
        CIInitiatorModel { data ->
            val midi1Bytes = listOf(Midi1Status.SYSEX.toByte()) + data + listOf(Midi1Status.SYSEX_END.toByte())
            midiDeviceManager.sendToAll(midi1Bytes.toByteArray(), 0)
        }.apply {
            midiMessageReportModeChanged.add {
                // if it went normal non-MIDI-Message-Report mode and has saved inputs, flush them to the logger.
                if (!receivingMidiMessageReports && chunkedMessages.any())
                    logMidiMessageReportChunk(chunkedMessages)
            }
        }
    }
    val responder by lazy {
        CIResponderModel(
            ciOutputSender = { data ->
                val midi1Bytes = listOf(Midi1Status.SYSEX.toByte()) + data + listOf(Midi1Status.SYSEX_END.toByte())
                midiDeviceManager.sendToAll(midi1Bytes.toByteArray(), 0)
            },
            midiMessageReportOutputSender = { data -> midiDeviceManager.sendToAll(data.toByteArray(), 0) }
        )
    }

    private var lastChunkedMessageChannel: Byte = -1 // will never match at first.
    private val chunkedMessages = mutableListOf<Byte>()

    private fun setupInputEventListener(input: MidiInput) {
        // FIXME: support UMP input
        input.setMessageReceivedListener { data, start, length, _ ->
            ViewHelper.runInUIContext {
                if (data.size > start + 3 &&
                    data[start] == Midi1Status.SYSEX.toByte() &&
                    data[start + 1] == MidiCIConstants.UNIVERSAL_SYSEX &&
                    data[start + 3] == MidiCIConstants.SYSEX_SUB_ID_MIDI_CI
                ) {
                    val ciMessage = data.drop(start + 1).take(length - 2)
                    // it is a MIDI-CI message
                    // FIXME: maybe make it exclusive?
                    if (isResponder)
                        responder.processCIMessage(ciMessage)
                    else
                        initiator.processCIMessage(ciMessage)
                }
                else if (!isResponder) {
                    // it may be part of MIDI Message Report.
                    // Until it receives End of MIDI Message Report, preserve inputs and output in batch style per channel.
                    if (initiator.receivingMidiMessageReports) {
                        val channel = data[0] and 15
                        if (channel != lastChunkedMessageChannel) {
                            if (chunkedMessages.any())
                                logMidiMessageReportChunk(chunkedMessages)
                            chunkedMessages.clear()
                            lastChunkedMessageChannel = channel
                        }
                        chunkedMessages.addAll(data.toList())
                    }
                    else
                    // CIResponder received some message. No idea why, but log anyway.
                        AppModel.log("[Initiator received MIDI] " + data.drop(start).take(length), MessageDirection.In)
                }
                else
                    // CIResponder received some message. No idea why, but log anyway.
                    AppModel.log("[Initiator received MIDI] " + data.drop(start).take(length), MessageDirection.In)
            }
        }
    }

    private fun logMidiMessageReportChunk(data: List<Byte>) {
        AppModel.log("[Initiator received MIDI (buffered)] " +
                data.joinToString { it.toUByte().toString(16) },
            MessageDirection.In)
    }

    init {
        midiDeviceManager.midiInputOpened.add {
            setupInputEventListener(it)
        }
    }
}