package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.citool.view.ViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random

class CIResponderModel(private val outputSender: (ciBytes: List<Byte>) -> Unit) {
    fun processCIMessage(data: List<Byte>) {
        val time = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        ViewModel.logText.value += "[${time.time.toString().substring(0, 8)}] SYSEX: " + data.joinToString { it.toString(16) } + "\n"
        responder.processInput(data)
    }

    private val logger = Logger()

    class Events {
        val discoveryReceived = mutableListOf<(msg: Message.DiscoveryInquiry) -> Unit>()
        val endpointInquiryReceived = mutableListOf<(msg: Message.EndpointInquiry) -> Unit>()
        val profileInquiryReceived = mutableListOf<(msg: Message.ProfileInquiry) -> Unit>()
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
        productInstanceId = "ktmidi-ci" + (Random.nextInt() % 65536)

        // Unknown
        processUnknownCIMessage = { data ->
            logger.nak(data)
            events.unknownMessageReceived.forEach { it(data) }
            sendNakForUnknownCIMessage(data)
        }
        // Discovery
        processDiscovery = { msg ->
            logger.discovery(msg)
            events.discoveryReceived.forEach { it(msg) }
            val reply = getDiscoveryReplyForInquiry(msg)
            logger.discoveryReply(reply)
            sendDiscoveryReply(reply)
        }
        processEndpointMessage = { msg ->
            logger.endpointInquiry(msg)
            events.endpointInquiryReceived.forEach { it(msg) }
            val reply = getEndpointReplyForInquiry(msg)
            logger.endpointReply(reply)
            sendEndpointReply(reply)
        }
        // Profile
        processProfileInquiry = { msg ->
            logger.profileInquiry(msg)
            events.profileInquiryReceived.forEach { it(msg) }
            val reply = getProfileReplyForInquiry(msg)
            logger.profileReply(reply)
            sendProfileReply(reply)
        }
        onProfileSet = { profile, enabled ->
            logger.profileSet(profile, enabled)
            events.profileStateChanged.forEach { it(profile, enabled) }
        }
        // PE
        processPropertyCapabilitiesInquiry = { msg ->
            logger.propertyGetCapabilitiesInquiry(msg)
            events.propertyCapabilityInquiryReceived.forEach { it(msg) }
            val reply = getPropertyCapabilitiesReplyFor(msg)
            logger.propertyGetCapabilitiesReply(reply)
            sendPropertyCapabilitiesReply(reply)
        }
        processGetPropertyData = { msg ->
            logger.getPropertyData(msg)
            events.getPropertyDataReceived.forEach { it(msg) }
            val reply = propertyService.getPropertyData(msg)
            logger.getPropertyDataReply(reply)
            sendPropertyGetDataReply(reply)
        }
        processSetPropertyData = { msg ->
            logger.setPropertyData(msg)
            events.setPropertyDataReceived.forEach { it(msg) }
            val reply = propertyService.setPropertyData(msg)
            sendPropertySetDataReply(reply)
        }
        processSubscribeProperty = { msg ->
            logger.subscribeProperty(msg)
            events.subscribePropertyReceived.forEach { it(msg) }
            val reply = propertyService.subscribeProperty(msg)
            logger.subscribePropertyReply(reply)
            sendPropertySubscribeReply(reply)
        }

        profileSet.add(Pair(MidiCIProfileId(0x7E, 1, 2, 3, 4), true))
        profileSet.add(Pair(MidiCIProfileId(0x7E, 5, 6, 7, 8), true))
    }
}