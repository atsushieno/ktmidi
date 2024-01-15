package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.ci.profilecommonrules.DefaultControlChangesProfile
import dev.atsushieno.ktmidi.citool.view.MidiCIProfileState

class CIResponderModel(private val outputSender: (ciBytes: List<Byte>) -> Unit) {
    fun processCIMessage(data: List<Byte>) {
        AppModel.log("[Responder received SYSEX] " + data.joinToString { it.toString(16) },
            MessageDirection.In)
        responder.processInput(data)
    }

    fun updateProfileTarget(profileState: MidiCIProfileState, address: Byte, enabled: Boolean, numChannelsRequested: Short) {
        val profile = responder.profiles.profiles.first { it.address == profileState.address.value && it.profile == profileState.profile }
        responder.profiles.update(profile, enabled, address, numChannelsRequested)
    }

    fun addProfile(profile: MidiCIProfile) {
        responder.profiles.add(profile)
        responder.sendProfileAddedReport(profile)
    }

    fun removeProfile(address: Byte, profileId: MidiCIProfileId) {
        // create a dummy entry...
        val profile = MidiCIProfile(profileId, address, false)
        responder.profiles.remove(profile)
        responder.sendProfileRemovedReport(profile)
    }

    fun updateProfileName(oldProfile: MidiCIProfileId, newProfile: MidiCIProfileId) {
        val removed = responder.profiles.profiles.filter { it.profile == oldProfile }
        val added = removed.map { MidiCIProfile(newProfile, it.address, it.enabled) }
        removed.forEach { removeProfile(it.address, it.profile) }
        added.forEach { addProfile(it) }
    }

    fun addProperty(property: PropertyMetadata) {
        responder.properties.addMetadata(property)
    }

    fun removeProperty(propertyId: String) {
        responder.properties.removeMetadata(propertyId)
    }

    val responder = MidiCIResponder(AppModel.muid, AppModel.recipient) { data ->
        AppModel.log("[Responder sent SYSEX] " + data.joinToString { it.toString(16) },
            MessageDirection.Out)
        outputSender(data)
    }.apply {
        // Profile
        onProfileSet.add { profile, numChannelsRequested ->
            profiles.profileEnabledChanged.forEach { it(profile, numChannelsRequested) }
        }

        // FIXME: they are dummy items that should be removed.
        profiles.add(MidiCIProfile(MidiCIProfileId(0, 1, 2, 3, 4), 0x7E, true))
        profiles.add(MidiCIProfile(MidiCIProfileId(5, 6, 7, 8, 9), 0x7F, true))
        profiles.add(MidiCIProfile(DefaultControlChangesProfile.profileIdForPartial, 0, false))
        profiles.add(MidiCIProfile(DefaultControlChangesProfile.profileIdForPartial, 4, true))
    }

    init {
        responder.logger.logEventReceived.add { msg, direction ->
            AppModel.log(msg, direction)
        }
        responder.midiMessageReporter = Midi1MessageReporter(outputSender)
    }
}
