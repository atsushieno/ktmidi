package dev.atsushieno.ktmidi.citool

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.citool.view.MidiCIProfileState
import dev.atsushieno.ktmidi.citool.view.ViewModel
import kotlin.random.Random

class CIInitiatorModel(private val outputSender: (ciBytes: List<Byte>) -> Unit) {
    val initiator by lazy {
        MidiCIInitiator(AppModel.muid, AppModel.initiator) { data ->
            AppModel.log("[Initiator sent SYSEX] " + data.joinToString { it.toString(16) },
                MessageDirection.Out)
            outputSender(data)
        }.apply {
            config.productInstanceId = "ktmidi-ci" + (Random.nextInt() % 65536)
            logger.logEventReceived.add { msg, direction ->
                AppModel.log(msg, direction)
            }

            val cml = this@CIInitiatorModel.connections
            connectionsChanged.add { change, conn ->
                when (change) {
                    MidiCIInitiator.ConnectionChange.Added -> cml.add(ConnectionModel(conn))
                    MidiCIInitiator.ConnectionChange.Removed -> cml.remove(cml.firstOrNull { conn == it.conn })
                    else -> {}
                }
            }
        }
    }

    val connections = mutableStateListOf<ConnectionModel>()

    fun processCIMessage(data: List<Byte>) {
        AppModel.log("[Initiator received SYSEX] " + data.joinToString { it.toString(16) },
            MessageDirection.In)
        initiator.processInput(data)
    }

    fun sendDiscovery() {
        initiator.sendDiscovery()
    }

    // FIXME: we need to make MidiCIInitiator EndpointInquiry hook-able.
    fun sendEndpointMessage(targetMUID: Int) {
        initiator.sendEndpointMessage(targetMUID)
    }

    fun setProfile(destinationMUID: Int, address: Byte, profile: MidiCIProfileId, nextEnabled: Boolean) {
        if (nextEnabled) {
            // FIXME: maybe we should pass number of channels somehow?
            val msg = Message.SetProfileOn(address, initiator.muid, destinationMUID, profile,
                // NOTE: juce_midi_ci has a bug that it expects 1 for 7E and 7F, whereas MIDI-CI v1.2 states:
                //   "When the Profile Destination field is set to address 0x7E or 0x7F, the number of Channels is determined
                //    by the width of the Group or Function Block. Set the Number of Channels Requested field to a value of 0x0000."
                if (address < 0x10 || ViewModel.settings.workaroundJUCEProfileNumChannelsIssue.value) 1
                else 0)
            initiator.setProfileOn(msg)
        } else {
            val msg = Message.SetProfileOff(address, initiator.muid, destinationMUID, profile)
            initiator.setProfileOff(msg)
        }
    }

    fun sendProfileDetailsInquiry(address: Byte, muid: Int, profile: MidiCIProfileId, target: Byte) {
        initiator.requestProfileDetails(address, muid, profile, target)
    }

    fun sendGetPropertyDataRequest(destinationMUID: Int, resource: String, encoding: String?) {
        initiator.sendGetPropertyData(destinationMUID, resource, encoding)
    }
    fun sendSetPropertyDataRequest(destinationMUID: Int, resource: String, data: List<Byte>, encoding: String?, isPartial: Boolean) {
        initiator.sendSetPropertyData(destinationMUID, resource, data, encoding, isPartial)
    }
    fun sendSubscribeProperty(destinationMUID: Int, resource: String, mutualEncoding: String?) {
        initiator.sendSubscribeProperty(destinationMUID, resource, mutualEncoding)
    }
    fun sendUnsubscribeProperty(destinationMUID: Int, resource: String, mutualEncoding: String?) {
        initiator.sendUnsubscribeProperty(destinationMUID, resource, mutualEncoding)
    }

    fun requestMidiMessageReport(address: Byte, targetMUID: Int,
                                 messageDataControl: Byte = MidiMessageReportDataControl.Full,
                                 systemMessages: Byte = MidiMessageReportSystemMessagesFlags.All.toByte(),
                                 channelControllerMessages: Byte = MidiMessageReportChannelControllerFlags.All.toByte(),
                                 noteDataMessages: Byte = MidiMessageReportNoteDataFlags.All.toByte()
    ) {
        initiator.sendMidiMessageReportInquiry(address, targetMUID, messageDataControl, systemMessages, channelControllerMessages, noteDataMessages)
    }
}

class ConnectionModel(val conn: MidiCIInitiator.Connection) {

    val profiles = mutableStateListOf<MidiCIProfileState>().apply {
        addAll(conn.profiles.profiles.map { MidiCIProfileState(mutableStateOf(it.address), it.profile, mutableStateOf(it.enabled)) })
    }

    val properties = mutableStateListOf<PropertyValue>().apply { addAll(conn.properties.values)}

    fun getMetadataList() = conn.propertyClient.getMetadataList()

    data class SubscriptionState(val propertyId: String, var state: MutableState<MidiCIInitiator.SubscriptionActionState>)
    var subscriptions = mutableStateListOf<SubscriptionState>()

    init {
        conn.profiles.profilesChanged.add { change, profile ->
            when (change) {
                ObservableProfileList.ProfilesChange.Added ->
                    profiles.add(MidiCIProfileState(mutableStateOf(profile.address), profile.profile, mutableStateOf(profile.enabled)))
                ObservableProfileList.ProfilesChange.Removed ->
                    profiles.removeAll {it.profile == profile.profile && it.address.value == profile.address }
            }
        }
        conn.profiles.profileEnabledChanged.add { profile, numChannelsRequested ->
            if (numChannelsRequested > 1)
                TODO("FIXME: implement")
            profiles.filter { it.profile == profile.profile && it.address.value == profile.address }
                .forEach { Snapshot.withMutableSnapshot { it.enabled.value = profile.enabled } }
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

