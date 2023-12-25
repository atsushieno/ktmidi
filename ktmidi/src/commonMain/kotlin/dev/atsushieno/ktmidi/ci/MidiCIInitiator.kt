package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.shl
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.random.Random

/*
    Typical MIDI-CI processing flow

    - MidiCIInitiator.sendDiscovery()
    - MidiCIResponder.processDiscovery()
    - MidiCIInitiator.requestProfiles()
    - MidiCIResponder.processProfileInquiry()

 The argument `sendOutput` takes a sysex bytestream which is NOT specific to MIDI 1.0 bytestream (i.e. it should
 support sysex7 UMPs) and thus does NOT contain F0 and F7.
 Same goes for `processInput()` function.

*/
class MidiCIInitiator(val device: MidiCIDeviceInfo,
                      private val sendOutput: (data: List<Byte>) -> Unit,
                      val outputPathId: Byte = 0,
                      val muid: Int = Random.nextInt() and 0x7F7F7F7F) {

    class Connection(
        private val parent: MidiCIInitiator,
        val muid: Int,
        val device: DeviceDetails,
        var maxSimultaneousPropertyRequests: Byte = 0,
        var productInstanceId: String = "",
        val propertyClient: MidiCIPropertyClient = CommonRulesPropertyClient(parent.muid) { msg -> parent.sendGetPropertyData(msg) }
    ) {
        val profiles = ObservableProfileList()

        val properties = ObservablePropertyList(propertyClient)

        private val openRequests = mutableListOf<Message.GetPropertyData>()

        fun updateProperty(msg: Message.GetPropertyDataReply) {
            val req = openRequests.firstOrNull { it.requestId == msg.requestId } ?: return
            openRequests.remove(req)
            val status = properties.getReplyStatusFor(msg.header) ?: return

            if (status == PropertyExchangeStatus.OK) {
                propertyClient.onGetPropertyDataReply(req, msg)
                properties.set(req, msg)
            }
        }

        fun addPendingRequest(msg: Message.GetPropertyData) {
            openRequests.add(msg)
        }
    }

    var midiCIBufferSize = 4096
    var receivableMaxSysExSize = MidiCIConstants.DEFAULT_RECEIVABLE_MAX_SYSEX_SIZE
    var productInstanceId: String? = null
    var maxSimultaneousPropertyRequests: Byte = 1

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

    fun setProfile(address: Byte, sourceMUID: Int, destinationMUID: Int, profileId: MidiCIProfileId, numChannelsRequested: Short, enabled: Boolean) =
        if (enabled)
            setProfileOn(Message.SetProfileOn(address, sourceMUID, destinationMUID, profileId, numChannelsRequested))
        else
            setProfileOff(Message.SetProfileOff(address, sourceMUID, destinationMUID, profileId))

    fun setProfileOn(msg: Message.SetProfileOn) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIProfileSet(buf, msg.address, true, msg.sourceMUID, msg.destinationMUID, msg.profile, msg.numChannelsRequested))
    }

    fun setProfileOff(msg: Message.SetProfileOff) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIProfileSet(buf, msg.address, false, msg.sourceMUID, msg.destinationMUID, msg.profile, 0))
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

    fun sendGetPropertyData(msg: Message.GetPropertyData) {
        val conn = connections[msg.destinationMUID]
        conn?.addPendingRequest(msg)
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIPropertyChunks(buf, CISubId2.PROPERTY_GET_DATA_INQUIRY,
            msg.sourceMUID, msg.destinationMUID, msg.requestId, msg.header, listOf()).forEach {
            sendOutput(it)
        }
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

        sendEndpointMessage(msg.sourceMUID, MidiCIConstants.ENDPOINT_STATUS_PRODUCT_INSTANCE_ID)

        if ((msg.ciCategorySupported.toInt() and MidiCISupportedCategories.PROFILE_CONFIGURATION.toInt()) != 0)
            requestProfiles(0x7F, msg.sourceMUID)
        if ((msg.ciCategorySupported.toInt() and MidiCISupportedCategories.PROPERTY_EXCHANGE.toInt()) != 0)
            requestPropertyExchangeCapabilities(0x7F, msg.sourceMUID, maxSimultaneousPropertyRequests)
    }
    var processDiscoveryReply: (msg: Message.DiscoveryReply) -> Unit = { msg ->
        handleNewEndpoint(msg)
    }

    private val defaultProcessInvalidateMUID = { sourceMUID: Int, destinationMUID: Int, muidToInvalidate: Int ->
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
    var processEndpointReply = defaultProcessEndpointReply

    // Protocol Negotiation is deprecated. We do not send any of them anymore.

    // Profile Configuration
    val defaultProcessProfileReply = { msg: Message.ProfileReply ->
        val conn = connections[msg.sourceMUID]
        msg.enabledProfiles.forEach { conn?.profiles?.add(MidiCIProfile(it, msg.address, true)) }
        msg.disabledProfiles.forEach { conn?.profiles?.add(MidiCIProfile(it, msg.address, false)) }
    }
    var processProfileReply = defaultProcessProfileReply

    val defaultProcessProfileAddedReport: (msg: Message.ProfileAdded) -> Unit = { msg ->
        val conn = connections[msg.sourceMUID]
        conn?.profiles?.add(MidiCIProfile(msg.profile, msg.address, false))
    }
    var processProfileAddedReport = defaultProcessProfileAddedReport

    val defaultProcessProfileRemovedReport: (msg: Message.ProfileRemoved) -> Unit = { msg ->
        val conn = connections[msg.sourceMUID]
        conn?.profiles?.remove(msg.profile)
    }
    var processProfileRemovedReport = defaultProcessProfileRemovedReport

    val defaultProcessProfileEnabledReport: (msg: Message.ProfileEnabled) -> Unit = { msg ->
        val conn = connections[msg.sourceMUID]
        conn?.profiles?.setEnabled(true, msg.address, msg.profile, msg.numChannelsRequested)
    }
    val defaultProcessProfileDisabledReport: (msg: Message.ProfileDisabled) -> Unit = { msg ->
        val conn = connections[msg.sourceMUID]
        conn?.profiles?.setEnabled(false, msg.address, msg.profile, msg.numChannelsRequested)
    }
    var processProfileEnabledReport = defaultProcessProfileEnabledReport
    var processProfileDisabledReport = defaultProcessProfileDisabledReport

    // Property Exchange
    val defaultProcessPropertyCapabilitiesReply: (msg: Message.PropertyGetCapabilitiesReply) -> Unit = { msg ->
        val conn = connections[msg.sourceMUID]
        if (conn != null) {
            conn.maxSimultaneousPropertyRequests = msg.maxSimultaneousRequests

            // proceed to query resource list
            GlobalScope.launch {
                conn.propertyClient.requestPropertyIds(msg.sourceMUID, requestIdSerial++)
            }
        }
        // FIXME: else -> error reporting
    }
    var processPropertyCapabilitiesReply = defaultProcessPropertyCapabilitiesReply

    val defaultProcessGetDataReply: (msg: Message.GetPropertyDataReply) -> Unit = { msg ->
        val conn = connections[msg.sourceMUID]
        if (conn != null) {
            conn.updateProperty(msg)
        }
    }

    var processGetDataReply = defaultProcessGetDataReply

    var processSetDataReply: (msg: Message.SetPropertyDataReply) -> Unit = {} // do nothing

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
                // Invalid MUID
                processInvalidateMUID(
                    CIRetrieval.midiCIGetSourceMUID(data),
                    0x7F7F7F7F,
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
            // FIXME: support set profile details reply
            // FIXME: support set profile enabled/disabled reports

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
            CISubId2.PROPERTY_SUBSCRIBE_REPLY -> {
                // Reply to Property Exchange Capabilities
                TODO("Implement")
            }

            else -> {
                processUnknownCIMessage(data)
            }
        }
    }
}