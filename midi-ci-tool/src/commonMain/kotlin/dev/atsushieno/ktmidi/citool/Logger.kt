package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.ci.Message
import dev.atsushieno.ktmidi.citool.view.ViewModel

class Logger {
    fun nak(data: List<Byte>) {
        ViewModel.log("- NAK(${data.joinToString { it.toString(16) }}")
    }

    private fun logMessage(msg: Any) {
        ViewModel.log("- $msg")
    }

    fun discovery(msg: Message.DiscoveryInquiry) = logMessage(msg)

    fun discoveryReply(msg: Message.DiscoveryReply) = logMessage(msg)

    fun endpointInquiry(msg: Message.EndpointInquiry) = logMessage(msg)

    fun endpointReply(msg: Message.EndpointReply) = logMessage(msg)

    fun profileInquiry(msg: Message.ProfileInquiry) = logMessage(msg)

    fun profileReply(msg: Message.ProfileReply) = logMessage(msg)

    fun profileAdded(msg: Message.ProfileAdded) = logMessage(msg)

    fun profileRemoved(msg: Message.ProfileRemoved) = logMessage(msg)

    fun setProfileOn(msg: Message.SetProfileOn) = logMessage(msg)

    fun setProfileOff(msg: Message.SetProfileOff) = logMessage(msg)

    fun profileEnabled(msg: Message.ProfileEnabled) = logMessage(msg)

    fun profileDisabled(msg: Message.ProfileDisabled) = logMessage(msg)

    fun propertyGetCapabilitiesInquiry(msg: Message.PropertyGetCapabilities) = logMessage(msg)

    fun propertyGetCapabilitiesReply(msg: Message.PropertyGetCapabilitiesReply) = logMessage(msg)

    fun getPropertyData(msg: Message.GetPropertyData) = logMessage(msg)

    fun getPropertyDataReply(msg: Message.GetPropertyDataReply) = logMessage(msg)

    fun setPropertyData(msg: Message.SetPropertyData) = logMessage(msg)

    fun setPropertyDataReply(msg: Message.SetPropertyDataReply) = logMessage(msg)

    fun subscribeProperty(msg: Message.SubscribeProperty) = logMessage(msg)

    fun subscribePropertyReply(msg: Message.SubscribePropertyReply) = logMessage(msg)
}