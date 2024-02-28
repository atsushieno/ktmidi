package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.propertycommonrules.CommonRulesPropertyClient
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyCommonHeaderKeys
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyExchangeStatus

class ClientConnection(
    private val parent: PropertyExchangeInitiator,
    val targetMUID: Int,
    val device: DeviceDetails,
    var maxSimultaneousPropertyRequests: Byte = 0,
    var productInstanceId: String = "",
    val propertyClient: MidiCIClientPropertyRules = CommonRulesPropertyClient(parent.logger, parent.muid, device)
) {

    // This is going to be the entry point for all the profile client features foe MidiCIDevice.
    class ProfileClient(private val conn: ClientConnection) {
        val device by conn.parent::parent

        val profiles = ObservableProfileList(mutableListOf())

        fun setProfile(address: Byte, group: Byte, profile: MidiCIProfileId, enabled: Boolean, numChannelsRequested: Short) {
            val common = Message.Common(device.muid, conn.targetMUID, address, group)
            if (enabled) {
                val msg = Message.SetProfileOn(common, profile,
                    // NOTE: juce_midi_ci has a bug that it expects 1 for 7E and 7F, whereas MIDI-CI v1.2 states:
                    //   "When the Profile Destination field is set to address 0x7E or 0x7F, the number of Channels is determined
                    //    by the width of the Group or Function Block. Set the Number of Channels Requested field to a value of 0x0000."
                    if (address < 0x10 || ImplementationSettings.workaroundJUCEProfileNumChannelsIssue)
                    { if (numChannelsRequested < 1) 1 else numChannelsRequested }
                    else numChannelsRequested
                )
                device.send(msg)
            } else {
                val msg = Message.SetProfileOff(common, profile)
                device.send(msg)
            }
        }

        fun processProfileReply(msg: Message.ProfileReply) {
            msg.enabledProfiles.forEach {
                profiles.add(MidiCIProfile(it, msg.group, msg.address, true, if (msg.address >= 0x7E) 0 else 1))
            }
            msg.disabledProfiles.forEach {
                profiles.add(MidiCIProfile(it, msg.group, msg.address, false, if (msg.address >= 0x7E) 0 else 1))
            }
        }

        fun processProfileAddedReport(msg: Message.ProfileAdded) {
            profiles.add(MidiCIProfile(msg.profile, msg.group, msg.address, false, if (msg.address >= 0x7E) 0 else 1))
        }

        fun processProfileRemovedReport(msg: Message.ProfileRemoved) {
            profiles.remove(MidiCIProfile(msg.profile, msg.group, msg.address, false, 0))
        }

        fun processProfileEnabledReport(msg: Message.ProfileEnabled) {
            profiles.setEnabled(true, msg.address, msg.profile, msg.numChannelsEnabled)
        }

        fun processProfileDisabledReport(msg: Message.ProfileDisabled) {
            profiles.setEnabled(false, msg.address, msg.profile, msg.numChannelsDisabled)
        }

        fun processProfileDetailsReply(msg: Message.ProfileDetailsReply) {
            // nothing to perform so far - use events if you need anything further
        }
    }

    val profileClient = ProfileClient(this)

    val properties = ClientObservablePropertyList(parent.logger, propertyClient)

    private val openRequests = mutableListOf<Message.GetPropertyData>()
    val subscriptions = mutableListOf<PropertyExchangeInitiator.ClientSubscription>()
    val subscriptionUpdated = mutableListOf<(sub: PropertyExchangeInitiator.ClientSubscription)->Unit>()

    val pendingChunkManager = PropertyChunkManager()

    fun updateProperty(msg: Message.GetPropertyDataReply): String? {
        val req = openRequests.firstOrNull { it.requestId == msg.requestId } ?: return null
        openRequests.remove(req)
        val status = propertyClient.getHeaderFieldInteger(msg.header, PropertyCommonHeaderKeys.STATUS) ?: return null

        if (status == PropertyExchangeStatus.OK) {
            propertyClient.onGetPropertyDataReply(req, msg)
            val propertyId = propertyClient.getPropertyIdForHeader(req.header)
            properties.updateValue(propertyId, msg)
            return propertyId
        }
        else
            return null
    }

    fun updateProperty(ourMUID: Int, msg: Message.SubscribeProperty): Pair<String?, Message.SubscribePropertyReply?> {
        val command = properties.updateValue(msg)
        return Pair(
            command,
            Message.SubscribePropertyReply(
                Message.Common(ourMUID, msg.sourceMUID, msg.address, msg.group),
                msg.requestId,
                propertyClient.createStatusHeader(PropertyExchangeStatus.OK), listOf()
            )
        )
    }

    fun addPendingRequest(msg: Message.GetPropertyData) {
        openRequests.add(msg)
    }
    fun addPendingSubscription(requestId: Byte, subscriptionId: String?, propertyId: String) {
        val sub = PropertyExchangeInitiator.ClientSubscription(
            requestId,
            subscriptionId,
            propertyId,
            PropertyExchangeInitiator.SubscriptionActionState.Subscribing
        )
        subscriptions.add(sub)
        subscriptionUpdated.forEach { it(sub) }
    }

    fun promoteSubscriptionAsUnsubscribing(propertyId: String, newRequestId: Byte) {
        val sub = subscriptions.firstOrNull { it.propertyId == propertyId }
        if (sub == null) {
            parent.logger.logError("Cannot unsubscribe property as not found: $propertyId")
            return
        }
        if (sub.state == PropertyExchangeInitiator.SubscriptionActionState.Unsubscribing) {
            parent.logger.logError("Unsubscription for the property is already underway (property: ${sub.propertyId}, subscriptionId: ${sub.subscriptionId}, state: ${sub.state})")
            return
        }
        sub.pendingRequestId = newRequestId
        sub.state = PropertyExchangeInitiator.SubscriptionActionState.Unsubscribing
        subscriptionUpdated.forEach { it(sub) }
    }

    fun processPropertySubscriptionReply(msg: Message.SubscribePropertyReply) {
        val subscriptionId = propertyClient.getHeaderFieldString(msg.header, PropertyCommonHeaderKeys.SUBSCRIBE_ID)
        if (subscriptionId == null) {
            parent.logger.logError("Subscription ID is missing in the Reply to Subscription message. requestId: ${msg.requestId}")
            if (!ImplementationSettings.workaroundJUCEMissingSubscriptionIdIssue)
                return
        }
        val sub = subscriptions.firstOrNull { subscriptionId == it.subscriptionId }
            ?: subscriptions.firstOrNull { it.pendingRequestId == msg.requestId }
        if (sub == null) {
            parent.logger.logError("There was no pending subscription that matches subscribeId ($subscriptionId) or requestId (${msg.requestId})")
            return
        }

        when (sub.state) {
            PropertyExchangeInitiator.SubscriptionActionState.Subscribed,
            PropertyExchangeInitiator.SubscriptionActionState.Unsubscribed -> {
                parent.logger.logError("Received Subscription Reply, but it is unexpected (property: ${sub.propertyId}, subscriptionId: ${sub.subscriptionId}, state: ${sub.state})")
                return
            }
            else -> {}
        }

        sub.subscriptionId = subscriptionId

        propertyClient.processPropertySubscriptionResult(sub, msg)

        if (sub.state == PropertyExchangeInitiator.SubscriptionActionState.Unsubscribing) {
            // do unsubscribe
            sub.state = PropertyExchangeInitiator.SubscriptionActionState.Unsubscribed
            subscriptions.remove(sub)
            subscriptionUpdated.forEach { it(sub) }
        } else {
            sub.state = PropertyExchangeInitiator.SubscriptionActionState.Subscribed
            subscriptionUpdated.forEach { it(sub) }
        }
    }
}