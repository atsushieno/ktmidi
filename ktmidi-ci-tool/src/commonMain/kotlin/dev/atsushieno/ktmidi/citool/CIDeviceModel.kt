package dev.atsushieno.ktmidi.citool

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.ci.profilecommonrules.DefaultControlChangesProfile
import dev.atsushieno.ktmidi.citool.view.MidiCIProfileState

class CIDeviceModel(val parent: CIDeviceManager, val muid: Int, config: MidiCIDeviceConfiguration,
                    private val ciOutputSender: (ciBytes: List<Byte>) -> Unit,
                    private val midiMessageReportOutputSender: (bytes: List<Byte>) -> Unit) {

    // FIXME: this means we somehow ignore any group specification wherever this field is used.
    var defaultSenderGroup: Byte = 0

    // from CIInitiatorModel
    val connections = mutableStateListOf<ClientConnectionModel>()

    var receivingMidiMessageReports = false
    var lastChunkedMessageChannel: Byte = -1 // will never match at first.
    val chunkedMessages = mutableListOf<Byte>()
    val midiMessageReportModeChanged = mutableListOf<() -> Unit>()

    val device by lazy {
        MidiCIDevice(muid, config,
            sendCIOutput = { group, data ->
                parent.owner.log(
                    "[sent CI SysEx (grp:$group)] " + data.joinToString { it.toUByte().toString(16) },
                    MessageDirection.Out
                )
                ciOutputSender(data)
            },
            sendMidiMessageReport = { protocol, data ->
                parent.owner.log(
                    "[sent MIDI Message Report (protocol=$protocol)] " + data.joinToString { it.toUByte().toString(16) },
                    MessageDirection.Out
                )
                midiMessageReportOutputSender(data)
            }
        ).apply {
            // initiator
            logger.logEventReceived.add { msg, direction ->
                parent.owner.log(msg, direction)
            }

            initiator.connectionsChanged.add { change, conn ->
                val cml = this@CIDeviceModel.connections
                when (change) {
                    ConnectionChange.Added -> this@CIDeviceModel.connections.add(ClientConnectionModel(this@CIDeviceModel, conn).apply {
                        midiMessageReportModeChanged.add {
                            // if it went normal non-MIDI-Message-Report mode and has saved inputs, flush them to the logger.
                            if (!receivingMidiMessageReports && chunkedMessages.any())
                                parent.parent.logMidiMessageReportChunk(chunkedMessages)
                        }
                    })
                    ConnectionChange.Removed -> cml.remove(cml.firstOrNull { conn == it.conn })
                    else -> {}
                }
            }
            events.midiMessageReportReplyReceived.add {
                receivingMidiMessageReports = true
                midiMessageReportModeChanged.forEach { it() }
            }
            events.endOfMidiMessageReportReceived.add {
                receivingMidiMessageReports = false
                midiMessageReportModeChanged.forEach { it() }
            }

            responder.midiMessageReporter = MidiMachineMessageReporter()

            // responder
            onProfileSet.add { profile -> localProfiles.profileEnabledChanged.forEach { it(profile) } }

            // FIXME: they are dummy items that should be removed.
            localProfiles.add(MidiCIProfile(MidiCIProfileId(0, 1, 2, 3, 4), 0, 0x7E, true, 0))
            localProfiles.add(MidiCIProfile(MidiCIProfileId(5, 6, 7, 8, 9), 0, 0x7F, true, 0))
            localProfiles.add(MidiCIProfile(DefaultControlChangesProfile.profileIdForPartial, 0, 0, false, 1))
            localProfiles.add(MidiCIProfile(DefaultControlChangesProfile.profileIdForPartial, 0, 4, true, 1))
        }
    }

    val initiator = CIInitiatorModel(this)

    fun processCIMessage(group: Byte, data: List<Byte>) {
        if (data.isEmpty()) return
        parent.owner.log("[received CI SysEx (grp:$group)] " + data.joinToString { it.toUByte().toString(16) }, MessageDirection.In)
        device.processInput(group, data)
    }

    // observable state
    val localProfileStates = mutableStateListOf<MidiCIProfileState>().apply {
        addAll(device.localProfiles.profiles.map {
            MidiCIProfileState(
                mutableStateOf(it.group),
                mutableStateOf(it.address),
                it.profile,
                mutableStateOf(it.enabled),
                mutableStateOf(it.numChannelsRequested)
            )
        })
    }

    // Management message client

    fun sendDiscovery() {
        device.sendDiscovery(defaultSenderGroup)
    }

    // local profile configuration

    fun updateLocalProfileTarget(profileState: MidiCIProfileState, address: Byte, enabled: Boolean, numChannelsRequested: Short) {
        val profile = device.localProfiles.profiles.first { it.address == profileState.address.value && it.profile == profileState.profile }
        device.localProfiles.update(profile, enabled, address, numChannelsRequested)
    }

    fun addLocalProfile(profile: MidiCIProfile) {
        device.localProfiles.add(profile)
        device.sendProfileAddedReport(defaultSenderGroup, profile)
    }

    fun removeLocalProfile(group: Byte, address: Byte, profileId: MidiCIProfileId) {
        // create a dummy entry...
        val profile = MidiCIProfile(profileId, group, address, false, 0)
        device.localProfiles.remove(profile)
        device.sendProfileRemovedReport(defaultSenderGroup, profile)
    }

    fun updateLocalProfileName(oldProfile: MidiCIProfileId, newProfile: MidiCIProfileId) {
        val removed = device.localProfiles.profiles.filter { it.profile == oldProfile }
        val added = removed.map { MidiCIProfile(newProfile, it.group, it.address, it.enabled, it.numChannelsRequested) }
        removed.forEach { removeLocalProfile(it.group, it.address, it.profile) }
        added.forEach { addLocalProfile(it) }
    }

    // Local property configuration
    fun addLocalProperty(property: PropertyMetadata) {
        device.responder.properties.addMetadata(property)
    }

    fun removeLocalProperty(propertyId: String) {
        device.responder.properties.removeMetadata(propertyId)
    }

    init {
        device.localProfiles.profilesChanged.add { change, profile ->
            when (change) {
                ObservableProfileList.ProfilesChange.Added ->
                    localProfileStates.add(
                        MidiCIProfileState(
                            mutableStateOf(profile.group),
                            mutableStateOf(profile.address),
                            profile.profile,
                            mutableStateOf(profile.enabled),
                            mutableStateOf(profile.numChannelsRequested)))
                ObservableProfileList.ProfilesChange.Removed ->
                    localProfileStates.removeAll { it.profile == profile.profile && it.address.value == profile.address }
            }
        }
        device.localProfiles.profileUpdated.add { profileId: MidiCIProfileId, oldAddress: Byte, newEnabled: Boolean, newAddress: Byte, numChannelsRequested: Short ->
            val entry = localProfileStates.first { it.profile == profileId && it.address.value == oldAddress }
            entry.address.value = newAddress
            entry.enabled.value = newEnabled
            entry.numChannelsRequested.value = numChannelsRequested
        }
        device.localProfiles.profileEnabledChanged.add { profile ->
            val dst = localProfileStates.first { it.profile == profile.profile && it.address.value == profile.address }
            dst.enabled.value = profile.enabled
        }
    }
}