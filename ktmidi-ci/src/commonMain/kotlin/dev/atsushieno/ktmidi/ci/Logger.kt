package dev.atsushieno.ktmidi.ci

class Logger {
    val logEventReceived = mutableListOf<(msg: Any)->Unit>()

    private fun logMessage(msg: Any) {
        logEventReceived.forEach { it(msg) }
    }

    fun nak(data: List<Byte>) {
        logMessage("- NAK(${data.joinToString { it.toString(16) }}")
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

    fun profileDetails(msg: Message.ProfileDetailsInquiry) = logMessage(msg)

    fun profileDetailsReply(msg: Message.ProfileDetailsReply) = logMessage(msg)

    fun propertyGetCapabilitiesInquiry(msg: Message.PropertyGetCapabilities) = logMessage(msg)

    fun propertyGetCapabilitiesReply(msg: Message.PropertyGetCapabilitiesReply) = logMessage(msg)

    fun getPropertyData(msg: Message.GetPropertyData) = logMessage(msg)

    fun getPropertyDataReply(msg: Message.GetPropertyDataReply) = logMessage(msg)

    fun setPropertyData(msg: Message.SetPropertyData) = logMessage(msg)

    fun setPropertyDataReply(msg: Message.SetPropertyDataReply) = logMessage(msg)

    fun subscribeProperty(msg: Message.SubscribeProperty) = logMessage(msg)

    fun subscribePropertyReply(msg: Message.SubscribePropertyReply) = logMessage(msg)

    fun propertyNotify(msg: Message.PropertyNotify) = logMessage(msg)

    fun processInquiry(msg: Message.ProcessInquiry) = logMessage(msg)
    fun processInquiryReply(msg: Message.ProcessInquiryReply) = logMessage(msg)
    fun midiMessageReport(msg: Message.ProcessMidiMessageReport) = logMessage(msg)
    fun midiMessageReportReply(msg: Message.ProcessMidiMessageReportReply) = logMessage(msg)
    fun endOfMidiMessageReport(msg: Message.ProcessEndOfMidiMessageReport) = logMessage(msg)
}