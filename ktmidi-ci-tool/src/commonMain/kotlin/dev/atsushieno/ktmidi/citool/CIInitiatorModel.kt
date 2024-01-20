package dev.atsushieno.ktmidi.citool

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.citool.view.MidiCIProfileState
import dev.atsushieno.ktmidi.citool.view.ViewModel
import kotlin.math.min

class CIInitiatorModel(private val device: CIDeviceModel) {
    val initiator by lazy { device.device.initiator }

    val connections = mutableStateListOf<ClientConnectionModel>()

    fun sendEndpointMessage(targetMUID: Int) {
        initiator.sendEndpointMessage(device.defaultSenderGroup, targetMUID)
    }

    fun setProfile(destinationMUID: Int, address: Byte, profile: MidiCIProfileId, nextEnabled: Boolean, newNumChannelsRequested: Short) {
        if (nextEnabled) {
            // FIXME: maybe we should pass number of channels somehow?
            val msg = Message.SetProfileOn(Message.Common(initiator.muid, destinationMUID, address, device.defaultSenderGroup), profile,
                // NOTE: juce_midi_ci has a bug that it expects 1 for 7E and 7F, whereas MIDI-CI v1.2 states:
                //   "When the Profile Destination field is set to address 0x7E or 0x7F, the number of Channels is determined
                //    by the width of the Group or Function Block. Set the Number of Channels Requested field to a value of 0x0000."
                if (address < 0x10 || ViewModel.settings.workaroundJUCEProfileNumChannelsIssue.value)
                    { if (newNumChannelsRequested < 1) 1 else newNumChannelsRequested }
                else newNumChannelsRequested
            )
            initiator.setProfileOn(msg)
        } else {
            val msg = Message.SetProfileOff(Message.Common(initiator.muid, destinationMUID, address, device.defaultSenderGroup), profile)
            initiator.setProfileOff(msg)
        }
    }

    fun sendProfileDetailsInquiry(address: Byte, muid: Int, profile: MidiCIProfileId, target: Byte) {
        initiator.requestProfileDetails(device.defaultSenderGroup, address, muid, profile, target)
    }

    fun sendGetPropertyDataRequest(destinationMUID: Int, resource: String, encoding: String?, paginateOffset: Int?, paginateLimit: Int?) {
        initiator.sendGetPropertyData(device.defaultSenderGroup, destinationMUID, resource, encoding, paginateOffset, paginateLimit)
    }
    fun sendSetPropertyDataRequest(destinationMUID: Int, resource: String, data: List<Byte>, encoding: String?, isPartial: Boolean) {
        initiator.sendSetPropertyData(device.defaultSenderGroup, destinationMUID, resource, data, encoding, isPartial)
    }
    fun sendSubscribeProperty(destinationMUID: Int, resource: String, mutualEncoding: String?) {
        initiator.sendSubscribeProperty(device.defaultSenderGroup, destinationMUID, resource, mutualEncoding)
    }
    fun sendUnsubscribeProperty(destinationMUID: Int, resource: String, mutualEncoding: String?) {
        initiator.sendUnsubscribeProperty(device.defaultSenderGroup, destinationMUID, resource, mutualEncoding)
    }

    fun requestMidiMessageReport(address: Byte, targetMUID: Int,
                                 messageDataControl: Byte = MidiMessageReportDataControl.Full,
                                 systemMessages: Byte = MidiMessageReportSystemMessagesFlags.All.toByte(),
                                 channelControllerMessages: Byte = MidiMessageReportChannelControllerFlags.All.toByte(),
                                 noteDataMessages: Byte = MidiMessageReportNoteDataFlags.All.toByte()
    ) {
        initiator.sendMidiMessageReportInquiry(device.defaultSenderGroup, address, targetMUID, messageDataControl, systemMessages, channelControllerMessages, noteDataMessages)
    }

    init {
        initiator.connectionsChanged.add { change, conn ->
            connections.add(ClientConnectionModel(device, conn))
        }
    }
}

class ClientConnectionModel(val parent: CIDeviceModel, val conn: MidiCIInitiator.ClientConnection) {

    val profiles = mutableStateListOf<MidiCIProfileState>().apply {
        addAll(conn.profiles.profiles.map { MidiCIProfileState(
            mutableStateOf(it.group),
            mutableStateOf(it.address),
            it.profile,
            mutableStateOf(it.enabled),
            mutableStateOf(it.numChannelsRequested)
        ) })
    }

    val properties = mutableStateListOf<PropertyValue>().apply { addAll(conn.properties.values)}

    fun getMetadataList() = conn.propertyClient.getMetadataList()

    data class SubscriptionState(val propertyId: String, var state: MutableState<MidiCIInitiator.SubscriptionActionState>)
    var subscriptions = mutableStateListOf<SubscriptionState>()

    init {
        conn.profiles.profilesChanged.add { change, profile ->
            when (change) {
                ObservableProfileList.ProfilesChange.Added ->
                    profiles.add(MidiCIProfileState(
                        mutableStateOf(profile.group),
                        mutableStateOf(profile.address),
                        profile.profile,
                        mutableStateOf(profile.enabled),
                        mutableStateOf(profile.numChannelsRequested)
                    ))
                ObservableProfileList.ProfilesChange.Removed ->
                    profiles.removeAll {it.profile == profile.profile && it.group.value == profile.group && it.address.value == profile.address }
            }
        }
        conn.profiles.profileEnabledChanged.add { profile ->
            profiles.filter { it.profile == profile.profile && it.group.value == profile.group && it.address.value == profile.address }
                .forEach {
                    it.enabled.value = profile.enabled
                    it.numChannelsRequested.value = profile.numChannelsRequested
                }
        }

        conn.properties.valueUpdated.add { entry ->
            val index = properties.indexOfFirst { it.id == entry.id }
            if (index < 0)
                properties.add(entry)
            else {
                properties.removeAt(index)
                properties.add(index, entry)
            }
        }

        conn.properties.propertiesCatalogUpdated.add {
            properties.clear()
            properties.addAll(conn.properties.values)
        }

        conn.subscriptionUpdated.add { sub ->
            if (sub.state == MidiCIInitiator.SubscriptionActionState.Subscribing)
                subscriptions.add(SubscriptionState(sub.propertyId, mutableStateOf(sub.state)))
            else {
                val state = subscriptions.firstOrNull { sub.propertyId == it.propertyId } ?: return@add
                state.state.value = sub.state
                if (sub.state == MidiCIInitiator.SubscriptionActionState.Unsubscribed)
                    subscriptions.remove(state)
            }
        }
    }
}

