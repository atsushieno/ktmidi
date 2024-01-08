package dev.atsushieno.ktmidi.ci

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.random.Random

/*
    Typical MIDI-CI processing flow

    - MidiCIInitiator.sendDiscovery()
    - MidiCIResponder.processInput() -> .processDiscovery
    - MidiCIInitiator.sendEndpointMessage(), .requestProfiles(), .requestPropertyExchangeCapabilities()
    - MidiCIResponder receives and processes each of the replies ...

 The argument `sendOutput` takes a sysex bytestream which is NOT specific to MIDI 1.0 bytestream (i.e. it should
 support sysex7 UMPs) and thus does NOT contain F0 and F7.
 Same goes for `processInput()` function.

*/
class MidiCIInitiator(val device: MidiCIDeviceInfo,
                      private val sendOutput: (data: List<Byte>) -> Unit,
                      // FIXME: make use of it somehow
                      val outputPathId: Byte = 0,
                      val muid: Int = Random.nextInt() and 0x7F7F7F7F) {

    class Events {
        val discoveryReplyReceived = mutableListOf<(Message.DiscoveryReply) -> Unit>()
        val endpointReplyReceived = mutableListOf<(Message.EndpointReply) -> Unit>()
        val profileInquiryReplyReceived = mutableListOf<(Message.ProfileReply) -> Unit>()
        val profileAddedReceived = mutableListOf<(Message.ProfileAdded) -> Unit>()
        val profileRemovedReceived = mutableListOf<(Message.ProfileRemoved) -> Unit>()
        val profileEnabledReceived = mutableListOf<(Message.ProfileEnabled) -> Unit>()
        val profileDisabledReceived = mutableListOf<(Message.ProfileDisabled) -> Unit>()
        val profileDetailsReplyReceived = mutableListOf<(Message.ProfileDetailsReply) -> Unit>()
        val unknownMessageReceived = mutableListOf<(data: List<Byte>) -> Unit>()
        val propertyCapabilityReplyReceived = mutableListOf<(Message.PropertyGetCapabilitiesReply) -> Unit>()
        val getPropertyDataReplyReceived = mutableListOf<(msg: Message.GetPropertyDataReply) -> Unit>()
        val setPropertyDataReplyReceived = mutableListOf<(msg: Message.SetPropertyDataReply) -> Unit>()
        val subscribePropertyReplyReceived = mutableListOf<(msg: Message.SubscribePropertyReply) -> Unit>()
        val subscribePropertyReceived = mutableListOf<(msg: Message.SubscribeProperty) -> Unit>()
        val processInquiryReplyReceived = mutableListOf<(msg: Message.ProcessInquiryReply) -> Unit>()
        val midiMessageReportReplyReceived = mutableListOf<(msg: Message.ProcessMidiMessageReportReply) -> Unit>()
        val endOfMidiMessageReportReceived = mutableListOf<(msg: Message.ProcessEndOfMidiMessageReport) -> Unit>()

    }

    val events = Events()

    val logger = Logger()

    class Connection(
        private val parent: MidiCIInitiator,
        val muid: Int,
        val device: DeviceDetails,
        var maxSimultaneousPropertyRequests: Byte = 0,
        var productInstanceId: String = "",
        val propertyClient: MidiCIPropertyClient = CommonRulesPropertyClient(parent.muid) { msg -> parent.sendGetPropertyData(msg) }
    ) {
        val profiles = ObservableProfileList()

        val properties = ClientObservablePropertyList(propertyClient)

        private val openRequests = mutableListOf<Message.GetPropertyData>()
        private val pendingSubscriptions = mutableMapOf<Byte,String>()

        fun updateProperty(msg: Message.GetPropertyDataReply) {
            val req = openRequests.firstOrNull { it.requestId == msg.requestId } ?: return
            openRequests.remove(req)
            val status = properties.getReplyStatusFor(msg.header) ?: return

            if (status == PropertyExchangeStatus.OK) {
                propertyClient.onGetPropertyDataReply(req, msg)
                properties.updateValue(req, msg)
            }
        }

        fun updateProperty(msg: Message.SubscribeProperty): Pair<String?,Message.SubscribePropertyReply?> {
            val command = properties.updateValue(msg)
            return Pair(
                command,
                Message.SubscribePropertyReply(muid, msg.sourceMUID, msg.requestId,
                    propertyClient.createStatusHeader(PropertyExchangeStatus.OK), listOf()
                )
            )
        }

        fun addPendingRequest(msg: Message.GetPropertyData) {
            openRequests.add(msg)
        }
        fun addPendingSubscription(requestId: Byte, propertyId: String) {
            pendingSubscriptions[requestId] = propertyId
        }

        fun removePendingSubscription(requestId: Byte): String? =
            pendingSubscriptions.remove(requestId)
    }

    var midiCIBufferSize = 4096
    var receivableMaxSysExSize = MidiCIConstants.DEFAULT_RECEIVABLE_MAX_SYSEX_SIZE
    var productInstanceId: String? = null
    var maxSimultaneousPropertyRequests: Byte = 1

    var autoSendEndpointInquiry = true
    var autoSendProfileInquiry = true
    var autoSendPropertyExchangeCapabilitiesInquiry = true
    var autoSendGetResourceList = true

    val connections = mutableMapOf<Int, Connection>()
    enum class ConnectionChange {
        Added,
        Removed
    }
    val connectionsChanged = mutableListOf<(change: ConnectionChange, connection: Connection) -> Unit>()

    // Initiator implementation

    // Discovery

    fun sendDiscovery(ciCategorySupported: Byte = MidiCISupportedCategories.THREE_P) =
        sendDiscovery(createDiscoveryInquiry(ciCategorySupported))
    fun createDiscoveryInquiry(ciCategorySupported: Byte = MidiCISupportedCategories.THREE_P) =
        Message.DiscoveryInquiry(muid,
            DeviceDetails(device.manufacturerId, device.familyId, device.modelId, device.versionId),
            ciCategorySupported, receivableMaxSysExSize, outputPathId)
    fun sendDiscovery(msg: Message.DiscoveryInquiry) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIDiscovery(
            buf, MidiCIConstants.CI_VERSION_AND_FORMAT, msg.muid, msg.device.manufacturer, msg.device.family, msg.device.modelNumber,
            msg.device.softwareRevisionLevel, msg.ciCategorySupported, msg.receivableMaxSysExSize, msg.outputPathId
        ))
    }

    fun sendEndpointMessage(targetMuid: Int, status: Byte = MidiCIConstants.ENDPOINT_STATUS_PRODUCT_INSTANCE_ID) =
        sendEndpointMessage(createEndpointMessage(targetMuid, status))
    fun createEndpointMessage(targetMuid: Int, status: Byte = MidiCIConstants.ENDPOINT_STATUS_PRODUCT_INSTANCE_ID) =
        Message.EndpointInquiry(muid, targetMuid, status)
    fun sendEndpointMessage(msg: Message.EndpointInquiry) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIEndpointMessage(buf, MidiCIConstants.CI_VERSION_AND_FORMAT,
            msg.sourceMUID, msg.destinationMUID, msg.status))
    }

    // Profile Configuration

    fun requestProfiles(destinationChannelOr7F: Byte, destinationMUID: Int) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIProfileInquiry(buf, destinationChannelOr7F, muid, destinationMUID))
    }

    fun setProfile(address: Byte, destinationMUID: Int, profileId: MidiCIProfileId, numChannelsRequested: Short, enabled: Boolean) =
        if (enabled)
            setProfileOn(Message.SetProfileOn(address, muid, destinationMUID, profileId, numChannelsRequested))
        else
            setProfileOff(Message.SetProfileOff(address, muid, destinationMUID, profileId))

    fun setProfileOn(msg: Message.SetProfileOn) {
        logger.setProfileOn(msg)
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIProfileSet(buf, msg.address, true, msg.sourceMUID, msg.destinationMUID, msg.profile, msg.numChannelsRequested))
    }

    fun setProfileOff(msg: Message.SetProfileOff) {
        logger.setProfileOff(msg)
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIProfileSet(buf, msg.address, false, msg.sourceMUID, msg.destinationMUID, msg.profile, 0))
    }

    fun sendProfileDetailsInquiry(address: Byte, muid: Int, profile: MidiCIProfileId, target: Byte) =
        sendProfileDetailsInquiry(Message.ProfileDetailsInquiry(address, this.muid, muid, profile, target))

    fun sendProfileDetailsInquiry(msg: Message.ProfileDetailsInquiry) {
        logger.profileDetails(msg)
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIProfileDetails(buf, msg.address, msg.sourceMUID, msg.destinationMUID, msg.profile, 0))
    }

    // Property Exchange

    fun requestPropertyExchangeCapabilities(destinationChannelOr7F: Byte, destinationMUID: Int, maxSimultaneousPropertyRequests: Byte) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIPropertyGetCapabilities(
            buf,
            destinationChannelOr7F,
            false,
            muid,
            destinationMUID,
            maxSimultaneousPropertyRequests
        ))
    }

    fun sendGetPropertyData(destinationMUID: Int, resource: String) {
        val conn = connections[destinationMUID]
        if (conn != null) {
            val header = conn.propertyClient.createRequestHeader(resource, false)
            val msg = Message.GetPropertyData(muid, destinationMUID, requestIdSerial++, header)
            logger.getPropertyData(msg)
            sendGetPropertyData(msg)
        }
    }

    fun sendGetPropertyData(msg: Message.GetPropertyData) {
        val conn = connections[msg.destinationMUID]
        conn?.addPendingRequest(msg)
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIPropertyChunks(buf, CISubId2.PROPERTY_GET_DATA_INQUIRY,
            msg.sourceMUID, msg.destinationMUID, msg.requestId, msg.header, listOf()).forEach {
            sendOutput(it)
        }
    }

    fun sendSetPropertyData(destinationMUID: Int, resource: String, data: List<Byte>, isPartial: Boolean = false) {
        val conn = connections[destinationMUID]
        if (conn != null) {
            val header = conn.propertyClient.createRequestHeader(resource, isPartial)
            // FIXME: handle body data chunks
            sendSetPropertyData(Message.SetPropertyData(muid, destinationMUID, requestIdSerial++, header, 1, 1, data))
        }
    }

    fun sendSetPropertyData(msg: Message.SetPropertyData) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIPropertyChunks(buf, CISubId2.PROPERTY_SET_DATA_INQUIRY,
            msg.sourceMUID, msg.destinationMUID, msg.requestId, msg.header, msg.body).forEach {
            sendOutput(it)
        }
    }

    fun sendSubscribeProperty(destinationMUID: Int, resource: String) {
        val conn = connections[destinationMUID]
        if (conn != null) {
            val header = conn.propertyClient.createSubscribeHeader(resource)
            val msg = Message.SubscribeProperty(muid, destinationMUID, requestIdSerial++, header, listOf())
            conn.addPendingSubscription(msg.requestId, resource)
            sendSubscribeProperty(msg)
        }
    }
    fun sendSubscribeProperty(msg: Message.SubscribeProperty) {
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIPropertyChunks(
            dst, CISubId2.PROPERTY_SUBSCRIBE,
            msg.sourceMUID, msg.destinationMUID, msg.requestId, msg.header, msg.body
        ).forEach {
            sendOutput(it)
        }
    }

    // Process Inquiry
    fun sendProcessInquiry(destinationMUID: Int) =
        sendProcessInquiry(createProcessInquiry(destinationMUID))
    fun createProcessInquiry(destinationMUID: Int) = Message.ProcessInquiry(muid, destinationMUID)
    fun sendProcessInquiry(msg: Message.ProcessInquiry) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIProcessInquiryCapabilities(buf, msg.sourceMUID, msg.destinationMUID))
    }

    fun sendMidiMessageReportInquiry(address: Byte, destinationMUID: Int,
                                     messageDataControl: Byte,
                                     systemMessages: Byte,
                                     channelControllerMessages: Byte,
                                     noteDataMessages: Byte) =
        sendMidiMessageReportInquiry(createMidiMessageReportInquiry(
            address, destinationMUID, messageDataControl, systemMessages, channelControllerMessages, noteDataMessages))
    fun createMidiMessageReportInquiry(address: Byte, destinationMUID: Int,
                                       messageDataControl: Byte,
                                       systemMessages: Byte,
                                       channelControllerMessages: Byte,
                                       noteDataMessages: Byte
                                       ) = Message.ProcessMidiMessageReport(
        address, muid, destinationMUID, messageDataControl, systemMessages, channelControllerMessages, noteDataMessages)
    fun sendMidiMessageReportInquiry(msg: Message.ProcessMidiMessageReport) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIMidiMessageReport(buf, true, msg.address, msg.sourceMUID, msg.destinationMUID,
            msg.messageDataControl, msg.systemMessages, msg.channelControllerMessages, msg.noteDataMessages))
    }

    private var requestIdSerial: Byte = 1

    // Reply handler

    val handleNewEndpoint = { msg: Message.DiscoveryReply ->
        // If successfully discovered, continue to endpoint inquiry
        val connection = Connection(this, msg.sourceMUID, msg.device)
        val existing = connections[msg.sourceMUID]
        if (existing != null) {
            // FIXME: should this involve "releasing" existing connection if any?
            connectionsChanged.forEach { it(ConnectionChange.Removed, existing) }
            connections.remove(msg.sourceMUID)
        }
        connections[msg.sourceMUID]= connection
        connectionsChanged.forEach { it(ConnectionChange.Added, connection) }

        if (autoSendEndpointInquiry)
            sendEndpointMessage(msg.sourceMUID, MidiCIConstants.ENDPOINT_STATUS_PRODUCT_INSTANCE_ID)

        if (autoSendProfileInquiry && (msg.ciCategorySupported.toInt() and MidiCISupportedCategories.PROFILE_CONFIGURATION.toInt()) != 0)
            requestProfiles(0x7F, msg.sourceMUID)
        if (autoSendPropertyExchangeCapabilitiesInquiry && (msg.ciCategorySupported.toInt() and MidiCISupportedCategories.PROPERTY_EXCHANGE.toInt()) != 0)
            requestPropertyExchangeCapabilities(0x7F, msg.sourceMUID, maxSimultaneousPropertyRequests)
    }
    var processDiscoveryReply: (msg: Message.DiscoveryReply) -> Unit = { msg ->
        logger.discoveryReply(msg)
        events.discoveryReplyReceived.forEach { it(msg) }
        handleNewEndpoint(msg)
    }

    private val defaultProcessInvalidateMUID = { sourceMUID: Int, muidToInvalidate: Int ->
        val conn = connections[muidToInvalidate]
        if (conn != null)
            connections.remove(muidToInvalidate)
    }
    var processInvalidateMUID = defaultProcessInvalidateMUID

    var onAck: (sourceMUID: Int, destinationMUID: Int, originalTransactionSubId: Byte, nakStatusCode: Byte, nakStatusData: Byte, nakDetailsForEachSubIdClassification: List<Byte>, messageLength: UShort, messageText: List<Byte>) -> Unit = { _,_,_,_,_,_,_,_ -> }
    var onNak: (sourceMUID: Int, destinationMUID: Int, originalTransactionSubId: Byte, nakStatusCode: Byte, nakStatusData: Byte, nakDetailsForEachSubIdClassification: List<Byte>, messageLength: UShort, messageText: List<Byte>) -> Unit = { _,_,_,_,_,_,_,_ -> }
    private fun defaultProcessAckNak(isNak: Boolean, sourceMUID: Int, destinationMUID: Int, originalTransactionSubId: Byte, statusCode: Byte, statusData: Byte, detailsForEachSubIdClassification: List<Byte>, messageLength: UShort, messageText: List<Byte>) {
        if (isNak)
            onNak(sourceMUID, destinationMUID, originalTransactionSubId, statusCode, statusData, detailsForEachSubIdClassification, messageLength, messageText)
        else
            onAck(sourceMUID, destinationMUID, originalTransactionSubId, statusCode, statusData, detailsForEachSubIdClassification, messageLength, messageText)
    }
    private val defaultProcessAck = { sourceMUID: Int, destinationMUID: Int, originalTransactionSubId: Byte, statusCode: Byte, statusData: Byte, detailsForEachSubIdClassification: List<Byte>, messageLength: UShort, messageText: List<Byte> ->
        defaultProcessAckNak(false, sourceMUID, destinationMUID, originalTransactionSubId, statusCode, statusData, detailsForEachSubIdClassification, messageLength, messageText)
    }
    var processAck = defaultProcessAck
    private val defaultProcessNak = { sourceMUID: Int, destinationMUID: Int, originalTransactionSubId: Byte, statusCode: Byte, statusData: Byte, detailsForEachSubIdClassification: List<Byte>, messageLength: UShort, messageText: List<Byte> ->
        defaultProcessAckNak(true, sourceMUID, destinationMUID, originalTransactionSubId, statusCode, statusData, detailsForEachSubIdClassification, messageLength, messageText)
    }
    var processNak = defaultProcessNak

    val defaultProcessEndpointReply = { msg: Message.EndpointReply ->
        val conn = connections[msg.sourceMUID]
        if (conn != null) {
            if (msg.status == MidiCIConstants.ENDPOINT_STATUS_PRODUCT_INSTANCE_ID)
                conn.productInstanceId = msg.data.toByteArray().decodeToString() // FIXME: verify that it is only ASCII chars?
        }
    }
    var processEndpointReply: (msg: Message.EndpointReply) -> Unit = { msg ->
        logger.endpointReply(msg)
        events.endpointReplyReceived.forEach { it(msg) }
        defaultProcessEndpointReply(msg)
    }

    // Protocol Negotiation is deprecated. We do not send any of them anymore.

    // Profile Configuration
    val defaultProcessProfileReply = { msg: Message.ProfileReply ->
        val conn = connections[msg.sourceMUID]
        msg.enabledProfiles.forEach { conn?.profiles?.add(MidiCIProfile(it, msg.address, true)) }
        msg.disabledProfiles.forEach { conn?.profiles?.add(MidiCIProfile(it, msg.address, false)) }
    }
    var processProfileReply = { msg: Message.ProfileReply ->
        logger.profileReply(msg)
        events.profileInquiryReplyReceived.forEach { it(msg) }
        defaultProcessProfileReply(msg)
    }

    val defaultProcessProfileAddedReport: (msg: Message.ProfileAdded) -> Unit = { msg ->
        val conn = connections[msg.sourceMUID]
        conn?.profiles?.add(MidiCIProfile(msg.profile, msg.address, false))
    }
    var processProfileAddedReport = { msg: Message.ProfileAdded ->
        logger.profileAdded(msg)
        events.profileAddedReceived.forEach { it(msg) }
        defaultProcessProfileAddedReport(msg)
    }

    val defaultProcessProfileRemovedReport: (msg: Message.ProfileRemoved) -> Unit = { msg ->
        val conn = connections[msg.sourceMUID]
        conn?.profiles?.remove(MidiCIProfile(msg.profile, msg.address, false))
    }
    var processProfileRemovedReport: (msg: Message.ProfileRemoved) -> Unit = { msg ->
        logger.profileRemoved(msg)
        events.profileRemovedReceived.forEach { it(msg) }
        defaultProcessProfileRemovedReport(msg)
    }

    fun defaultProcessProfileEnabledReport(msg: Message.ProfileEnabled) {
        val conn = connections[msg.sourceMUID]
        conn?.profiles?.setEnabled(true, msg.address, msg.profile, msg.numChannelsRequested)
    }
    fun defaultProcessProfileDisabledReport(msg: Message.ProfileDisabled) {
        val conn = connections[msg.sourceMUID]
        conn?.profiles?.setEnabled(false, msg.address, msg.profile, msg.numChannelsRequested)
    }
    var processProfileEnabledReport: (msg: Message.ProfileEnabled) -> Unit = { msg ->
        logger.profileEnabled(msg)
        events.profileEnabledReceived.forEach { it(msg) }
        defaultProcessProfileEnabledReport(msg)
    }
    var processProfileDisabledReport: (msg: Message.ProfileDisabled) -> Unit = { msg ->
        logger.profileDisabled(msg)
        events.profileDisabledReceived.forEach { it(msg) }
        defaultProcessProfileDisabledReport(msg)
    }

    var processProfileDetailsReply: (msg: Message.ProfileDetailsReply) -> Unit = { msg ->
        logger.profileDetailsReply(msg)
        events.profileDetailsReplyReceived.forEach { it(msg) }
        // nothing to perform - use events if you need anything further
    }

    // Property Exchange
    val defaultProcessPropertyCapabilitiesReply: (msg: Message.PropertyGetCapabilitiesReply) -> Unit = { msg ->
        val conn = connections[msg.sourceMUID]
        if (conn != null) {
            conn.maxSimultaneousPropertyRequests = msg.maxSimultaneousRequests

            // proceed to query resource list
            if (autoSendGetResourceList)
                GlobalScope.launch {
                    conn.propertyClient.requestPropertyList(msg.sourceMUID, requestIdSerial++)
                }
        }
        // FIXME: else -> error reporting
    }
    var processPropertyCapabilitiesReply: (msg: Message.PropertyGetCapabilitiesReply) -> Unit = { msg ->
        logger.propertyGetCapabilitiesReply(msg)
        events.propertyCapabilityReplyReceived.forEach { it(msg) }
        defaultProcessPropertyCapabilitiesReply(msg)
    }

    val defaultProcessGetDataReply: (msg: Message.GetPropertyDataReply) -> Unit = { msg ->
        connections[msg.sourceMUID]?.updateProperty(msg)
    }

    var processGetDataReply: (msg: Message.GetPropertyDataReply) -> Unit = { msg ->
        logger.getPropertyDataReply(msg)
        events.getPropertyDataReplyReceived.forEach { it(msg) }
        defaultProcessGetDataReply(msg)
    }

    var processSetDataReply: (msg: Message.SetPropertyDataReply) -> Unit = { msg ->
        logger.setPropertyDataReply(msg)
        events.setPropertyDataReplyReceived.forEach { it(msg) }
        // nothing to delegate further
    }

    fun sendPropertySubscribeReply(msg: Message.SubscribePropertyReply) {
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIPropertyChunks(
            dst, CISubId2.PROPERTY_SUBSCRIBE_REPLY,
            msg.sourceMUID, msg.destinationMUID, msg.requestId, msg.header, msg.body
        ).forEach {
            sendOutput(it)
        }
    }
    var processSubscribeProperty: (msg: Message.SubscribeProperty) -> Unit = { msg ->
        logger.subscribeProperty(msg)
        events.subscribePropertyReceived.forEach { it(msg) }
        val conn = connections[msg.sourceMUID]
        val reply = conn?.updateProperty(msg)
        if (reply?.second != null && !ImplementationSettings.workaroundJUCEPropertySubscriptionReplyIssue) {
            logger.subscribePropertyReply(reply.second!!)
            sendPropertySubscribeReply(reply.second!!)
        } else {
            // FIXME: it should send back NAK
        }
        // If the update was NOTIFY, then it is supposed to send Get Data request.
        if (reply?.first == MidiCISubscriptionCommand.NOTIFY)
            sendGetPropertyData(msg.sourceMUID, conn.propertyClient.getPropertyIdForHeader(msg.header))
    }

    fun defaultProcessSubscribePropertyReply(msg: Message.SubscribePropertyReply) {
        val conn = connections[msg.sourceMUID]
        if (conn != null) {
            if (conn.propertyClient.getReplyStatusFor(msg.header) == PropertyExchangeStatus.OK) {
                val propertyId = conn.removePendingSubscription(msg.requestId)
                if (propertyId != null)
                    conn.propertyClient.processPropertySubscriptionResult(propertyId, msg)
                // FIXME: log error otherwise
            }
        }
    }
    var processSubscribePropertyReply: (msg: Message.SubscribePropertyReply) -> Unit = { msg ->
        logger.subscribePropertyReply(msg)
        events.subscribePropertyReplyReceived.forEach { it(msg) }
        defaultProcessSubscribePropertyReply(msg)
    }

    // Process Inquiry
    var processProcessInquiryReply: (msg: Message.ProcessInquiryReply) -> Unit = { msg ->
        logger.processInquiryReply(msg)
        events.processInquiryReplyReceived.forEach { it(msg) }
        // FIXME: implement the rest of event handler
    }

    var processMidiMessageReportReply: (msg: Message.ProcessMidiMessageReportReply) -> Unit = { msg ->
        logger.midiMessageReportReply(msg)
        events.midiMessageReportReplyReceived.forEach { it(msg) }
        // FIXME: implement the rest of event handler
    }

    var processEndOfMidiMessageReport: (msg: Message.ProcessEndOfMidiMessageReport) -> Unit = { msg ->
        logger.endOfMidiMessageReport(msg)
        events.endOfMidiMessageReportReceived.forEach { it(msg) }
        // FIXME: implement the rest of event handler
    }

    // Miscellaneous messages
    fun sendNakForUnknownCIMessage(data: List<Byte>) {
        val source = data[1]
        val originalSubId = data[3]
        val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
        val nak = MidiCIAckNakData(source, muid, sourceMUID, originalSubId,
            CINakStatus.MessageNotSupported, 0, listOf(), listOf())
        val dst = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIAckNak(dst, true, nak))
    }
    var processUnknownCIMessage: (data: List<Byte>) -> Unit = { data ->
        logger.nak(data)
        events.unknownMessageReceived.forEach { it(data) }
        sendNakForUnknownCIMessage(data)
    }


    fun processInput(data: List<Byte>) {
        if (data[0] != 0x7E.toByte() || data[2] != 0xD.toByte())
            return // not MIDI-CI sysex

        val destinationMUID = CIRetrieval.midiCIGetDestinationMUID(data)
        if (destinationMUID != muid && destinationMUID != MidiCIConstants.BROADCAST_MUID_32)
            return // we are not the target

        when (data[3]) {
            // Protocol Negotiation - we ignore them. Falls back to NAK

            // Discovery
            CISubId2.DISCOVERY_INQUIRY -> {
                // If we send back NAK, then it may result in infinite loop like InvalidateMUID -> new Discovery
            }
            CISubId2.DISCOVERY_REPLY -> {
                val ciSupported = data[24]
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val device = CIRetrieval.midiCIGetDeviceDetails(data)
                val max = CIRetrieval.midiCIMaxSysExSize(data)
                // only available in MIDI-CI 1.2 or later.
                val initiatorOutputPath = if (data.size > 29) data[29] else 0
                val functionBlock = if (data.size > 30) data[30] else 0
                // Reply to Discovery
                processDiscoveryReply(Message.DiscoveryReply(
                    sourceMUID, destinationMUID, device, ciSupported, max, initiatorOutputPath, functionBlock))
            }
            CISubId2.ENDPOINT_MESSAGE_REPLY -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val status = data[13]
                val dataLength = data[14] + (data[15].toInt() shl 7)
                val dataValue = data.drop(16).take(dataLength)
                processEndpointReply(Message.EndpointReply(sourceMUID, destinationMUID, status, dataValue))
            }
            CISubId2.INVALIDATE_MUID -> {
                processInvalidateMUID(
                    CIRetrieval.midiCIGetSourceMUID(data),
                    CIRetrieval.midiCIGetMUIDToInvalidate(data)
                    )
            }
            CISubId2.ACK -> {
                // ACK MIDI-CI
                processAck(
                    CIRetrieval.midiCIGetSourceMUID(data),
                    CIRetrieval.midiCIGetDestinationMUID(data),
                    data[13],
                    data[14],
                    data[15],
                    data.drop(16).take(5),
                    (data[21] + (data[22].toInt() shl 7)).toUShort(),
                    data.drop(23)
                )
            }
            CISubId2.NAK -> {
                // NAK MIDI-CI
                processNak(
                    CIRetrieval.midiCIGetSourceMUID(data),
                    CIRetrieval.midiCIGetDestinationMUID(data),
                    data[13],
                    data[14],
                    data[15],
                    data.drop(16).take(5),
                    (data[21] + (data[22].toInt() shl 7)).toUShort(),
                    data.drop(23)
                )
            }

            // Profile Configuration
            CISubId2.PROFILE_INQUIRY_REPLY -> {
                val address = CIRetrieval.midiCIGetAddressing(data)
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val profiles = CIRetrieval.midiCIGetProfileSet(data)
                processProfileReply(Message.ProfileReply(
                    address,
                    sourceMUID,
                    destinationMUID,
                    profiles.filter { it.second }.map { it.first },
                    profiles.filter { !it.second }.map { it.first })
                )
            }
            CISubId2.PROFILE_ADDED_REPORT -> {
                val address = CIRetrieval.midiCIGetAddressing(data)
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val profile = CIRetrieval.midiCIGetProfileId(data)
                processProfileAddedReport(Message.ProfileAdded(address, sourceMUID, profile))
            }
            CISubId2.PROFILE_REMOVED_REPORT -> {
                val address = CIRetrieval.midiCIGetAddressing(data)
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val profile = CIRetrieval.midiCIGetProfileId(data)
                processProfileRemovedReport(Message.ProfileRemoved(address, sourceMUID, profile))
            }
            CISubId2.PROFILE_DETAILS_REPLY -> {
                val address = CIRetrieval.midiCIGetAddressing(data)
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val profile = CIRetrieval.midiCIGetProfileId(data)
                val target = data[18]
                val dataSize = data[19] + (data[20] shl 7)
                val details = data.drop(21).take(dataSize)
                processProfileDetailsReply(Message.ProfileDetailsReply(address, sourceMUID, destinationMUID, profile, target, details))
            }

            CISubId2.PROFILE_ENABLED_REPORT -> {
                val address = CIRetrieval.midiCIGetAddressing(data)
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val profile = CIRetrieval.midiCIGetProfileId(data)
                val channels = (data[18] + (data[19] shl 7)).toShort()
                processProfileEnabledReport(Message.ProfileEnabled(address, sourceMUID, profile, channels))
            }
            CISubId2.PROFILE_DISABLED_REPORT -> {
                val address = CIRetrieval.midiCIGetAddressing(data)
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val profile = CIRetrieval.midiCIGetProfileId(data)
                val channels = (data[18] + (data[19] shl 7)).toShort()
                processProfileDisabledReport(Message.ProfileDisabled(address, sourceMUID, profile, channels))
            }

            // Property Exchange
            CISubId2.PROPERTY_CAPABILITIES_REPLY -> {
                processPropertyCapabilitiesReply(Message.PropertyGetCapabilitiesReply(
                    CIRetrieval.midiCIGetAddressing(data),
                    CIRetrieval.midiCIGetSourceMUID(data),
                    destinationMUID,
                    CIRetrieval.midiCIGetMaxPropertyRequests(data))
                )
            }
            CISubId2.PROPERTY_GET_DATA_REPLY -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val header = CIRetrieval.midiCIGetPropertyHeader(data)
                // FIXME: handle chunked body content
                val requestId = data[13]
                val numChunks = CIRetrieval.midiCIGetPropertyTotalChunks(data)
                val chunkIndex = CIRetrieval.midiCIGetPropertyChunkIndex(data)
                val body = CIRetrieval.midiCIGetPropertyBodyInThisChunk(data)
                processGetDataReply(Message.GetPropertyDataReply(
                    sourceMUID, destinationMUID, requestId, header, numChunks, chunkIndex, body))
            }
            CISubId2.PROPERTY_SET_DATA_REPLY -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val header = CIRetrieval.midiCIGetPropertyHeader(data)
                val requestId = data[13]
                processSetDataReply(Message.SetPropertyDataReply(
                    sourceMUID, destinationMUID, requestId, header))
            }
            CISubId2.PROPERTY_SUBSCRIBE -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val requestId = data[13]
                val headerSize = data[14] + (data[15].toInt() shl 7)
                val header = data.drop(16).take(headerSize)
                processSubscribeProperty(Message.SubscribeProperty(sourceMUID, destinationMUID, requestId, header, listOf()))
            }
            CISubId2.PROPERTY_SUBSCRIBE_REPLY -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val requestId = data[13]
                val headerSize = data[14] + (data[15].toInt() shl 7)
                val header = data.drop(16).take(headerSize)
                processSubscribePropertyReply(Message.SubscribePropertyReply(sourceMUID, destinationMUID, requestId, header, listOf()))
            }

            // Process Inquiry
            CISubId2.PROCESS_INQUIRY_CAPABILITIES_REPLY -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val supportedFeatures = data[13]
                processProcessInquiryReply(Message.ProcessInquiryReply(sourceMUID, destinationMUID, supportedFeatures))
            }
            CISubId2.PROCESS_INQUIRY_MIDI_MESSAGE_REPORT_REPLY -> {
                val address = CIRetrieval.midiCIGetAddressing(data)
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val messageDataControl = data[13]
                val systemMessages = data[14]
                val channelControllerMessages = data[16]
                val noteDataMessages = data[17]
                processMidiMessageReportReply(Message.ProcessMidiMessageReportReply(
                    address, sourceMUID, destinationMUID,
                    messageDataControl, systemMessages, channelControllerMessages, noteDataMessages))
            }
            CISubId2.PROCESS_INQUIRY_END_OF_MIDI_MESSAGE -> {
                val address = CIRetrieval.midiCIGetAddressing(data)
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                processEndOfMidiMessageReport(Message.ProcessEndOfMidiMessageReport(address, sourceMUID, destinationMUID))
            }

            else -> {
                processUnknownCIMessage(data)
            }
        }
    }
}