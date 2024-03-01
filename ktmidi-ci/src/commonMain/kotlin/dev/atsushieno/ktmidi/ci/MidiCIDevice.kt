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
    val device: MidiCIDeviceInfo
        get() = config.deviceInfo

    val unknownCIMessageReceived = mutableListOf<(data: List<Byte>) -> Unit>()
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

    // Messenger
    // FIXME: expose this implementation details once we defined good API for normal MIDI-CI app developers
    internal val messenger = Messenger(this, sendCIOutput, sendMidiMessageReport)

    fun processInput(group: Byte, data: List<Byte>) = messenger.processInput(group, data)

    fun sendDiscovery() = messenger.sendDiscovery()

    fun addLocalProfile(profile: MidiCIProfile) {
        localProfiles.add(profile)
        messenger.sendProfileAddedReport(profile)
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

    fun requestProfileDetails(address: Byte, muid: Int, profile: MidiCIProfileId, target: Byte) =
        messenger.sendProfileDetailsInquiry(address, muid, profile, target)

    fun requestMidiMessageReport(address: Byte, targetMUID: Int, messageDataControl: Byte, systemMessages: Byte, channelControllerMessages: Byte, noteDataMessages: Byte) =
        messenger.sendMidiMessageReportInquiry(address, targetMUID, messageDataControl, systemMessages, channelControllerMessages, noteDataMessages)

    val localProperties by messenger.responder::properties
    val localPropertyMetadataList
        get() = messenger.responder.propertyService.getMetadataList()
    fun addLocalProperty(property: PropertyMetadata) = messenger.responder.properties.addMetadata(property)
    fun removeLocalProperty(propertyId: String) = messenger.responder.properties.removeMetadata(propertyId)
    fun updatePropertyMetadata(oldPropertyId: String, property: PropertyMetadata) = messenger.responder.properties.updateMetadata(oldPropertyId, property)
    fun updatePropertyValue(propertyId: String, data: List<Byte>, isPartial: Boolean) = messenger.responder.updatePropertyValue(propertyId, data, isPartial)

    private fun conn(destinationMUID: Int): ClientConnection =
        connections[destinationMUID] ?: throw MidiCIException("Unknown destination MUID: $destinationMUID")
    fun sendGetPropertyDataRequest(destinationMUID: Int, resource: String, encoding: String?, paginateOffset: Int?, paginateLimit: Int?) =
        conn(destinationMUID).saveAndSendGetPropertyData(destinationMUID, resource, encoding, paginateOffset, paginateLimit)
    fun sendSetPropertyDataRequest(destinationMUID: Int, resource: String, data: List<Byte>, encoding: String?, isPartial: Boolean) =
        conn(destinationMUID).sendSetPropertyData(destinationMUID, resource, data, encoding, isPartial)
    fun sendSubscribeProperty(destinationMUID: Int, resource: String, mutualEncoding: String?) =
        conn(destinationMUID).sendSubscribeProperty(destinationMUID, resource, mutualEncoding)
    fun sendUnsubscribeProperty(destinationMUID: Int, resource: String, mutualEncoding: String?) =
        conn(destinationMUID).sendUnsubscribeProperty(destinationMUID, resource, mutualEncoding)


    fun updateDeviceInfo(deviceInfo: MidiCIDeviceInfo) {
        messenger.responder.propertyService.deviceInfo = deviceInfo
    }
}

