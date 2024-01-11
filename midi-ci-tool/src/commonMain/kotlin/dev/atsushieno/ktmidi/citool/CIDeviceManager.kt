package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.Midi1Status
import dev.atsushieno.ktmidi.MidiInput
import dev.atsushieno.ktmidi.ci.MidiCIConstants
import dev.atsushieno.ktmidi.citool.view.ViewHelper

class CIDeviceManager(private val midiDeviceManager: MidiDeviceManager) {
    var isResponder = false

    val initiator by lazy {
        CIInitiatorModel { data ->
            val midi1Bytes = listOf(Midi1Status.SYSEX.toByte()) + data + listOf(Midi1Status.SYSEX_END.toByte())
            midiDeviceManager.sendToAll(midi1Bytes.toByteArray(), 0)
        }
    }
    val responder by lazy {
        CIResponderModel { data ->
            val midi1Bytes = listOf(Midi1Status.SYSEX.toByte()) + data + listOf(Midi1Status.SYSEX_END.toByte())
            midiDeviceManager.sendToAll(midi1Bytes.toByteArray(), 0)
        }
    }


    private fun setupInputEventListener(input: MidiInput) {
        input.setMessageReceivedListener { data, start, length, _ ->
            ViewHelper.runInUIContext {
                if (data.size > 3 &&
                    data[start] == Midi1Status.SYSEX.toByte() &&
                    data[start + 1] == MidiCIConstants.UNIVERSAL_SYSEX &&
                    data[start + 3] == MidiCIConstants.UNIVERSAL_SYSEX_SUB_ID_MIDI_CI
                ) {
                    // it is a MIDI-CI message
                    // FIXME: maybe make it exclusive?
                    if (isResponder)
                        responder.processCIMessage(data.drop(start + 1).take(length - 2))
                    else
                        initiator.processCIMessage(data.drop(start + 1).take(length - 2))
                }
            }
        }
    }

    init {
        midiDeviceManager.midiInputOpened.add {
            setupInputEventListener(it)
        }
    }
}