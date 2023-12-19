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

    class Logger {
        fun nak(data: List<Byte>) {
            ViewModel.logText.value += "- NAK(${data.joinToString { it.toString(16) }}\n"
        }

        fun discovery(initiatorMUID: Int, initiatorOutputPath: Byte) {
            ViewModel.logText.value += "- Discovery(initiatorMUID = ${initiatorMUID.toString(16)}, initiatorOutputPath = $initiatorOutputPath)\n"
        }

        fun endpointMessage(initiatorMUID: Int, destinationMUID: Int, status: Byte) {
            ViewModel.logText.value += "- Endpoint(initiatorMUID = ${initiatorMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, $status)\n"
        }

        fun profileInquiry(source: Byte, sourceMUID: Int, destinationMUID: Int) {
            ViewModel.logText.value += "- ProfileInquiry(source = $source, sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)})\n"
        }

        fun profileSet(profile: MidiCIProfileId, enabled: Boolean) {
            ViewModel.logText.value += "- Profile${if (enabled) "Enabled" else "Disabled"}($profile})\n"
        }

        fun propertyGetCapabilitiesInquiry(destination: Byte, sourceMUID: Int, destinationMUID: Int, max: Byte) {
            ViewModel.logText.value += "- PropertyGetCapabilities(destination = $destination, sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, max = $max)\n"
        }

        fun getPropertyData(sourceMUID: Int, destinationMUID: Int, requestId: Byte, header: List<Byte>) {
            ViewModel.logText.value += "- GetPropertyData(sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, requestId = $requestId, header = ${header.toByteArray().decodeToString()})\n"
        }

        fun setPropertyData(sourceMUID: Int, destinationMUID: Int, requestId: Byte, header: List<Byte>, body: List<Byte>) {
            ViewModel.logText.value += "- SetPropertyData(sourceMUID = ${sourceMUID.toString(16)}, destinationMUID = ${destinationMUID.toString(16)}, requestId =  $requestId, header = ${header.toByteArray().decodeToString()}, body = ${body.toByteArray().decodeToString()})\n"
        }

    }

    private val logger = Logger()

    class Events {
        val discoveryReceived = mutableListOf<(initiatorMUID: Int, initiatorOutputPath: Byte) -> Unit>()
        val endpointInquiryReceived = mutableListOf<(initiatorMUID: Int, destinationMUID: Int, status: Byte) -> Unit>()
        val processInquiryReceived = mutableListOf<(source: Byte, initiatorMUID: Int, destinationMUID: Int) -> Unit>()
        val profileStateChanged = mutableListOf<(profile: MidiCIProfileId, enabled: Boolean) -> Unit>()
        val unknownMessageReceived = mutableListOf<(data: List<Byte>) -> Unit>()
        val propertyCapabilityInquiryReceived = mutableListOf<(destination: Byte, sourceMUID: Int, destinationMUID: Int, max: Byte) -> Unit>()
        val getPropertyDataReceived = mutableListOf<(sourceMUID: Int, destinationMUID: Int, requestId: Byte, header: List<Byte>) -> Unit>()
        val setPropertyDataReceived = mutableListOf<(sourceMUID: Int, destinationMUID: Int, requestId: Byte, header: List<Byte>, data: List<Byte>) -> Json.JsonValue>()
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
            logger.nak(data)
            events.unknownMessageReceived.forEach { it(data) }
            sendNakForUnknownCIMessage(data)
        }
        // Endpoint
        processDiscovery = { initiatorMUID, initiatorOutputPath ->
            logger.discovery(initiatorMUID, initiatorOutputPath)
            events.discoveryReceived.forEach { it(initiatorMUID, initiatorOutputPath) }
            sendDiscoveryReplyForInquiry(initiatorMUID, initiatorOutputPath)
        }
        processEndpointMessage = { initiatorMUID, destinationMUID, status ->
            logger.endpointMessage(initiatorMUID, destinationMUID, status)
            events.endpointInquiryReceived.forEach { it(initiatorMUID, destinationMUID, status) }
            sendEndpointReplyForInquiry(initiatorMUID, destinationMUID, status)
        }
        // Profile
        processProfileInquiry = { source, sourceMUID, destinationMUID ->
            logger.profileInquiry(source, sourceMUID, destinationMUID)
            events.processInquiryReceived.forEach { it(source, sourceMUID, destinationMUID) }
            sendProfileReplyForInquiry(source, sourceMUID, destinationMUID)
        }
        onProfileSet = { profile, enabled ->
            logger.profileSet(profile, enabled)
            events.profileStateChanged.forEach { it(profile, enabled) }
        }
        // PE
        processPropertyCapabilitiesInquiry = { destination, sourceMUID, destinationMUID, max ->
            logger.propertyGetCapabilitiesInquiry(destination, sourceMUID, destinationMUID, max)
            events.propertyCapabilityInquiryReceived.forEach { it(destination, sourceMUID, destinationMUID, max) }
            sendPropertyCapabilitiesReply(destination, sourceMUID, destinationMUID, max)
        }
        getProperty = { sourceMUID, destinationMUID, requestId, header ->
            logger.getPropertyData(sourceMUID, destinationMUID, requestId, header)
            events.getPropertyDataReceived.forEach { it(sourceMUID, destinationMUID, requestId, header) }

            val jsonInquiry = Json.parse(PropertyCommonConverter.decodeASCIIToString(header.toByteArray().decodeToString()))
            getPropertyJson(jsonInquiry)
        }
        setProperty = { sourceMUID, destinationMUID, requestId, header, body ->
            logger.setPropertyData(sourceMUID, destinationMUID, requestId, header, body)
            events.setPropertyDataReceived.forEach { it(sourceMUID, destinationMUID, requestId, header, body) }

            val jsonInquiryHeader = Json.parse(PropertyCommonConverter.decodeASCIIToString(header.toByteArray().decodeToString()))
            val jsonInquiryBody = Json.parse(PropertyCommonConverter.decodeASCIIToString(body.toByteArray().decodeToString()))
            setPropertyJson(jsonInquiryHeader, jsonInquiryBody)
        }

        profileSet.add(Pair(MidiCIProfileId(0x7E, 1, 2, 3, 4), true))
        profileSet.add(Pair(MidiCIProfileId(0x7E, 5, 6, 7, 8), true))
    }
}