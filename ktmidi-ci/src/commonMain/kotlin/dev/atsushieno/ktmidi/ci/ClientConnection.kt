package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.propertycommonrules.CommonRulesPropertyClient
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyCommonHeaderKeys
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyExchangeStatus
import dev.atsushieno.ktmidi.ci.propertycommonrules.PropertyResourceNames

enum class SubscriptionActionState {
    Subscribing,
    Subscribed,
    Unsubscribing,
    Unsubscribed
}
data class ClientSubscription(var pendingRequestId: Byte?, var subscriptionId: String?, val propertyId: String, var state: SubscriptionActionState)

class ClientConnection(
    private val parent: MidiCIDevice,
    val targetMUID: Int,
    val device: DeviceDetails,
    var maxSimultaneousPropertyRequests: Byte = 0,
    var productInstanceId: String = "",
    val propertyClient: MidiCIClientPropertyRules = CommonRulesPropertyClient(parent.logger, parent.muid, device)
) {

    // This is going to be the entry point for all the profile client features foe MidiCIDevice.
    class ProfileClient(private val conn: ClientConnection) {
        val device by conn::parent

        val profiles = ObservableProfileList(mutableListOf())

        fun setProfile(
            address: Byte,
            group: Byte,
            profile: MidiCIProfileId,
            enabled: Boolean,
            numChannelsRequested: Short
        ) {
            val common = Message.Common(device.muid, conn.targetMUID, address, group)
            if (enabled) {
                val msg = Message.SetProfileOn(
                    common, profile,
                    // NOTE: juce_midi_ci has a bug that it expects 1 for 7E and 7F, whereas MIDI-CI v1.2 states:
                    //   "When the Profile Destination field is set to address 0x7E or 0x7F, the number of Channels is determined
                    //    by the width of the Group or Function Block. Set the Number of Channels Requested field to a value of 0x0000."
                    if (address < 0x10 || ImplementationSettings.workaroundJUCEProfileNumChannelsIssue) {
                        if (numChannelsRequested < 1) 1 else numChannelsRequested
                    } else numChannelsRequested
                )
                device.messenger.send(msg)
            } else {
                val msg = Message.SetProfileOff(common, profile)
                device.messenger.send(msg)
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

    // FIXME: isolate them into another class.
    //  It should be `PropertyClient`, but the name conflicts with the constructor argument right now...!

    val properties = ClientObservablePropertyList(parent.logger, propertyClient)

    private val openRequests = mutableListOf<Message.GetPropertyData>()
    val subscriptions = mutableListOf<ClientSubscription>()
    val subscriptionUpdated = mutableListOf<(sub: ClientSubscription)->Unit>()

    val pendingChunkManager = PropertyChunkManager()

    fun updateRemoteProperty(msg: Message.GetPropertyDataReply): String? {
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

    fun updateLocalProperty(ourMUID: Int, msg: Message.SubscribeProperty): Pair<String?, Message.SubscribePropertyReply?> {
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
        val sub = ClientSubscription(
            requestId,
            subscriptionId,
            propertyId,
            SubscriptionActionState.Subscribing
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
        if (sub.state == SubscriptionActionState.Unsubscribing) {
            parent.logger.logError("Unsubscription for the property is already underway (property: ${sub.propertyId}, subscriptionId: ${sub.subscriptionId}, state: ${sub.state})")
            return
        }
        sub.pendingRequestId = newRequestId
        sub.state = SubscriptionActionState.Unsubscribing
        subscriptionUpdated.forEach { it(sub) }
    }

    fun processPropertySubscriptionReply(msg: Message.SubscribePropertyReply) {
        if (propertyClient.getHeaderFieldInteger(msg.header, PropertyCommonHeaderKeys.STATUS) != PropertyExchangeStatus.OK)
            return // FIXME: should we do anything further here?

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
            SubscriptionActionState.Subscribed,
            SubscriptionActionState.Unsubscribed -> {
                parent.logger.logError("Received Subscription Reply, but it is unexpected (property: ${sub.propertyId}, subscriptionId: ${sub.subscriptionId}, state: ${sub.state})")
                return
            }
            else -> {}
        }

        sub.subscriptionId = subscriptionId

        propertyClient.processPropertySubscriptionResult(sub, msg)

        if (sub.state == SubscriptionActionState.Unsubscribing) {
            // do unsubscribe
            sub.state = SubscriptionActionState.Unsubscribed
            subscriptions.remove(sub)
            subscriptionUpdated.forEach { it(sub) }
        } else {
            sub.state = SubscriptionActionState.Subscribed
            subscriptionUpdated.forEach { it(sub) }
        }
    }

    // FIXME: too much exposure of Common Rules for PE
    fun saveAndSendGetPropertyData(group: Byte, destinationMUID: Int, resource: String, encoding: String? = null, paginateOffset: Int? = null, paginateLimit: Int? = null) {
        val header = propertyClient.createDataRequestHeader(resource, mapOf(
            PropertyCommonHeaderKeys.MUTUAL_ENCODING to encoding,
            PropertyCommonHeaderKeys.SET_PARTIAL to false,
            PropertyCommonHeaderKeys.OFFSET to paginateOffset,
            PropertyCommonHeaderKeys.LIMIT to paginateLimit
        ).filter { it.value != null })
        val msg = Message.GetPropertyData(Message.Common(parent.muid, destinationMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group),
            parent.messenger.requestIdSerial++, header)
        saveAndSendGetPropertyData(msg)
    }

    fun saveAndSendGetPropertyData(msg: Message.GetPropertyData) {
        addPendingRequest(msg)
        parent.messenger.send(msg)
    }

    // FIXME: too much exposure of Common Rules for PE
    fun sendSetPropertyData(group: Byte, destinationMUID: Int, resource: String, data: List<Byte>, encoding: String? = null, isPartial: Boolean = false) {
        val header = propertyClient.createDataRequestHeader(resource, mapOf(
            PropertyCommonHeaderKeys.MUTUAL_ENCODING to encoding,
            PropertyCommonHeaderKeys.SET_PARTIAL to isPartial))
        val encodedBody = propertyClient.encodeBody(data, encoding)
        parent.messenger.send(Message.SetPropertyData(Message.Common(parent.muid, destinationMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group),
            parent.messenger.requestIdSerial++, header, encodedBody))
    }

    fun sendSubscribeProperty(group: Byte, destinationMUID: Int, resource: String, mutualEncoding: String?, subscriptionId: String? = null) {
        val header = propertyClient.createSubscriptionHeader(resource, mapOf(
            PropertyCommonHeaderKeys.COMMAND to MidiCISubscriptionCommand.START,
            PropertyCommonHeaderKeys.MUTUAL_ENCODING to mutualEncoding))
        val msg = Message.SubscribeProperty(Message.Common(parent.muid, destinationMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group),
            parent.messenger.requestIdSerial++, header, listOf())
        addPendingSubscription(msg.requestId, subscriptionId, resource)
        parent.messenger.send(msg)
    }

    fun sendUnsubscribeProperty(group: Byte, destinationMUID: Int, resource: String, mutualEncoding: String?, subscriptionId: String? = null) {
        val newRequestId = parent.messenger.requestIdSerial++
        val header = propertyClient.createSubscriptionHeader(resource, mapOf(
            PropertyCommonHeaderKeys.COMMAND to MidiCISubscriptionCommand.END,
            PropertyCommonHeaderKeys.MUTUAL_ENCODING to mutualEncoding))
        val msg = Message.SubscribeProperty(Message.Common(parent.muid, destinationMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, group),
            newRequestId, header, listOf())
        promoteSubscriptionAsUnsubscribing(resource, newRequestId)
        parent.messenger.send(msg)
    }

    // client PE event receivers

    fun processPropertyCapabilitiesReply(msg: Message.PropertyGetCapabilitiesReply) {
        maxSimultaneousPropertyRequests = msg.maxSimultaneousRequests

        // proceed to query resource list
        if (parent.config.autoSendGetResourceList)
            saveAndSendGetPropertyData(propertyClient.getPropertyListRequest(msg.group, msg.sourceMUID, parent.messenger.requestIdSerial++))
    }

    fun processGetDataReply(msg: Message.GetPropertyDataReply) {
        val propertyId = updateRemoteProperty(msg)

        // If the reply was ResourceList, and the parsed body contained an entry for DeviceInfo, and
        //  if it is configured as auto-queried, then send another Get Property Data request for it.
        if (parent.config.autoSendGetDeviceInfo && propertyId == PropertyResourceNames.RESOURCE_LIST) {
            val def = propertyClient.getMetadataList()?.firstOrNull { it.resource == PropertyResourceNames.DEVICE_INFO }
            if (def != null)
                saveAndSendGetPropertyData(msg.group, msg.sourceMUID, def.resource, def.encodings.firstOrNull())
        }
    }

    fun processSubscribeProperty(msg: Message.SubscribeProperty) {
        val reply = updateLocalProperty(parent.muid, msg)
        if (reply.second != null)
            parent.messenger.send(reply.second!!)
        // If the update was NOTIFY, then it is supposed to send Get Data request.
        if (reply.first == MidiCISubscriptionCommand.NOTIFY)
            saveAndSendGetPropertyData(msg.group, msg.sourceMUID, propertyClient.getPropertyIdForHeader(msg.header),
                // is there mutualEncoding from SubscribeProperty?
                encoding = null,
                paginateOffset = null, paginateLimit = null)
    }
}