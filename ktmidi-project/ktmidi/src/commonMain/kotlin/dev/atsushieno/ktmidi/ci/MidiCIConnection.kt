package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.MidiCIProtocolType
import dev.atsushieno.ktmidi.MidiCIProtocolValue
import dev.atsushieno.ktmidi.MidiInput
import dev.atsushieno.ktmidi.MidiOutput
import dev.atsushieno.ktmidi.OnMidiReceivedEventListener
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.time.compareTo

data class DeviceDetails(val manufacturer: Int = 0, val family: Short = 0, val familyModelNumber: Short = 0, val softwareRevisionLevel: Int = 0)

enum class MidiCIInitiatorState {
    Initial,
    DISCOVERY_SENT,
    DISCOVERED,
    NEW_PROTOCOL_SENT, // almost no chance to stay at the state
    TEST_SENT,
    ESTABLISHED
}

enum class MidiCIDiscoveryResponseCode {
    Reply,
    InvalidateMUID,
    NAK
}

// It is used to determine default authority level
object MidiCIAuthorityLevelBasis {
    const val NodeServer: Byte = 0x60 // to 0x6F
    const val Gateway: Byte = 0x50 // to 0x5F
    const val Translator: Byte = 0x40 // to 0x4F
    const val Endpoint: Byte = 0x30 // to 0x3F
    const val EventProcessor: Byte = 0x20 // to 0x2F
    const val Transport: Byte = 0x10 // to 0x1F
}

object MidiCISystem {
    @OptIn(ExperimentalTime::class)
    var timeSource: TimeSource = TimeSource.Monotonic
}

object MidiCIConstants {
    val CI_VERSION_AND_FORMAT: Byte = 0x1

    val RECEIVABLE_MAX_SYSEX_SIZE = 4096

    val RESPONDER_CI_CATEGORY_SUPPORTED: Byte = 0x7F

    val DEVICE_ID_MIDI_PORT: Byte = 0x7F

    val Midi1ProtocolTypeInfo = MidiCIProtocolTypeInfo(MidiCIProtocolType
        .MIDI1.toByte(), MidiCIProtocolValue.MIDI1.toByte(), 0, 0, 0)
    val Midi2ProtocolTypeInfo = MidiCIProtocolTypeInfo(MidiCIProtocolType
        .MIDI2.toByte(), MidiCIProtocolValue.MIDI2_V1.toByte(), 0, 0, 0)
    // The list is ordered from most preferred protocol type info, as per MIDI-CI spec. section 6.5 describes.
    val Midi2ThenMidi1Protocols = listOf(Midi1ProtocolTypeInfo, Midi2ProtocolTypeInfo)
    val Midi1ThenMidi2Protocols = listOf(Midi1ProtocolTypeInfo, Midi2ProtocolTypeInfo)
}

/*

    Typical MIDI-CI processing flow

    - MidiCIInitiator.sendDiscovery()
    - MidiCIResponder.processDiscovery()
    - MidiCIInitiator.initiateProtocolNegotiation()
    - MidiCIResponder.processNegotiationInquiry()
    - MidiCIInitiator.setNewProtocol() - no matching reply from responder
    - MidiCIInitiator.testNewProtocol()
    - MidiCIInitiator.confirmProtocol()
 */

class MidiCIInitiator(private val input: MidiInput, private val output: MidiOutput, val device: DeviceDetails,
                      val authorityLevel: Byte = MidiCIAuthorityLevelBasis.NodeServer,
                      val muid: Int = Random.nextInt()) {

    var state: MidiCIInitiatorState = MidiCIInitiatorState.Initial

    var midiProtocol: Int = MidiCIProtocolType.MIDI1
    var protocolTested: Boolean = false

    private var latestDiscoveryResponseCode: MidiCIDiscoveryResponseCode? = null
    private var discoveredDevice: DeviceDetails? = null
    private var protocolTestData: List<Byte>? = null

    var preferredProtocols = MidiCIConstants.Midi2ThenMidi1Protocols

    private val defaultProcessDiscoveryResponse = { responseCode: MidiCIDiscoveryResponseCode, device: DeviceDetails, sourceMUID: Int ->
        latestDiscoveryResponseCode = responseCode
        state = if (responseCode == MidiCIDiscoveryResponseCode.Reply) MidiCIInitiatorState.DISCOVERED else MidiCIInitiatorState.Initial

        // If successfully discovered, continue to protocol promotion to MIDI 2.0
        if (responseCode == MidiCIDiscoveryResponseCode.Reply) {
            discoveredDevice = device
            initiateProtocolNegotiation(sourceMUID, authorityLevel, preferredProtocols)
        }
    }
    var processDiscoveryResponse = defaultProcessDiscoveryResponse

    private val defaultProcessReplyToInitiateProtocolNegotiation = { supportedProtocols: List<MidiCIProtocolTypeInfo>, sourceMUID: Int ->
        val protocol = preferredProtocols.firstOrNull { i -> supportedProtocols.any { i.type == it.type && i.version == it.version }}
        if (protocol != null) {
            setNewProtocol(sourceMUID, authorityLevel, protocol)
            state = MidiCIInitiatorState.NEW_PROTOCOL_SENT
            protocolTestData = (0 until 48).map { it.toByte()}
            testNewProtocol(sourceMUID, authorityLevel, protocolTestData!!)
            state = MidiCIInitiatorState.TEST_SENT
        }
    }
    var processReplyToInitiateProtocolNegotiation = defaultProcessReplyToInitiateProtocolNegotiation

    private val defaultProcessTestProtocolReply = { sourceMUID: Int, testData: List<Byte> ->
        if (testData.toByteArray() contentEquals protocolTestData?.toByteArray()) {
            confirmProtocol(sourceMUID, authorityLevel)
            state = MidiCIInitiatorState.ESTABLISHED
        }
    }
    var processTestProtocolReply = defaultProcessTestProtocolReply

    fun sendDiscovery(ciCategorySupported: Byte) {
        val buf = mutableListOf<Byte>()
        CIFactory.midiCIDiscovery(buf, MidiCIConstants.CI_VERSION_AND_FORMAT, muid, device.manufacturer, device.family, device.familyModelNumber,
            device.softwareRevisionLevel, ciCategorySupported, MidiCIConstants.RECEIVABLE_MAX_SYSEX_SIZE)
        output.send(buf.toByteArray(), 0, buf.size, 0)
        state = MidiCIInitiatorState.DISCOVERY_SENT
    }

    fun initiateProtocolNegotiation(destinationMUID: Int, authorityLevel: Byte, protocolTypes: List<MidiCIProtocolTypeInfo>) {
        val buf = mutableListOf<Byte>()
        CIFactory.midiCIProtocolNegotiation(buf, false, muid, destinationMUID, authorityLevel, protocolTypes)
        output.send(buf.toByteArray(), 0, buf.size, 0)
    }

    fun setNewProtocol(destinationMUID: Int, authorityLevel: Byte, newProtocolType: MidiCIProtocolTypeInfo) {
        val buf = mutableListOf<Byte>()
        CIFactory.midiCIProtocolSet(buf, muid, destinationMUID, authorityLevel, newProtocolType)
        output.send(buf.toByteArray(), 0, buf.size, 0)
    }

    fun testNewProtocol(destinationMUID: Int, authorityLevel: Byte, testData48Bytes: List<Byte>) {
        val buf = mutableListOf<Byte>()
        CIFactory.midiCIProtocolTest(buf, true, muid, destinationMUID, authorityLevel, testData48Bytes)
        output.send(buf.toByteArray(), 0, buf.size, 0)
    }

    fun confirmProtocol(destinationMUID: Int, authorityLevel: Byte) {
        val buf = mutableListOf<Byte>()
        CIFactory.midiCIProtocolConfirmEstablished(buf, muid, destinationMUID, authorityLevel)
        output.send(buf.toByteArray(), 0, buf.size, 0)
    }

    private val inputListener = object: OnMidiReceivedEventListener {
        override fun onEventReceived(data: ByteArray, start: Int, length: Int, timestampInNanoseconds: Long) {
            if (start + 4 < data.size)
                return // invalid input
            if (data.drop(start).toByteArray() contentEquals byteArrayOf(0xF0.toByte(), 0x7E, 0x7F, 0xD)) {
                when (data[start + 4]) {
                    CIFactory.SUB_ID_2_PROTOCOL_NEGOTIATION_REPLY -> {
                        // Reply to Initiate Protocol Negotiation
                        processReplyToInitiateProtocolNegotiation(
                            CIRetrieval.midiCIGetSupportedProtocols(data, start),
                            CIRetrieval.midiCIGetSourceMUID(data, start))
                    }
                    CIFactory.SUB_ID_2_TEST_NEW_PROTOCOL_R2I -> {
                        // Test New Protocol Responder to Initiator
                        processTestProtocolReply(
                            CIRetrieval.midiCIGetSourceMUID(data, start),
                            CIRetrieval.midiCIGetTestData(data, start))
                    }
                    CIFactory.SUB_ID_2_DISCOVERY_REPLY -> {
                        // Reply to Discovery
                        processDiscoveryResponse(MidiCIDiscoveryResponseCode.Reply,
                            CIRetrieval.midiCIGetDeviceDetails(data, start),
                            CIRetrieval.midiCIGetSourceMUID(data, start))
                    }
                    CIFactory.SUB_ID_2_INVALIDATE_MUID -> {
                        // Invalid MUID
                        processDiscoveryResponse(MidiCIDiscoveryResponseCode.InvalidateMUID,
                            CIRetrieval.midiCIGetDeviceDetails(data, start),
                            CIRetrieval.midiCIGetSourceMUID(data, start))
                    }
                    CIFactory.SUB_ID_2_NAK -> {
                        // NAK MIDI-CI
                        processDiscoveryResponse(MidiCIDiscoveryResponseCode.NAK,
                            CIRetrieval.midiCIGetDeviceDetails(data, start),
                            CIRetrieval.midiCIGetSourceMUID(data, start))
                    }
                    CIFactory.SUB_ID_2_PROPERTY_CAPABILITIES_REPLY -> {
                        // Reply to Property Exchange Capabilities
                    }
                    CIFactory.SUB_ID_2_PROPERTY_HAS_DATA_REPLY -> {
                        // Reply to Property Exchange Capabilities
                    }
                    CIFactory.SUB_ID_2_PROPERTY_GET_DATA_REPLY -> {
                        // Reply to Property Exchange Capabilities
                    }
                    CIFactory.SUB_ID_2_PROPERTY_SET_DATA_REPLY -> {
                        // Reply to Property Exchange Capabilities
                    }
                    CIFactory.SUB_ID_2_PROPERTY_SUBSCRIBE_REPLY -> {
                        // Reply to Property Exchange Capabilities
                    }
                }
            }
        }
    }

    init {
        input.setMessageReceivedListener(inputListener)
    }
}

class MidiCIResponder(private val input: MidiInput, private val output: MidiOutput, private val device: DeviceDetails,
                      private val authorityLevel: Byte = MidiCIAuthorityLevelBasis.NodeServer,
                      private val muid: Int = Random.nextInt()) {

    var currentMidiProtocol = MidiCIConstants.Midi1ProtocolTypeInfo

    private var defaultProcessDiscovery: (deviceDetails: DeviceDetails, initiatorMUID: Int) -> MidiCIDiscoveryResponseCode =
        { _, _ -> MidiCIDiscoveryResponseCode.Reply }
    var processDiscovery = defaultProcessDiscovery

    var supportedProtocols: List<MidiCIProtocolTypeInfo> = MidiCIConstants.Midi2ThenMidi1Protocols

    private var defaultProcessNegotiationInquiry: (supportedProtocols: List<MidiCIProtocolTypeInfo>, initiatorMUID: Int) -> List<MidiCIProtocolTypeInfo> =
        // we don't listen to initiator :p
        { _, _ -> supportedProtocols }
    var processNegotiationInquiry = defaultProcessNegotiationInquiry

    private var defaultProcessSetNewProtocol: (newProtocol: MidiCIProtocolTypeInfo, initiatorMUID: Int) -> Boolean = { newProtocol, initiateMUID ->
        supportedProtocols.any { newProtocol.type == it.type && newProtocol.version == it.version} }
    var processSetNewProtocol = defaultProcessSetNewProtocol

    @OptIn(ExperimentalTime::class)
    private var defaultProcessTestNewProtocol: (testData: List<Byte>, initiatorMUID: Int) -> Boolean =
        { _, _ -> protocolTestTimeout != null && MidiCISystem.timeSource.markNow().elapsedNow() < protocolTestTimeout!!.elapsedNow() }
    var processTestNewProtocol = defaultProcessTestNewProtocol

    @OptIn(ExperimentalTime::class)
    private var protocolTestTimeout: TimeMark? = null

    @OptIn(ExperimentalTime::class)
    private val inputListener = object: OnMidiReceivedEventListener {
        override fun onEventReceived(data: ByteArray, start: Int, length: Int, timestampInNanoseconds: Long) {
            if (start + 4 < data.size)
                return // invalid input
            if (data.drop(start).toByteArray() contentEquals byteArrayOf(0xF0.toByte(), 0x7E, 0x7F, 0xD)) {
                when (data[start + 4]) {
                    CIFactory.SUB_ID_2_DISCOVERY_INQUIRY -> {
                        val device = CIRetrieval.midiCIGetDeviceDetails(data, start)
                        val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data, start)
                        val dst = mutableListOf<Byte>()
                        when (processDiscovery(device, sourceMUID)) {
                            MidiCIDiscoveryResponseCode.Reply -> CIFactory.midiCIDiscoveryReply(
                                dst, MidiCIConstants.CI_VERSION_AND_FORMAT, muid, sourceMUID,
                                device.manufacturer, device.family, device.familyModelNumber, device.softwareRevisionLevel,
                                MidiCIConstants.RESPONDER_CI_CATEGORY_SUPPORTED, MidiCIConstants.RECEIVABLE_MAX_SYSEX_SIZE)
                            MidiCIDiscoveryResponseCode.InvalidateMUID -> CIFactory.midiCIDiscoveryInvalidateMuid(
                                dst, MidiCIConstants.CI_VERSION_AND_FORMAT, muid, sourceMUID)
                            MidiCIDiscoveryResponseCode.NAK -> CIFactory.midiCIDiscoveryNak(
                                dst, MidiCIConstants.DEVICE_ID_MIDI_PORT, MidiCIConstants.CI_VERSION_AND_FORMAT, muid, sourceMUID)
                        }
                        output.send(dst.toByteArray(), 0, dst.size, 0)
                    }
                    CIFactory.SUB_ID_2_PROTOCOL_NEGOTIATION_INQUIRY -> {
                        val requestedProtocols = CIRetrieval.midiCIGetSupportedProtocols(data, start)
                        val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data, start)
                        val dst = mutableListOf<Byte>()
                        CIFactory.midiCIProtocolNegotiation(dst, true, muid, sourceMUID, authorityLevel,
                            processNegotiationInquiry(requestedProtocols, sourceMUID))
                        output.send(dst.toByteArray(), 0, dst.size, 0)
                    }
                    CIFactory.SUB_ID_2_SET_NEW_PROTOCOL -> {
                        val requestedProtocol = CIRetrieval.midiCIGetNewProtocol(data, start)
                        val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data, start)

                        if (processSetNewProtocol(requestedProtocol, sourceMUID)) {
                            currentMidiProtocol = requestedProtocol
                            protocolTestTimeout = MidiCISystem.timeSource.markNow().plus(300.milliseconds)
                        }
                    }
                    CIFactory.SUB_ID_2_TEST_NEW_PROTOCOL_I2R -> {
                        val sourceMUID = CIRetrieval.midiCIGetSourceMUID(data, start)
                        val testData = CIRetrieval.midiCIGetTestData(data, start)
                        if (processTestNewProtocol(testData, sourceMUID)) {
                            val dst = mutableListOf<Byte>()
                            CIFactory.midiCIProtocolTest(dst, false, muid, sourceMUID, authorityLevel, testData)
                            output.send(dst.toByteArray(), 0, dst.size, 0)
                        }
                        protocolTestTimeout = null
                    }
                    CIFactory.SUB_ID_2_CONFIRM_NEW_PROTOCOL_ESTABLISHED -> {
                        // New Protocol Established
                    }
                }
            }
        }
    }

    init {
        input.setMessageReceivedListener(inputListener)
    }
}