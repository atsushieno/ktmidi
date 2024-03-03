package dev.atsushieno.ktmidi.citool

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.ci.profilecommonrules.DefaultControlChangesProfile

class CIDeviceModel(val parent: CIDeviceManager, val muid: Int, config: MidiCIDeviceConfiguration,
                    private val ciOutputSender: (group: Byte, ciBytes: List<Byte>) -> Unit,
                    private val midiMessageReportOutputSender: (group: Byte, bytes: List<Byte>) -> Unit) {
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
                ciOutputSender(group, data)
            },
            sendMidiMessageReport = { group, protocol, data ->
                parent.owner.log(
                    "[sent MIDI Message Report (protocol=$protocol)] " + data.joinToString { it.toUByte().toString(16) },
                    MessageDirection.Out
                )
                midiMessageReportOutputSender(group, data)
            }
        ).apply {
            // initiator
            logger.logEventReceived.add { msg, direction ->
                parent.owner.log(msg, direction)
            }

            connectionsChanged.add { change, conn ->
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
            messageReceived.add {
                receivingMidiMessageReports = true
                midiMessageReportModeChanged.forEach { it() }
            }
            messageReceived.add {
                receivingMidiMessageReports = false
                midiMessageReportModeChanged.forEach { it() }
            }

            midiMessageReporter = MidiMachineMessageReporter()

            // responder
            profileHost.onProfileSet.add { profile -> profileHost.profiles.profileEnabledChanged.forEach { it(profile) } }
        }
    }

    fun processCIMessage(group: Byte, data: List<Byte>) {
        if (data.isEmpty()) return
        parent.owner.log("[received CI SysEx (grp:$group)] " + data.joinToString { it.toUByte().toString(16) }, MessageDirection.In)
        device.processInput(group, data)
    }

    // observable state
    val localProfileStates = mutableStateListOf<MidiCIProfileState>().apply {
        addAll(device.profileHost.profiles.profiles.map {
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
        device.sendDiscovery()
    }

    // remote profile configuration

    fun sendProfileDetailsInquiry(address: Byte, muid: Int, profile: MidiCIProfileId, target: Byte) {
        device.requestProfileDetails(address, muid, profile, target)
    }

    // local profile configuration

    fun updateLocalProfileTarget(profileState: MidiCIProfileState, newAddress: Byte, enabled: Boolean, numChannelsRequested: Short) =
        device.profileHost.updateProfileTarget(profileState.profile, profileState.address.value, newAddress, enabled, numChannelsRequested)

    fun addLocalProfile(profile: MidiCIProfile) = device.profileHost.addProfile(profile)

    fun removeLocalProfile(group: Byte, address: Byte, profileId: MidiCIProfileId) = device.profileHost.removeProfile(group, address, profileId)

    fun updateLocalProfileName(oldProfile: MidiCIProfileId, newProfile: MidiCIProfileId) {
        val removed = device.profileHost.profiles.profiles.filter { it.profile == oldProfile }
        val added = removed.map { MidiCIProfile(newProfile, it.group, it.address, it.enabled, it.numChannelsRequested) }
        removed.forEach { removeLocalProfile(it.group, it.address, it.profile) }
        added.forEach { addLocalProfile(it) }
    }

    // Local property exchange
    fun addLocalProperty(property: PropertyMetadata) = device.propertyHost.addProperty(property)

    fun removeLocalProperty(propertyId: String) = device.propertyHost.removeProperty(propertyId)

    fun updatePropertyMetadata(oldPropertyId: String, property: PropertyMetadata) =
        device.propertyHost.updatePropertyMetadata(oldPropertyId, property)

    fun shutdownSubscription(destinationMUID: Int, resource: String) {
        device.propertyHost.shutdownSubscription(destinationMUID, resource)
    }

    fun addTestProfileItems() {
        with(device.profileHost.profiles) {
            add(MidiCIProfile(MidiCIProfileId(listOf(0, 1, 2, 3, 4)), 0, 0x7E, true, 0))
            add(MidiCIProfile(MidiCIProfileId(listOf(5, 6, 7, 8, 9)), 0, 0x7F, true, 0))
            add(MidiCIProfile(DefaultControlChangesProfile.profileIdForPartial, 0, 0, false, 1))
            add(MidiCIProfile(DefaultControlChangesProfile.profileIdForPartial, 0, 4, true, 1))
        }
    }

    fun updatePropertyValue(propertyId: String, data: List<Byte>, isPartial: Boolean) {
        device.propertyHost.setPropertyValue(propertyId, data, isPartial)
    }

    fun updateDeviceInfo(deviceInfo: MidiCIDeviceInfo) {
        device.propertyHost.updateDeviceInfo(deviceInfo)
    }

    val localProperties by device.propertyHost::properties

    init {
        device.profileHost.profiles.profilesChanged.add { change, profile ->
            when (change) {
                ObservableProfileList.ProfilesChange.Added ->
                    localProfileStates.add(
                        MidiCIProfileState(
                            mutableStateOf(profile.group),
                            mutableStateOf(profile.address),
                            profile.profile,
                            mutableStateOf(profile.enabled),
                            mutableStateOf(profile.numChannelsRequested))
                    )
                ObservableProfileList.ProfilesChange.Removed ->
                    localProfileStates.removeAll { it.profile == profile.profile && it.address.value == profile.address }
            }
        }
        device.profileHost.profiles.profileUpdated.add { profileId: MidiCIProfileId, oldAddress: Byte, newEnabled: Boolean, newAddress: Byte, numChannelsRequested: Short ->
            val entry = localProfileStates.first { it.profile == profileId && it.address.value == oldAddress }
            entry.address.value = newAddress
            entry.enabled.value = newEnabled
            entry.numChannelsRequested.value = numChannelsRequested
        }
        device.profileHost.profiles.profileEnabledChanged.add { profile ->
            val dst = localProfileStates.first { it.profile == profile.profile && it.address.value == profile.address }
            dst.enabled.value = profile.enabled
        }

        device.connectionsChanged.add { change, conn ->
            when (change) {
                ConnectionChange.Added -> connections.add(ClientConnectionModel(this, conn))
                ConnectionChange.Removed -> connections.removeAll { it.conn == conn }
            }
        }
    }
}