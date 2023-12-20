package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.ci.Message
import dev.atsushieno.ktmidi.ci.MidiCIDeviceInfo
import dev.atsushieno.ktmidi.ci.MidiCIInitiator
import dev.atsushieno.ktmidi.ci.MidiCIProfileId
import dev.atsushieno.ktmidi.citool.view.ViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class CIInitiatorModel(private val outputSender: (ciBytes: List<Byte>) -> Unit) {
    fun processCIMessage(data: List<Byte>) {
        val time = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        ViewModel.logText.value += "[${
            time.time.toString().substring(0, 8)
        }] SYSEX: " + data.joinToString { it.toString(16) } + "\n"
        initiator.processInput(data)
    }

    private val logger = Logger()

    class Events {
        val discoveryReplyReceived = mutableListOf<(Message.DiscoveryReply) -> Unit>()
        val endpointReplyReceived = mutableListOf<(Message.EndpointReply) -> Unit>()
        val profileInquiryReplyReceived = mutableListOf<(source: Byte, initiatorMUID: Int, destinationMUID: Int) -> Unit>()
        val unknownMessageReceived = mutableListOf<(data: List<Byte>) -> Unit>()
        val propertyCapabilityReplyReceived = mutableListOf<(Message.PropertyGetCapabilitiesReply) -> Unit>()
        val getPropertyDataReplyReceived = mutableListOf<(msg: Message.GetPropertyDataReply) -> Unit>()
        val setPropertyDataReplyReceived = mutableListOf<(msg: Message.SetPropertyDataReply) -> Unit>()
        val subscribePropertyReceived = mutableListOf<(msg: Message.SubscribePropertyReply) -> Unit>()
    }

    private val events = Events()

    private val device = MidiCIDeviceInfo(1,2,1,1,
        "atsushieno", "KtMidi", "KtMidi-CI-Tool Initiator", "0.1")

    private val initiator = MidiCIInitiator(device, { data ->
        ViewModel.logText.value += "[I] REQUEST SYSEX: " + data.joinToString { it.toString(16) } + "\n"
        outputSender(data)
    }).apply {
        productInstanceId = "KtMidi-CI-Tool Responder"

        // Unknown
        processUnknownCIMessage = { data ->
            logger.nak(data)
            events.unknownMessageReceived.forEach { it(data) }
            sendNakForUnknownCIMessage(data)
        }

        processDiscoveryReply = { msg ->
            logger.discoveryReply(msg)
            events.discoveryReplyReceived.forEach { it(msg) }
            handleNewEndpoint(msg)
        }

        processEndpointReply = { msg ->
            logger.endpointReply(msg)
            events.endpointReplyReceived.forEach { it(msg) }
            defaultProcessEndpointReply(msg)
        }

        processPropertyCapabilitiesReply = { msg ->
            logger.propertyGetCapabilitiesReply(msg)
            events.propertyCapabilityReplyReceived.forEach { it(msg) }
            defaultProcessPropertyCapabilitiesReply(msg)
        }

        processGetDataReply = { msg ->
            logger.getPropertyDataReply(msg)
            events.getPropertyDataReplyReceived.forEach { it(msg) }
            defaultProcessGetDataReply(msg)
        }

        processSetDataReply = { msg ->
            logger.setPropertyDataReply(msg)
            events.setPropertyDataReplyReceived.forEach { it(msg) }
            // nothing to delegate further
        }
    }

    fun sendDiscovery() {
        initiator.sendDiscovery()
    }
}

