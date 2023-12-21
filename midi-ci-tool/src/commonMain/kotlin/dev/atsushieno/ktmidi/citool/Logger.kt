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

    fun propertyGetCapabilitiesInquiry(msg: Message.PropertyGetCapabilities) = logMessage(msg)

    fun propertyGetCapabilitiesReply(msg: Message.PropertyGetCapabilitiesReply) = logMessage(msg)

    fun getPropertyData(msg: Message.GetPropertyData) = logMessage(msg)

    fun getPropertyDataReply(msg: Message.GetPropertyDataReply) = logMessage(msg)

    fun setPropertyData(msg: Message.SetPropertyData) = logMessage(msg)

    fun setPropertyDataReply(msg: Message.SetPropertyDataReply) = logMessage(msg)

    fun subscribeProperty(msg: Message.SubscribeProperty) = logMessage(msg)

    fun subscribePropertyReply(msg: Message.SubscribePropertyReply) = logMessage(msg)
}