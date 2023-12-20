package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.citool.view.ViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class CIResponderModel(private val outputSender: (ciBytes: List<Byte>) -> Unit) {
    fun processCIMessage(data: List<Byte>) {
        val time = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        ViewModel.logText.value += "[${time.time.toString().substring(0, 8)}] SYSEX: " + data.joinToString { it.toString(16) } + "\n"
        responder.processInput(data)
    }

    private val logger = Logger()

    class Events {
        val discoveryReceived = mutableListOf<(msg: Message.DiscoveryInquiry) -> Unit>()
        val endpointInquiryReceived = mutableListOf<(initiatorMUID: Int, destinationMUID: Int, status: Byte) -> Unit>()
        val profileInquiryReceived = mutableListOf<(source: Byte, initiatorMUID: Int, destinationMUID: Int) -> Unit>()
        val profileStateChanged = mutableListOf<(profile: MidiCIProfileId, enabled: Boolean) -> Unit>()
        val unknownMessageReceived = mutableListOf<(data: List<Byte>) -> Unit>()
        val propertyCapabilityInquiryReceived = mutableListOf<(Message.PropertyGetCapabilities) -> Unit>()
        val getPropertyDataReceived = mutableListOf<(msg: Message.GetPropertyData) -> Unit>()
        val setPropertyDataReceived = mutableListOf<(msg: Message.SetPropertyData) -> Unit>()
        val subscribePropertyReceived = mutableListOf<(msg: Message.SubscribeProperty) -> Unit>()
    }

    private val events = Events()

    private val device = MidiCIDeviceInfo(1,2,2,1,
        "atsushieno", "KtMidi", "KtMidi-CI-Tool Responder", "0.1")

    private val responder = MidiCIResponder(device, { data ->
        ViewModel.logText.value += "[R] REPLY SYSEX: " + data.joinToString { it.toString(16) } + "\n"
        outputSender(data)
    }).apply {
        productInstanceId = "KtMidi-CI-Tool Responder"

        // Unknown
        processUnknownCIMessage = { data ->
            logger.nak(data)
            events.unknownMessageReceived.forEach { it(data) }
            sendNakForUnknownCIMessage(data)
        }
        // Endpoint
        processDiscovery = { msg ->
            logger.discovery(msg)
            events.discoveryReceived.forEach { it(msg) }
            sendDiscoveryReplyForInquiry(msg)
        }
        processEndpointMessage = { initiatorMUID, destinationMUID, status ->
            logger.endpointMessage(initiatorMUID, destinationMUID, status)
            events.endpointInquiryReceived.forEach { it(initiatorMUID, destinationMUID, status) }
            sendEndpointReplyForInquiry(initiatorMUID, destinationMUID, status)
        }
        // Profile
        processProfileInquiry = { source, sourceMUID, destinationMUID ->
            logger.profileInquiry(source, sourceMUID, destinationMUID)
            events.profileInquiryReceived.forEach { it(source, sourceMUID, destinationMUID) }
            sendProfileReplyForInquiry(source, sourceMUID, destinationMUID)
        }
        onProfileSet = { profile, enabled ->
            logger.profileSet(profile, enabled)
            events.profileStateChanged.forEach { it(profile, enabled) }
        }
        // PE
        processPropertyCapabilitiesInquiry = { msg ->
            logger.propertyGetCapabilitiesInquiry(msg)
            events.propertyCapabilityInquiryReceived.forEach { it(msg) }
            sendPropertyCapabilitiesReply(getPropertyCapabilitiesReplyFor(msg))
        }
        processGetPropertyData = { msg ->
            logger.getPropertyData(msg)
            events.getPropertyDataReceived.forEach { it(msg) }
            sendPropertyGetDataReply(msg, propertyService.getPropertyData(msg))
        }
        processSetPropertyData = { msg ->
            logger.setPropertyData(msg)
            events.setPropertyDataReceived.forEach { it(msg) }
            sendPropertySetDataReply(msg, propertyService.setPropertyData(msg))
        }
        processSubscribeProperty = { msg ->
            logger.subscribeProperty(msg)
            events.subscribePropertyReceived.forEach { it(msg) }
            sendPropertySubscribeReply(msg, propertyService.subscribeProperty(msg))
        }

        profileSet.add(Pair(MidiCIProfileId(0x7E, 1, 2, 3, 4), true))
        profileSet.add(Pair(MidiCIProfileId(0x7E, 5, 6, 7, 8), true))
    }
}