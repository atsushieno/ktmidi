package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.ci.MidiCIDeviceInfo
import dev.atsushieno.ktmidi.ci.MidiCIProfileId
import dev.atsushieno.ktmidi.ci.MidiCIResponder
import dev.atsushieno.ktmidi.citool.view.ViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

class CIResponderModel(private val outputSender: (ciBytes: List<Byte>) -> Unit) {
    fun processCIMessage(data: List<Byte>) {
        val time = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        ViewModel.logText.value += "[${time.time}] SYSEX: " + data.joinToString { it.toString(16) } + "\n"
        responder.processInput(data)
    }

    class Events {
        val discoveryReceived = mutableListOf<(initiatorMUID: Int, initiatorOutputPath: Byte) -> Unit>()
    }

    private val events = Events()

    private val device = MidiCIDeviceInfo(1,2,3,4,
        "atsushieno", "KtMidi", "KtMidi-CI-Tool", "0.1")

    private val responder = MidiCIResponder(device, {
        data -> outputSender(data)
    }).apply {
        productInstanceId = "KtMidi-CI-Tool"

        processDiscovery = { initiatorMUID, initiatorOutputPath ->
            events.discoveryReceived.forEach { it(initiatorMUID, initiatorOutputPath) }
            sendDiscoveryReplyForInquiry(initiatorMUID, initiatorOutputPath)
        }

        profileSet.add(Pair(MidiCIProfileId(0x7E, 1, 2, 3, 4), true))
        profileSet.add(Pair(MidiCIProfileId(0x7E, 5, 6, 7, 8), true))
    }
}