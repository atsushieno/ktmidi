package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.ci.Json
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
        val endpointInquiryReceived = mutableListOf<(initiatorMUID: Int, destinationMUID: Int, status: Byte) -> Unit>()
        val processInquiryReceived = mutableListOf<(source: Byte, initiatorMUID: Int, destinationMUID: Int) -> Unit>()
        val profileStateChanged = mutableListOf<(profile: MidiCIProfileId, enabled: Boolean) -> Unit>()
        val unknownMessageReceived = mutableListOf<(data: List<Byte>) -> Unit>()
        val propertyCapabilityInquiryReceived = mutableListOf<(destination: Byte, sourceMUID: Int, destinationMUID: Int, max: Byte) -> Unit>()
        val getPropertyDataReceived = mutableListOf<(sourceMUID: Int, destinationMUID: Int, requestId: Byte, header: List<Byte>) -> Unit>()
    }

    private val events = Events()

    private val device = MidiCIDeviceInfo(1,2,3,4,
        "atsushieno", "KtMidi", "KtMidi-CI-Tool", "0.1")

    private val responder = MidiCIResponder(device, {
        data -> outputSender(data)
    }).apply {
        productInstanceId = "KtMidi-CI-Tool"

        // Unknown
        processUnknownCIMessage = { data ->
            ViewModel.logText.value += "- NAK(${data.joinToString { it.toString(16) }}"
            events.unknownMessageReceived.forEach { it(data) }
            sendNakForUnknownCIMessage(data)
        }
        // Endpoint
        processDiscovery = { initiatorMUID, initiatorOutputPath ->
            ViewModel.logText.value += "- Discovery(${initiatorMUID.toString(16)}, $initiatorOutputPath)\n"
            events.discoveryReceived.forEach { it(initiatorMUID, initiatorOutputPath) }
            sendDiscoveryReplyForInquiry(initiatorMUID, initiatorOutputPath)
        }
        processEndpointMessage = { initiatorMUID, destinationMUID, status ->
            ViewModel.logText.value += "- Endpoint(${initiatorMUID.toString(16)}, ${destinationMUID.toString(16)}, $status)\n"
            events.endpointInquiryReceived.forEach { it(initiatorMUID, destinationMUID, status) }
            sendEndpointReplyForInquiry(initiatorMUID, destinationMUID, status)
        }
        // Profile
        processProfileInquiry = { source, sourceMUID, destinationMUID ->
            ViewModel.logText.value += "- ProfileInquiry($source, ${sourceMUID.toString(16)}, ${destinationMUID.toString(16)})\n"
            events.processInquiryReceived.forEach { it(source, sourceMUID, destinationMUID) }
            sendProfileReplyForInquiry(source, sourceMUID, destinationMUID)
        }
        onProfileSet = { profile, enabled ->
            ViewModel.logText.value += "- Profile${if (enabled) "Enabled" else "Disabled"}($profile})\n"
            events.profileStateChanged.forEach { it(profile, enabled) }
        }
        // PE
        processPropertyCapabilitiesInquiry = { destination, sourceMUID, destinationMUID, max ->
            ViewModel.logText.value += "- PropertyGetCapabilities($destination, ${sourceMUID.toString(16)}, ${destinationMUID.toString(16)}, max)\n"
            events.propertyCapabilityInquiryReceived.forEach { it(destination, sourceMUID, destinationMUID, max) }
            sendPropertyCapabilitiesReply(destination, sourceMUID, destinationMUID, max)
        }
        processGetPropertyData = { sourceMUID, destinationMUID, requestId, header ->
            ViewModel.logText.value += "- GetPropertyData(${sourceMUID.toString(16)}, ${destinationMUID.toString(16)}, $requestId, ${header.toByteArray().decodeToString()})\n"
            events.getPropertyDataReceived.forEach { it(sourceMUID, destinationMUID, requestId, header) }
            sendReplyToGetPropertyData(sourceMUID, destinationMUID, requestId, header)
        }

        profileSet.add(Pair(MidiCIProfileId(0x7E, 1, 2, 3, 4), true))
        profileSet.add(Pair(MidiCIProfileId(0x7E, 5, 6, 7, 8), true))
    }
}