package dev.atsushieno.ktmidi.citool

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.ci.profilecommonrules.DefaultControlChangesProfile
import dev.atsushieno.ktmidi.citool.view.MidiCIProfileState
import kotlin.random.Random

class CIDeviceModel(val parent: CIDeviceManager, muid: Int, config: MidiCIDeviceConfiguration,
                    private val ciOutputSender: (ciBytes: List<Byte>) -> Unit,
                    private val midiMessageReportOutputSender: (bytes: List<Byte>) -> Unit) {

    // from CIInitiatorModel
    val connections = mutableStateListOf<ClientConnectionModel>()

    var receivingMidiMessageReports = false
    var lastChunkedMessageChannel: Byte = -1 // will never match at first.
    val chunkedMessages = mutableListOf<Byte>()
    val midiMessageReportModeChanged = mutableListOf<() -> Unit>()

    val device by lazy {
        MidiCIDevice(muid, config,
            sendCIOutput = { data ->
                AppModel.log(
                    "[sent CI SysEx] " + data.joinToString { it.toUByte().toString(16) },
                    MessageDirection.Out
                )
                ciOutputSender(data)
            },
            sendMidiMessageReport = { protocol, data ->
                AppModel.log(
                    "[sent MIDI Message Report (protocol=$protocol)] " + data.joinToString { it.toUByte().toString(16) },
                    MessageDirection.Out
                )
                midiMessageReportOutputSender(data)
            }
        ).apply {
            // initiator
            logger.logEventReceived.add { msg, direction ->
                AppModel.log(msg, direction)
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

            responder.midiMessageReporter = Midi1MessageReporter(midiMessageReportOutputSender)

            // responder
            onProfileSet.add { profile, numChannelsRequested ->
                localProfiles.profileEnabledChanged.forEach { it(profile, numChannelsRequested) }
            }

            // FIXME: they are dummy items that should be removed.
            localProfiles.add(MidiCIProfile(MidiCIProfileId(0, 1, 2, 3, 4), 0x7E, true))
            localProfiles.add(MidiCIProfile(MidiCIProfileId(5, 6, 7, 8, 9), 0x7F, true))
            localProfiles.add(MidiCIProfile(DefaultControlChangesProfile.profileIdForPartial, 0, false))
            localProfiles.add(MidiCIProfile(DefaultControlChangesProfile.profileIdForPartial, 4, true))
        }
    }

    val initiator = CIInitiatorModel(this)

    fun processCIMessage(data: List<Byte>) {
        if (data.isEmpty()) return
        AppModel.log("[received CI SysEx] " + data.joinToString { it.toUByte().toString(16) }, MessageDirection.In)
        device.processInput(data)
    }

    // observable state
    val localProfileStates = mutableStateListOf<MidiCIProfileState>().apply {
        addAll(device.localProfiles.profiles.map {
            MidiCIProfileState(
                mutableStateOf(it.address),
                it.profile,
                mutableStateOf(it.enabled)
            )
        })
    }

    // Management message client

    fun sendDiscovery() {
        device.sendDiscovery()
    }

    // local profile configuration

    fun updateLocalProfileTarget(profileState: MidiCIProfileState, address: Byte, enabled: Boolean, numChannelsRequested: Short) {
        val profile = device.localProfiles.profiles.first { it.address == profileState.address.value && it.profile == profileState.profile }
        device.localProfiles.update(profile, enabled, address, numChannelsRequested)
    }

    fun addLocalProfile(profile: MidiCIProfile) {
        device.localProfiles.add(profile)
        device.sendProfileAddedReport(profile)
    }

    fun removeLocalProfile(address: Byte, profileId: MidiCIProfileId) {
        // create a dummy entry...
        val profile = MidiCIProfile(profileId, address, false)
        device.localProfiles.remove(profile)
        device.sendProfileRemovedReport(profile)
    }

    fun updateLocalProfileName(oldProfile: MidiCIProfileId, newProfile: MidiCIProfileId) {
        val removed = device.localProfiles.profiles.filter { it.profile == oldProfile }
        val added = removed.map { MidiCIProfile(newProfile, it.address, it.enabled) }
        removed.forEach { removeLocalProfile(it.address, it.profile) }
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
                    localProfileStates.add(MidiCIProfileState(mutableStateOf(profile.address), profile.profile, mutableStateOf(profile.enabled)))
                ObservableProfileList.ProfilesChange.Removed ->
                    localProfileStates.removeAll { it.profile == profile.profile && it.address.value == profile.address }
            }
        }
        device.localProfiles.profileUpdated.add { profileId: MidiCIProfileId, oldAddress: Byte, newEnabled: Boolean, newAddress: Byte, numChannelsRequested: Short ->
            val entry = localProfileStates.first { it.profile == profileId && it.address.value == oldAddress }
            if (numChannelsRequested > 1)
                TODO("FIXME: implement")
            entry.address.value = newAddress
            entry.enabled.value = newEnabled
        }
        device.localProfiles.profileEnabledChanged.add { profile, numChannelsRequested ->
            if (numChannelsRequested > 1)
                TODO("FIXME: implement")
            val dst = localProfileStates.first { it.profile == profile.profile && it.address.value == profile.address }
            dst.enabled.value = profile.enabled
        }
    }
}