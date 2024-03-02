package dev.atsushieno.ktmidi.ci

enum class ConnectionChange {
    Added,
    Removed
}

/**
 * Represents A MIDI-CI Device entity, or a Function Block[*1].
 *
 * [*1] M2-101-UM_v1_2: MIDI-CI specification section 3.1 states:
 * "Each Function Block that supports MIDI-CI shall have a different MUID and act as a unique MIDI-CI Device"
 *
 */
class MidiCIDevice(val muid: Int, val config: MidiCIDeviceConfiguration,
                   sendCIOutput: (group: Byte, data: List<Byte>) -> Unit,
                   sendMidiMessageReport: (group: Byte, protocol: MidiMessageReportProtocol, data: List<Byte>) -> Unit
) {
    val device: MidiCIDeviceInfo
        get() = config.deviceInfo

    val unknownCIMessageReceived = mutableListOf<(data: List<Byte>) -> Unit>()
    val messageReceived = mutableListOf<(msg: Message) -> Unit>()
    val logger = Logger()

    val connections = mutableMapOf<Int, ClientConnection>()
    val connectionsChanged = mutableListOf<(change: ConnectionChange, connection: ClientConnection) -> Unit>()

    // FIXME: expose this implementation details once we defined good API for normal MIDI-CI app developers
    internal val messenger = Messenger(this, sendCIOutput, sendMidiMessageReport)

    fun processInput(group: Byte, data: List<Byte>) = messenger.processInput(group, data)

    fun sendDiscovery() = messenger.sendDiscovery()

    // Profile Configuration

    val profileHost = ProfileConfigurationHostFacade(this)

    // We usually do not need to call it explicitly, unless MidiCIDeviceConfiguration.autoSendProfileInquiry is disabled.
    fun requestProfiles(group: Byte, address: Byte, destinationMUID: Int) =
        messenger.sendProfileInquiry(group, address, destinationMUID)

    // can be used by both client and host
    fun requestProfileDetails(address: Byte, targetMUID: Int, profile: MidiCIProfileId, target: Byte) =
        messenger.sendProfileDetailsInquiry(address, targetMUID, profile, target)

    fun sendProfileSpecificData(address: Byte, targetMUID: Int, profile: MidiCIProfileId, data: List<Byte>) =
        messenger.sendProfileSpecificData(address, targetMUID, profile, data)

    // Property Exchange

    val propertyHost = PropertyExchangeHostFacade(this)

    private fun conn(destinationMUID: Int): ClientConnection =
        connections[destinationMUID] ?: throw MidiCIException("Unknown destination MUID: $destinationMUID")

    fun sendGetPropertyDataRequest(
        destinationMUID: Int,
        resource: String,
        encoding: String?,
        paginateOffset: Int?,
        paginateLimit: Int?
    ) =
        conn(destinationMUID).saveAndSendGetCommonRulesPropertyData(
            destinationMUID,
            resource,
            encoding,
            paginateOffset,
            paginateLimit
        )

    fun sendSetPropertyDataRequest(
        destinationMUID: Int,
        resource: String,
        data: List<Byte>,
        encoding: String?,
        isPartial: Boolean
    ) =
        conn(destinationMUID).sendSetPropertyData(destinationMUID, resource, data, encoding, isPartial)

    fun sendSubscribeProperty(destinationMUID: Int, resource: String, mutualEncoding: String?) =
        conn(destinationMUID).sendSubscribeProperty(destinationMUID, resource, mutualEncoding)

    fun sendUnsubscribeProperty(destinationMUID: Int, resource: String, mutualEncoding: String?) =
        conn(destinationMUID).sendUnsubscribeProperty(destinationMUID, resource, mutualEncoding)

    // Process Inquiry

    var midiMessageReporter: MidiMessageReporter = StubMidiMessageReporter()

    fun requestMidiMessageReport(address: Byte, targetMUID: Int, messageDataControl: Byte, systemMessages: Byte, channelControllerMessages: Byte, noteDataMessages: Byte) =
        messenger.sendMidiMessageReportInquiry(address, targetMUID, messageDataControl, systemMessages, channelControllerMessages, noteDataMessages)
}
