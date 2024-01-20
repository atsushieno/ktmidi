package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.propertycommonrules.CommonRulesPropertyService
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyCommonHeaderKeys
import dev.atsushieno.ktmidi.ci.propertycommonrules.SubscriptionEntry
import kotlin.experimental.and

/**
 * This class is responsible only for one input/output connection pair
 *
 * The argument `sendOutput` takes a sysex bytestream which is NOT specific to MIDI 1.0 bytestream (i.e. it should
 * support sysex7 UMPs) and thus does NOT contain F0 and F7.
 * Same goes for `processInput()` function.
 *
 */
class MidiCIResponder(
    val parent: MidiCIDevice,
    val config: MidiCIResponderConfiguration,
    private val sendOutput: (group: Byte, data: List<Byte>) -> Unit,
    private val sendMidiMessageReport: (protocol: MidiMessageReportProtocol, data: List<Byte>) -> Unit
) {
    val muid by parent::muid
    val device by parent::device
    val events by parent::events
    val logger by parent::logger

    val propertyService by lazy { CommonRulesPropertyService(logger, muid, device, config.propertyValues, config.propertyMetadataList) }
    val properties by lazy { ServiceObservablePropertyList(config.propertyValues, propertyService) }
    val subscriptions: List<SubscriptionEntry> by propertyService::subscriptions

    var midiMessageReporter: MidiMessageReporter = object : MidiMessageReporter {
        // stub implementation
        override val midiTransportProtocol = MidiMessageReportProtocol.Midi1Stream

        override fun reportMidiMessages(
            groupAddress: Byte,
            channelAddress: Byte,
            messageDataControl: Byte,
            midiMessageReportSystemMessages: Byte,
            midiMessageReportChannelControllerMessages: Byte,
            midiMessageReportNoteDataMessages: Byte
        ): Sequence<List<Byte>> = sequenceOf()
    }

    private var requestIdSerial: Byte = 0

    // update property value. It involves updates to subscribers
    fun updatePropertyValue(group: Byte, propertyId: String, data: List<Byte>, isPartial: Boolean) {
        properties.values.first { it.id == propertyId }.body = data
        notifyPropertyUpdatesToSubscribers(group, propertyId, data, isPartial)
    }

    var notifyPropertyUpdatesToSubscribers: (group: Byte, propertyId: String, data: List<Byte>, isPartial: Boolean) -> Unit = { group, propertyId, data, isPartial ->
        createPropertyNotification(group, propertyId, data, isPartial).forEach { msg ->
            notifyPropertyUpdatesToSubscribers(msg)
        }
    }
    private fun createPropertyNotification(group: Byte, propertyId: String, data: List<Byte>, isPartial: Boolean): Sequence<Message.SubscribeProperty> = sequence {
        var lastEncoding: String? = null
        var lastEncodedData = data
        subscriptions.filter { it.resource == propertyId }.forEach {
            val encodedData = if (it.encoding == lastEncoding) lastEncodedData else if (it.encoding == null) data else propertyService.encodeBody(data, it.encoding)
            // do not invoke encodeBody() many times.
            if (it.encoding != lastEncoding && it.encoding != null) {
                lastEncoding = it.encoding
                lastEncodedData = encodedData
            }
            val header = propertyService.createUpdateNotificationHeader(it.resource, mapOf(
                PropertyCommonHeaderKeys.SUBSCRIBE_ID to it.subscribeId,
                PropertyCommonHeaderKeys.SET_PARTIAL to isPartial,
                PropertyCommonHeaderKeys.MUTUAL_ENCODING to it.encoding))
            yield(Message.SubscribeProperty(Message.Common(muid, MidiCIConstants.BROADCAST_MUID_32, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group),
                requestIdSerial++, header, encodedData))
        }
    }

    fun notifyPropertyUpdatesToSubscribers(msg: Message.SubscribeProperty) = sendPropertySubscription(msg)

    // Notify end of subscription updates
    fun sendPropertySubscription(msg: Message.SubscribeProperty) {
        logger.logMessage(msg, MessageDirection.Out)
        val dst = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        CIFactory.midiCIPropertyChunks(
            dst, parent.config.maxPropertyChunkSize, CISubId2.PROPERTY_SUBSCRIBE,
            msg.sourceMUID, msg.destinationMUID, msg.requestId, msg.header, msg.body
        ).forEach {
            sendOutput(msg.group, it)
        }
    }
    fun terminateSubscriptions(group: Byte) {
        propertyService.subscriptions.forEach {
            val msg = Message.SubscribeProperty(Message.Common(muid, it.muid, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group),
                requestIdSerial++,
                propertyService.createTerminateNotificationHeader(it.subscribeId), listOf()
            )
            sendPropertySubscription(msg)
        }
    }

    // Message handlers

    // Property Exchange

    // Should this also delegate to property service...?
    fun sendPropertyCapabilitiesReply(msg: Message.PropertyGetCapabilitiesReply) {
        logger.logMessage(msg, MessageDirection.Out)
        val dst = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        sendOutput(msg.group, CIFactory.midiCIPropertyGetCapabilities(dst, msg.address, true,
            msg.sourceMUID, msg.destinationMUID, msg.maxSimultaneousRequests))
    }
    val getPropertyCapabilitiesReplyFor: (msg: Message.PropertyGetCapabilities) -> Message.PropertyGetCapabilitiesReply = { msg ->
        val establishedMaxSimultaneousPropertyRequests =
            if (msg.maxSimultaneousRequests > parent.config.maxSimultaneousPropertyRequests) parent.config.maxSimultaneousPropertyRequests
            else msg.maxSimultaneousRequests
        Message.PropertyGetCapabilitiesReply(Message.Common(muid, msg.sourceMUID, msg.address, msg.group),
            establishedMaxSimultaneousPropertyRequests)
    }
    var processPropertyCapabilitiesInquiry: (msg: Message.PropertyGetCapabilities) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.propertyCapabilityInquiryReceived.forEach { it(msg) }
        val reply = getPropertyCapabilitiesReplyFor(msg)
        sendPropertyCapabilitiesReply(reply)
    }

    fun sendPropertyGetDataReply(msg: Message.GetPropertyDataReply) {
        logger.logMessage(msg, MessageDirection.Out)
        val dst = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        CIFactory.midiCIPropertyChunks(
            dst, parent.config.maxPropertyChunkSize, CISubId2.PROPERTY_GET_DATA_REPLY,
            msg.sourceMUID, msg.destinationMUID, msg.requestId, msg.header, msg.body
        ).forEach {
            sendOutput(msg.group, it)
        }
    }
    var processGetPropertyData: (msg: Message.GetPropertyData) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.getPropertyDataReceived.forEach { it(msg) }
        val reply = propertyService.getPropertyData(msg)
        if (reply.isSuccess) {
            sendPropertyGetDataReply(reply.getOrNull()!!)
        }
        else
            logger.logError(reply.exceptionOrNull()?.message ?: "Incoming GetPropertyData message resulted in an error")
    }

    fun sendPropertySetDataReply(msg: Message.SetPropertyDataReply) {
        logger.logMessage(msg, MessageDirection.Out)
        val dst = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        CIFactory.midiCIPropertyChunks(
            dst, parent.config.maxPropertyChunkSize, CISubId2.PROPERTY_SET_DATA_REPLY,
            msg.sourceMUID, msg.destinationMUID, msg.requestId, msg.header, listOf()
        ).forEach {
            sendOutput(msg.group, it)
        }
    }
    var processSetPropertyData: (msg: Message.SetPropertyData) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.setPropertyDataReceived.forEach { it(msg) }
        val reply = propertyService.setPropertyData(msg)
        if (reply.isSuccess)
            sendPropertySetDataReply(reply.getOrNull()!!)
        else
            logger.logError(reply.exceptionOrNull()?.message ?: "Incoming SetPropertyData message resulted in an error")
    }

    fun sendPropertySubscribeReply(msg: Message.SubscribePropertyReply) {
        logger.logMessage(msg, MessageDirection.Out)
        val dst = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        CIFactory.midiCIPropertyChunks(
            dst, parent.config.maxPropertyChunkSize, CISubId2.PROPERTY_SUBSCRIBE_REPLY,
            msg.sourceMUID, msg.destinationMUID, msg.requestId, msg.header, msg.body
        ).forEach {
            sendOutput(msg.group, it)
        }
    }
    var processSubscribeProperty: (msg: Message.SubscribeProperty) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.subscribePropertyReceived.forEach { it(msg) }
        val reply = propertyService.subscribeProperty(msg)
        if (reply.isSuccess)
            sendPropertySubscribeReply(reply.getOrNull()!!)
        else
            logger.logError(reply.exceptionOrNull()?.message ?: "Incoming SubscribeProperty message resulted in an error")
    }

    // It receives reply to property notifications
    var processSubscribePropertyReply: (msg: Message.SubscribePropertyReply) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.subscribePropertyReplyReceived.forEach { it(msg) }
    }

    // Process Inquiry

    fun getProcessInquiryReplyFor(msg: Message.ProcessInquiry) =
        Message.ProcessInquiryReply(Message.Common(muid, msg.sourceMUID, msg.address, msg.group),
            config.processInquirySupportedFeatures)
    fun sendProcessProcessInquiryReply(msg: Message.ProcessInquiryReply) {
        logger.logMessage(msg, MessageDirection.Out)
        val dst = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        sendOutput(msg.group, CIFactory.midiCIProcessInquiryCapabilitiesReply(
            dst, msg.sourceMUID, msg.destinationMUID, msg.supportedFeatures))
    }
    var processProcessInquiry: (msg: Message.ProcessInquiry) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.processInquiryReceived.forEach { it(msg) }
        sendProcessProcessInquiryReply(getProcessInquiryReplyFor(msg))
    }

    fun getMidiMessageReportReplyFor(msg: Message.MidiMessageReportInquiry) =
        Message.MidiMessageReportReply(Message.Common(muid, msg.sourceMUID, msg.address, msg.group),
            msg.systemMessages and config.midiMessageReportSystemMessages,
            msg.channelControllerMessages and config.midiMessageReportChannelControllerMessages,
            msg.noteDataMessages and config.midiMessageReportNoteDataMessages)
    fun sendMidiMessageReportReply(msg: Message.MidiMessageReportReply) {
        logger.logMessage(msg, MessageDirection.Out)
        val dst = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        sendOutput(msg.group, CIFactory.midiCIMidiMessageReportReply(dst, msg.address,
            msg.sourceMUID, msg.destinationMUID,
            msg.systemMessages, msg.channelControllerMessages, msg.noteDataMessages))
    }
    fun getEndOfMidiMessageReportFor(msg: Message.MidiMessageReportInquiry) =
        Message.MidiMessageReportNotifyEnd(Message.Common(muid, msg.sourceMUID, msg.address, msg.group))
    fun sendEndOfMidiMessageReport(msg: Message.MidiMessageReportNotifyEnd) {
        logger.logMessage(msg, MessageDirection.Out)
        val dst = MutableList<Byte>(parent.config.receivableMaxSysExSize) { 0 }
        sendOutput(msg.group, CIFactory.midiCIEndOfMidiMessage(dst, msg.address, msg.sourceMUID, msg.destinationMUID))
    }
    fun defaultProcessMidiMessageReport(msg: Message.MidiMessageReportInquiry) {
        sendMidiMessageReportReply(getMidiMessageReportReplyFor(msg))

        // send specified MIDI messages
        midiMessageReporter.reportMidiMessages(
            msg.group,
            msg.address,
            config.midiMessageReportMessageDataControl,
            config.midiMessageReportSystemMessages,
            config.midiMessageReportChannelControllerMessages,
            config.midiMessageReportNoteDataMessages
        ).forEach {
            sendMidiMessageReport(midiMessageReporter.midiTransportProtocol, it)
        }

        sendEndOfMidiMessageReport(getEndOfMidiMessageReportFor(msg))
    }
    var processMidiMessageReport: (msg: Message.MidiMessageReportInquiry) -> Unit = { msg ->
        logger.logMessage(msg, MessageDirection.In)
        events.midiMessageReportReceived.forEach { it(msg) }
        defaultProcessMidiMessageReport(msg)
    }

    init {
        if (muid != muid and 0x7F7F7F7F)
            throw IllegalArgumentException("muid must consist of 7-bit byte values i.e. each 8-bit number must not have the topmost bit as 0. (`muid` must be equivalent to `muid and 0x7F7F7F7F`")
    }
}