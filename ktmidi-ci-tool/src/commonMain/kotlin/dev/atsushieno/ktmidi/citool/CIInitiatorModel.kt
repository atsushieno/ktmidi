package dev.atsushieno.ktmidi.citool

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.ci.propertycommonrules.CommonRulesPropertyClient
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyResourceNames

class CIInitiatorModel(private val device: CIDeviceModel) {
    private val initiator by lazy { device.device.initiator }

    fun sendEndpointMessage(targetMUID: Int) {
        device.device.sendEndpointMessage(device.defaultSenderGroup, targetMUID)
    }

    fun sendProfileDetailsInquiry(address: Byte, muid: Int, profile: MidiCIProfileId, target: Byte) {
        device.device.requestProfileDetails(device.defaultSenderGroup, address, muid, profile, target)
    }

    fun sendGetPropertyDataRequest(destinationMUID: Int, resource: String, encoding: String?, paginateOffset: Int?, paginateLimit: Int?) {
        initiator.saveAndSendGetPropertyData(device.defaultSenderGroup, destinationMUID, resource, encoding, paginateOffset, paginateLimit)
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
}

class ClientConnectionModel(val parent: CIDeviceModel, val conn: ClientConnection) {

    val profiles = mutableStateListOf<MidiCIProfileState>().apply {
        addAll(conn.profileClient.profiles.profiles.map { MidiCIProfileState(
            mutableStateOf(it.group),
            mutableStateOf(it.address),
            it.profile,
            mutableStateOf(it.enabled),
            mutableStateOf(it.numChannelsRequested)
        ) })
    }

    fun setProfile(address: Byte, profile: MidiCIProfileId, newEnabled: Boolean, newNumChannelsRequested: Short) =
        conn.profileClient.setProfile(address, parent.defaultSenderGroup, profile, newEnabled, newNumChannelsRequested)

    var deviceInfo = mutableStateOf((conn.propertyClient as CommonRulesPropertyClient).deviceInfo)

    val properties = mutableStateListOf<PropertyValue>().apply { addAll(conn.properties.values)}

    fun getMetadataList() = conn.propertyClient.getMetadataList()

    data class SubscriptionState(val propertyId: String, var state: MutableState<PropertyExchangeInitiator.SubscriptionActionState>)
    var subscriptions = mutableStateListOf<SubscriptionState>()

    fun requestMidiMessageReport(address: Byte, targetMUID: Int,
                                 messageDataControl: Byte = MidiMessageReportDataControl.Full,
                                 systemMessages: Byte = MidiMessageReportSystemMessagesFlags.All,
                                 channelControllerMessages: Byte = MidiMessageReportChannelControllerFlags.All,
                                 noteDataMessages: Byte = MidiMessageReportNoteDataFlags.All
    ) {
        parent.device.sendMidiMessageReportInquiry(parent.defaultSenderGroup, address, targetMUID, messageDataControl, systemMessages, channelControllerMessages, noteDataMessages)
    }

    init {
        conn.profileClient.profiles.profilesChanged.add { change, profile ->
            when (change) {
                ObservableProfileList.ProfilesChange.Added ->
                    profiles.add(
                        MidiCIProfileState(
                        mutableStateOf(profile.group),
                        mutableStateOf(profile.address),
                        profile.profile,
                        mutableStateOf(profile.enabled),
                        mutableStateOf(profile.numChannelsRequested)
                    )
                    )
                ObservableProfileList.ProfilesChange.Removed ->
                    profiles.removeAll {it.profile == profile.profile && it.group.value == profile.group && it.address.value == profile.address }
            }
        }
        conn.profileClient.profiles.profileEnabledChanged.add { profile ->
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
            if (entry.id == PropertyResourceNames.DEVICE_INFO)
                deviceInfo.value = (conn.propertyClient as CommonRulesPropertyClient).deviceInfo
        }

        conn.properties.propertiesCatalogUpdated.add {
            properties.clear()
            properties.addAll(conn.properties.values)
        }

        conn.subscriptionUpdated.add { sub ->
            if (sub.state == PropertyExchangeInitiator.SubscriptionActionState.Subscribing)
                subscriptions.add(SubscriptionState(sub.propertyId, mutableStateOf(sub.state)))
            else {
                val state = subscriptions.firstOrNull { sub.propertyId == it.propertyId } ?: return@add
                state.state.value = sub.state
                if (sub.state == PropertyExchangeInitiator.SubscriptionActionState.Unsubscribed)
                    subscriptions.remove(state)
            }
        }
    }
}

