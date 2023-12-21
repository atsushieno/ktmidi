package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.ci.Message
import dev.atsushieno.ktmidi.ci.MidiCIProfileId
import dev.atsushieno.ktmidi.citool.view.ViewModel

class Logger {
    fun nak(data: List<Byte>) {
        ViewModel.logText.value += "- NAK(${data.joinToString { it.toString(16) }}\n"
    }

    private fun logMessage(msg: Any) {
        ViewModel.logText.value += "- $msg\n"
    }

    fun discovery(msg: Message.DiscoveryInquiry) = logMessage(msg)

    fun discoveryReply(msg: Message.DiscoveryReply) = logMessage(msg)

    fun endpointInquiry(msg: Message.EndpointInquiry) = logMessage(msg)

    fun endpointReply(msg: Message.EndpointReply) = logMessage(msg)

    fun profileInquiry(msg: Message.ProfileInquiry) = logMessage(msg)

    fun profileReply(msg: Message.ProfileReply) = logMessage(msg)

    fun profileAdded(msg: Message.ProfileAdded) = logMessage(msg)

    fun profileRemoved(msg: Message.ProfileRemoved) = logMessage(msg)

    fun profileSet(profile: MidiCIProfileId, enabled: Boolean) {
        ViewModel.logText.value += "- Profile${if (enabled) "Enabled" else "Disabled"}($profile})\n"
    }

    fun propertyGetCapabilitiesInquiry(msg: Message.PropertyGetCapabilities) {
        ViewModel.logText.value += "- PropertyGetCapabilities(destination = ${msg.destination}, sourceMUID = ${msg.sourceMUID.toString(16)}, destinationMUID = ${msg.destinationMUID.toString(16)}, max = ${msg.max})\n"
    }

    fun propertyGetCapabilitiesReply(msg: Message.PropertyGetCapabilitiesReply) {
        ViewModel.logText.value += "- PropertyGetCapabilitiesReply(destination = ${msg.destination}, sourceMUID = ${msg.sourceMUID.toString(16)}, destinationMUID = ${msg.destinationMUID.toString(16)}, max = ${msg.max})\n"
    }

    fun getPropertyData(msg: Message.GetPropertyData) {
        ViewModel.logText.value += "- GetPropertyData(sourceMUID = ${msg.sourceMUID.toString(16)}, destinationMUID = ${msg.destinationMUID.toString(16)}, requestId = ${msg.requestId}, header = ${msg.header.toByteArray().decodeToString()})\n"
    }

    fun getPropertyDataReply(msg: Message.GetPropertyDataReply) {
        ViewModel.logText.value += "- GetPropertyDataReply(sourceMUID = ${msg.sourceMUID.toString(16)}, destinationMUID = ${msg.destinationMUID.toString(16)}, requestId = ${msg.requestId}, header = ${msg.header.toByteArray().decodeToString()}, body = ${msg.body.toByteArray().decodeToString()})\n"
    }

    fun setPropertyData(msg: Message.SetPropertyData) {
        ViewModel.logText.value += "- SetPropertyData(sourceMUID = ${msg.sourceMUID.toString(16)}, destinationMUID = ${msg.destinationMUID.toString(16)}, requestId =  ${msg.requestId}, header = ${msg.header.toByteArray().decodeToString()}, body = ${msg.body.toByteArray().decodeToString()})\n"
    }

    fun setPropertyDataReply(msg: Message.SetPropertyDataReply) {
        ViewModel.logText.value += "- SetPropertyDataReply(sourceMUID = ${msg.sourceMUID.toString(16)}, destinationMUID = ${msg.destinationMUID.toString(16)}, requestId =  ${msg.requestId}, header = ${msg.header.toByteArray().decodeToString()})\n"
    }

    fun subscribeProperty(msg: Message.SubscribeProperty) {
        ViewModel.logText.value += "- SubscribeProperty(sourceMUID = ${msg.sourceMUID.toString(16)}, destinationMUID = ${msg.destinationMUID.toString(16)}, requestId =  ${msg.requestId}, header = ${msg.header.toByteArray().decodeToString()})\n"
    }

    fun subscribePropertyReply(msg: Message.SubscribePropertyReply) {
        ViewModel.logText.value += "- $msg\n"
    }
}