package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.ci.Message
import dev.atsushieno.ktmidi.ci.MidiCIProfileId
import dev.atsushieno.ktmidi.citool.view.ViewModel

class Logger {
    fun nak(data: List<Byte>) {
        ViewModel.logText.value += "- NAK(${data.joinToString { it.toString(16) }}\n"
    }

    fun discovery(msg: Message.DiscoveryInquiry) {
        ViewModel.logText.value += "- Discovery(device = ${msg.device}, muid = ${msg.muid.toString(16)}, category = ${msg.ciCategorySupported}, maxSysEx = ${msg.receivableMaxSysExSize}, outputPathId = ${msg.outputPathId})\n"
    }

    fun discoveryReply(msg: Message.DiscoveryReply) {
        ViewModel.logText.value += "- DiscoveryReply(device = ${msg.device}, sourceMUID = ${msg.sourceMUID.toString(16)}, destinationMUID = ${msg.destinationMUID.toString(16)})\n"
    }

    fun endpointMessage(msg: Message.EndpointInquiry) {
        ViewModel.logText.value += "- Endpoint(sourceMUID = ${msg.sourceMUID.toString(16)}, destinationMUID = ${msg.destinationMUID.toString(16)}, ${msg.status})\n"
    }

    fun endpointReply(msg: Message.EndpointReply) {
        ViewModel.logText.value += "- EndpointReply(status = ${msg.status}, sourceMUID = ${msg.sourceMUID.toString(16)}, destinationMUID = ${msg.destinationMUID.toString(16)}, data = (string: ${msg.data.toByteArray().decodeToString()}, bytes: ${msg.data.joinToString { it.toString(16) }}))\n"
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
}