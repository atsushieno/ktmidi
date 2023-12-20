package dev.atsushieno.ktmidi.ci

import kotlin.random.Random

/*
    Typical MIDI-CI processing flow

    - MidiCIInitiator.sendDiscovery()
    - MidiCIResponder.processDiscovery()
    - MidiCIInitiator.requestProfiles()
    - MidiCIResponder.processProfileInquiry()

 These classes are responsible only for one input/output connection pair.
 The argument `sendOutput` takes a sysex bytestream which is NOT specific to MIDI 1.0 bytestream (i.e. it should
 support sysex7 UMPs) and thus does NOT contain F0 and F7.
 Same goes for `processInput()` function.

*/
class MidiCIInitiator(val device: MidiCIDeviceInfo,
                      private val sendOutput: (data: List<Byte>) -> Unit,
                      val outputPathId: Byte = 0,
                      val muid: Int = Random.nextInt() and 0x7F7F7F7F) {
    var midiCIBufferSize = 4096
    var receivableMaxSysExSize = MidiCIConstants.DEFAULT_RECEIVABLE_MAX_SYSEX_SIZE
    var productInstanceId: String? = null

    var state: MidiCIInitiatorState = MidiCIInitiatorState.Initial

    private var discoveredDevice: DeviceDetails? = null

    val profiles = mutableListOf<MidiCIProfileId>()

    var establishedMaxSimultaneousPropertyRequests: Byte? = null

    // Input handlers

    private val defaultProcessDiscoveryResponse = { device: DeviceDetails, sourceMUID: Int, destinationMUID: Int ->
        if (destinationMUID == muid) {
            state = MidiCIInitiatorState.DISCOVERED

            // If successfully discovered, continue to endpoint inquiry
            discoveredDevice = device
            sendEndpointMessage(sourceMUID, MidiCIConstants.ENDPOINT_STATUS_PRODUCT_INSTANCE_ID)

            requestProfiles(0x7F, sourceMUID)
        }
    }
    var processDiscoveryResponse = defaultProcessDiscoveryResponse

    private val defaultProcessInvalidateMUID = { sourceMUID: Int, destinationMUID: Int, muidToInvalidate: Int ->
        if (muidToInvalidate == muid) {
            state = MidiCIInitiatorState.Initial
        }
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

    private val defaultProcessEndpointMessageResponse = { sourceMUID: Int, destinationMUID: Int, status: Byte, data: List<Byte> ->
        if (status == MidiCIConstants.ENDPOINT_STATUS_PRODUCT_INSTANCE_ID)
            productInstanceId = data.toByteArray().decodeToString() // FIXME: verify that it is only ASCII chars?
    }
    var processEndpointMessageResponse = defaultProcessEndpointMessageResponse

    /*

    Protocol Negotiation is deprecated. We do not send any of those anymore.

    private val defaultProcessReplyToInitiateProtocolNegotiation = { supportedProtocols: List<MidiCIProtocolTypeInfo>, sourceMUID: Int ->
        val protocol = preferredProtocols.firstOrNull { i -> supportedProtocols.any { i.type == it.type && i.version == it.version }}
        if (protocol != null) {
            // we set state before sending the MIDI data as it may process the rest of the events synchronously through the end...
            state = MidiCIInitiatorState.NEW_PROTOCOL_SENT
            setNewProtocol(sourceMUID, authorityLevel, protocol)
            currentMidiProtocol = protocol
            protocolTestData = (0 until 48).map { it.toByte()}
            state = MidiCIInitiatorState.TEST_SENT
            testNewProtocol(sourceMUID, authorityLevel, protocolTestData!!)
        }
    }
    var processReplyToInitiateProtocolNegotiation = defaultProcessReplyToInitiateProtocolNegotiation

    private val defaultProcessTestProtocolReply = { sourceMUID: Int, testData: List<Byte> ->
        if (testData.toByteArray() contentEquals protocolTestData?.toByteArray()) {
            protocolTested = true
            state = MidiCIInitiatorState.ESTABLISHED
            confirmProtocol(sourceMUID, authorityLevel)
        }
    }
    var processTestProtocolReply = defaultProcessTestProtocolReply
    */

    var processProfileInquiryResponse: (destinationChannel: Byte, sourceMUID: Int, profileSet: List<Pair<MidiCIProfileId, Boolean>>) -> Unit = { _, _, _ ->
        // do nothing
    }

    private val defaultProcessProfileAddedReport: (profileId: MidiCIProfileId) -> Unit = { profileId -> profiles.add(profileId) }
    var processProfileAddedReport = defaultProcessProfileAddedReport

    private val defaultProcessProfileRemovedReport: (profileId: MidiCIProfileId) -> Unit = { profileId -> profiles.remove(profileId) }
    var processProfileRemovedReport = defaultProcessProfileRemovedReport

    private val defaultProcessGetMaxSimultaneousPropertyReply: (destinationChannel: Byte, sourceMUID: Int, destinationMUID: Int, max: Byte) -> Unit = { _, _, _, max ->
        establishedMaxSimultaneousPropertyRequests = max
    }
    var processGetMaxSimultaneousPropertyReply = defaultProcessGetMaxSimultaneousPropertyReply

    // Discovery

    fun sendDiscovery(ciCategorySupported: Byte = MidiCIDiscoveryCategoryFlags.ThreePs) =
        sendDiscovery(Message.DiscoveryInquiry(muid,
            device.manufacturerId, device.familyId, device.modelId, device.versionId,
            ciCategorySupported, receivableMaxSysExSize, outputPathId))
    fun sendDiscovery(msg: Message.DiscoveryInquiry) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIDiscovery(
            buf, MidiCIConstants.CI_VERSION_AND_FORMAT, msg.muid, msg.manufacturerId, msg.familyId, msg.modelId,
            msg.versionId, msg.ciCategorySupported, msg.receivableMaxSysExSize, msg.outputPathId
        ))
        // we set state before sending the MIDI data as it may process the rest of the events synchronously through the end...
        state = MidiCIInitiatorState.DISCOVERY_SENT
    }

    fun sendEndpointMessage(targetMuid: Int, status: Byte) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIEndpointMessage(buf, MidiCIConstants.CI_VERSION_AND_FORMAT, muid, targetMuid, status))
    }

    /*
    // Protocol Negotiation - we do not send them anymore.

    fun initiateProtocolNegotiation(destinationMUID: Int, authorityLevel: Byte, protocolTypes: List<MidiCIProtocolTypeInfo>) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIProtocolNegotiation(buf, false, muid, destinationMUID, authorityLevel, protocolTypes)
        sendOutput(buf)
    }

    fun setNewProtocol(destinationMUID: Int, authorityLevel: Byte, newProtocolType: MidiCIProtocolTypeInfo) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIProtocolSet(buf, muid, destinationMUID, authorityLevel, newProtocolType)
        sendOutput(buf)
    }

    fun testNewProtocol(destinationMUID: Int, authorityLevel: Byte, testData48Bytes: List<Byte>) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIProtocolTest(buf, true, muid, destinationMUID, authorityLevel, testData48Bytes)
        sendOutput(buf)
    }

    fun confirmProtocol(destinationMUID: Int, authorityLevel: Byte) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        CIFactory.midiCIProtocolConfirmEstablished(buf, muid, destinationMUID, authorityLevel)
        sendOutput(buf)
    }
    */

    // Profile Configuration

    fun requestProfiles(destinationChannelOr7F: Byte, destinationMUID: Int) {
        val buf = MutableList<Byte>(midiCIBufferSize) { 0 }
        sendOutput(CIFactory.midiCIProfileInquiry(buf, destinationChannelOr7F, muid, destinationMUID))
    }

    // Property Exchange
    // TODO: implement the rest (it's going to take long time, read the entire Common Rules for PE, support split chunks in reader and writer, and JSON serializers)

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

    // Reply handler

    fun sendNakForUnknownCIMessage(data: List<Byte>) {
        val source = data[1]
        val originalSubId = data[3]
        val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
        val destinationMUID = CIRetrieval.midiCIGetDestinationMUID(data)
        val nak = MidiCIAckNakData(source, sourceMUID, destinationMUID, originalSubId,
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
        when (data[3]) {
            /*
            // Protocol Negotiation - we ignore them
            FIXME: we should send back NAK
            CIFactory.SUB_ID_2_PROTOCOL_NEGOTIATION_REPLY -> {
                // Reply to Initiate Protocol Negotiation
                processReplyToInitiateProtocolNegotiation(
                    CIRetrieval.midiCIGetSupportedProtocols(data),
                    CIRetrieval.midiCIGetSourceMUID(data))
            }
            CIFactory.SUB_ID_2_TEST_NEW_PROTOCOL_R2I -> {
                // Test New Protocol Responder to Initiator
                processTestProtocolReply(
                    CIRetrieval.midiCIGetSourceMUID(data),
                    CIRetrieval.midiCIGetTestData(data))
            }
            */
            // Discovery
            CIFactory.SUB_ID_2_DISCOVERY_REPLY -> {
                // Reply to Discovery
                processDiscoveryResponse(
                    CIRetrieval.midiCIGetDeviceDetails(data),
                    CIRetrieval.midiCIGetSourceMUID(data),
                    CIRetrieval.midiCIGetDestinationMUID(data)
                )
            }
            CIFactory.SUB_ID_2_ENDPOINT_MESSAGE_REPLY -> {
                val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data)
                val destinationMUID = CIRetrieval.midiCIGetDestinationMUID(data)
                val status = data[13]
                val dataLength = data[14] + (data[15].toInt() shl 7)
                val dataValue = data.drop(16).take(dataLength)
                processEndpointMessageResponse(sourceMUID, destinationMUID, status, dataValue)
            }
            CIFactory.SUB_ID_2_INVALIDATE_MUID -> {
                // Invalid MUID
                processInvalidateMUID(
                    CIRetrieval.midiCIGetSourceMUID(data),
                    0x7F7F7F7F,
                    CIRetrieval.midiCIGetMUIDToInvalidate(data)
                    )
            }
            CIFactory.SUB_ID_2_ACK -> {
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
            CIFactory.SUB_ID_2_NAK -> {
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
            CIFactory.SUB_ID_2_PROFILE_INQUIRY_REPLY -> {
                processProfileInquiryResponse(
                    CIRetrieval.midiCIGetDestination(data),
                    CIRetrieval.midiCIGetSourceMUID(data),
                    CIRetrieval.midiCIGetProfileSet(data)
                )
            }
            CIFactory.SUB_ID_2_PROFILE_ADDED_REPORT -> {
                processProfileAddedReport(CIRetrieval.midiCIGetProfileId(data))
            }
            CIFactory.SUB_ID_2_PROFILE_REMOVED_REPORT -> {
                processProfileRemovedReport(CIRetrieval.midiCIGetProfileId(data))
            }
            // FIXME: support set profile on/off messages

            // Property Exchange
            CIFactory.SUB_ID_2_PROPERTY_CAPABILITIES_REPLY -> {
                processGetMaxSimultaneousPropertyReply(
                    CIRetrieval.midiCIGetDestination(data),
                    CIRetrieval.midiCIGetSourceMUID(data),
                    CIRetrieval.midiCIGetSourceMUID(data),
                    CIRetrieval.midiCIGetMaxPropertyRequests(data)
                )
            }
            CIFactory.SUB_ID_2_PROPERTY_HAS_DATA_REPLY -> {
                // Reply to Property Exchange Capabilities
                TODO("Implement")
            }
            CIFactory.SUB_ID_2_PROPERTY_GET_DATA_REPLY -> {
                // Reply to Property Exchange Capabilities
                TODO("Implement")
            }
            CIFactory.SUB_ID_2_PROPERTY_SET_DATA_REPLY -> {
                // Reply to Property Exchange Capabilities
                TODO("Implement")
            }
            CIFactory.SUB_ID_2_PROPERTY_SUBSCRIBE_REPLY -> {
                // Reply to Property Exchange Capabilities
                TODO("Implement")
            }

            else -> {
                processUnknownCIMessage(data)
            }
        }
    }
}