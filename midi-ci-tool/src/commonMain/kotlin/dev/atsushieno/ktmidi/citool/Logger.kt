package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.ci.Message
import dev.atsushieno.ktmidi.ci.MidiCIProfileId
import dev.atsushieno.ktmidi.citool.view.ViewModel

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

    fun propertyGetCapabilitiesInquiry(msg: Message.PropertyGetCapabilities) {
        ViewModel.logText.value += "- PropertyGetCapabilities(destination = ${msg.destination}, sourceMUID = ${msg.sourceMUID.toString(16)}, destinationMUID = ${msg.destinationMUID.toString(16)}, max = ${msg.max})\n"
    }

    fun getPropertyData(msg: Message.GetPropertyData) {
        ViewModel.logText.value += "- GetPropertyData(sourceMUID = ${msg.sourceMUID.toString(16)}, destinationMUID = ${msg.destinationMUID.toString(16)}, requestId = ${msg.requestId}, header = ${msg.header.toByteArray().decodeToString()})\n"
    }

    fun setPropertyData(msg: Message.SetPropertyData) {
        ViewModel.logText.value += "- SetPropertyData(sourceMUID = ${msg.sourceMUID.toString(16)}, destinationMUID = ${msg.destinationMUID.toString(16)}, requestId =  ${msg.requestId}, header = ${msg.header.toByteArray().decodeToString()}, body = ${msg.body.toByteArray().decodeToString()})\n"
    }

    fun subscribeProperty(msg: Message.SubscribeProperty) {
        ViewModel.logText.value += "- SubscribeProperty(sourceMUID = ${msg.sourceMUID.toString(16)}, destinationMUID = ${msg.destinationMUID.toString(16)}, requestId =  ${msg.requestId}, header = ${msg.header.toByteArray().decodeToString()})\n"
    }
}