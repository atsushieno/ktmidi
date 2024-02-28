package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.profilecommonrules.CommonRulesProfileService

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
    var requestIdSerial: Byte = 1

    val initiator by lazy { PropertyExchangeInitiator(this, config) }
    val responder by lazy { PropertyExchangeResponder(this, config) }

    val device: MidiCIDeviceInfo
        get() = config.device

    val unknownMessageReceived = mutableListOf<(data: List<Byte>) -> Unit>()
    val messageReceived = mutableListOf<(msg: Message) -> Unit>()

    val logger = Logger()

    val connections = mutableMapOf<Int, ClientConnection>()
    val connectionsChanged = mutableListOf<(change: ConnectionChange, connection: ClientConnection) -> Unit>()

    internal var profileService: MidiCIProfileRules = CommonRulesProfileService()
    val localProfiles = ObservableProfileList(config.localProfiles)

    // These events are invoked when it received Set Profile On/Off request from Initiator.
    val onProfileSet = mutableListOf<(profile: MidiCIProfile) -> Unit>()

    var midiMessageReporter: MidiMessageReporter = object : MidiMessageReporter {
        // it is a stub implementation object

        override val midiTransportProtocol = MidiMessageReportProtocol.Midi1Stream

        // this does nothing
        override fun reportMidiMessages(
            groupAddress: Byte,
            channelAddress: Byte,
            messageDataControl: Byte,
            midiMessageReportSystemMessages: Byte,
            midiMessageReportChannelControllerMessages: Byte,
            midiMessageReportNoteDataMessages: Byte
        ): Sequence<List<Byte>> = sequenceOf()
    }

    // Messenger (implementation details for normal MIDI-CI app developers)
    internal val messenger = Messenger(this, sendCIOutput, sendMidiMessageReport)

    fun processInput(group: Byte, data: List<Byte>) = messenger.processInput(group, data)

    fun sendDiscovery(group: Byte) = messenger.sendDiscovery(group)

    fun addLocalProfile(group: Byte, profile: MidiCIProfile) {
        localProfiles.add(profile)
        messenger.sendProfileAddedReport(group, profile)
    }

    fun removeLocalProfile(group: Byte, address: Byte, profileId: MidiCIProfileId) {
        // create a dummy entry...
        val profile = MidiCIProfile(profileId, group, address, false, 0)
        localProfiles.remove(profile)
        messenger.sendProfileRemovedReport(group, profile)
    }

    fun updateLocalProfileTarget(profileId: MidiCIProfileId, oldAddress: Byte, newAddress: Byte, enabled: Boolean, numChannelsRequested: Short) {
        val profile = localProfiles.profiles.first { it.address == oldAddress && it.profile == profileId }
        localProfiles.update(profile, enabled, newAddress, numChannelsRequested)
    }

    fun requestProfileDetails(group: Byte, address: Byte, muid: Int, profile: MidiCIProfileId, target: Byte) =
        messenger.sendProfileDetailsInquiry(group, address, muid, profile, target)

    fun requestMidiMessageReport(group: Byte, address: Byte, targetMUID: Int, messageDataControl: Byte, systemMessages: Byte, channelControllerMessages: Byte, noteDataMessages: Byte) =
        messenger.sendMidiMessageReportInquiry(group, address, targetMUID, messageDataControl, systemMessages, channelControllerMessages, noteDataMessages)
}

