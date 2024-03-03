package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.propertycommonrules.*

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
    deviceDetails: DeviceDetails,
    var maxSimultaneousPropertyRequests: Byte = 0,
    var productInstanceId: String = "",
) {
    var propertyRules: MidiCIClientPropertyRules = CommonRulesPropertyClient(parent, this)
    var deviceInfo = MidiCIDeviceInfo(
        deviceDetails.manufacturer,
        deviceDetails.family,
        deviceDetails.modelNumber,
        deviceDetails.softwareRevisionLevel,
        "",
        "",
        "",
        "",
        ""
    )

    val profileClient = ProfileClient(parent, this)

    val propertyClient = PropertyClient(parent, this)
}

// This is going to be the entry point for all the profile client features foe MidiCIDevice.
class ProfileClient(private val device: MidiCIDevice, private val conn: ClientConnection) {

    val profiles = ObservableProfileList(mutableListOf())

    fun setProfile(
        group: Byte,
        address: Byte,
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

    internal fun processProfileReply(msg: Message.ProfileReply) {
        msg.enabledProfiles.forEach {
            profiles.add(MidiCIProfile(it, msg.group, msg.address, true, if (msg.address >= 0x7E) 0 else 1))
        }
        msg.disabledProfiles.forEach {
            profiles.add(MidiCIProfile(it, msg.group, msg.address, false, if (msg.address >= 0x7E) 0 else 1))
        }
    }

    internal fun processProfileAddedReport(msg: Message.ProfileAdded) {
        profiles.add(MidiCIProfile(msg.profile, msg.group, msg.address, false, if (msg.address >= 0x7E) 0 else 1))
    }

    internal fun processProfileRemovedReport(msg: Message.ProfileRemoved) {
        profiles.remove(MidiCIProfile(msg.profile, msg.group, msg.address, false, 0))
    }

    internal  fun processProfileEnabledReport(msg: Message.ProfileEnabled) {
        profiles.setEnabled(true, msg.address, msg.profile, msg.numChannelsEnabled)
    }

    internal fun processProfileDisabledReport(msg: Message.ProfileDisabled) {
        profiles.setEnabled(false, msg.address, msg.profile, msg.numChannelsDisabled)
    }

    internal fun processProfileDetailsReply(msg: Message.ProfileDetailsReply) {
        // nothing to perform so far - use events if you need anything further
    }
}

class PropertyClient(private val device: MidiCIDevice, private val conn: ClientConnection) {
    private val muid by device::muid
    private val logger by device::logger
    private val messenger by device::messenger
    private val targetMUID by conn::targetMUID
    private val propertyRules by conn::propertyRules

    val properties = ClientObservablePropertyList(logger, propertyRules)

    private val openRequests = mutableListOf<Message.GetPropertyData>()
    val subscriptions = mutableListOf<ClientSubscription>()
    val subscriptionUpdated = mutableListOf<(sub: ClientSubscription)->Unit>()

    val pendingChunkManager = PropertyChunkManager()

    private fun updatePropertyBySubscribe(msg: Message.SubscribeProperty): Pair<String?, Message.SubscribePropertyReply?> {
        val command = properties.updateValue(msg)
        return Pair(
            command,
            Message.SubscribePropertyReply(
                Message.Common(muid, msg.sourceMUID, msg.address, msg.group),
                msg.requestId,
                propertyRules.createStatusHeader(PropertyExchangeStatus.OK), listOf()
            )
        )
    }

    private fun handleUnsubscriptionNotification(ourMUID: Int, msg: Message.SubscribeProperty): Pair<String?, Message.SubscribePropertyReply?> {
        val sub = subscriptions.firstOrNull { it.subscriptionId == propertyRules.getHeaderFieldString(msg.header, PropertyCommonHeaderKeys.SUBSCRIBE_ID) } ?:
            subscriptions.firstOrNull { it.propertyId == propertyRules.getPropertyIdForHeader(msg.header) }
        if (sub == null)
            return Pair("subscription ID is not specified in the unsubscription request", null)
        subscriptions.remove(sub)
        return Pair(null, Message.SubscribePropertyReply(
            Message.Common(ourMUID, msg.sourceMUID, msg.address, msg.group),
            msg.requestId,
            propertyRules.createStatusHeader(PropertyExchangeStatus.OK), listOf()
        ))
    }

    private fun addPendingSubscription(requestId: Byte, subscriptionId: String?, propertyId: String) {
        val sub = ClientSubscription(
            requestId,
            subscriptionId,
            propertyId,
            SubscriptionActionState.Subscribing
        )
        subscriptions.add(sub)
        subscriptionUpdated.forEach { it(sub) }
    }

    private fun promoteSubscriptionAsUnsubscribing(propertyId: String, newRequestId: Byte) {
        val sub = subscriptions.firstOrNull { it.propertyId == propertyId }
        if (sub == null) {
            logger.logError("Cannot unsubscribe property as not found: $propertyId")
            return
        }
        if (sub.state == SubscriptionActionState.Unsubscribing) {
            logger.logError("Unsubscription for the property is already underway (property: ${sub.propertyId}, subscriptionId: ${sub.subscriptionId}, state: ${sub.state})")
            return
        }
        sub.pendingRequestId = newRequestId
        sub.state = SubscriptionActionState.Unsubscribing
        subscriptionUpdated.forEach { it(sub) }
    }

    internal fun processPropertySubscriptionReply(msg: Message.SubscribePropertyReply) {
        if (propertyRules.getHeaderFieldInteger(msg.header, PropertyCommonHeaderKeys.STATUS) != PropertyExchangeStatus.OK)
            return // FIXME: should we do anything further here?

        val subscriptionId = propertyRules.getHeaderFieldString(msg.header, PropertyCommonHeaderKeys.SUBSCRIBE_ID)
        val sub = subscriptions.firstOrNull { subscriptionId == it.subscriptionId }
            ?: subscriptions.firstOrNull { it.pendingRequestId == msg.requestId }
        if (sub == null) {
            logger.logError("There was no pending subscription that matches subscribeId ($subscriptionId) or requestId (${msg.requestId})")
            return
        }
        // `subscribeId` must be attached everywhere, except for unsubscription reply.
        if (subscriptionId == null && sub.state != SubscriptionActionState.Unsubscribing) {
            logger.logError("Subscription ID is missing in the Reply to Subscription message. requestId: ${msg.requestId}")
            if (!ImplementationSettings.workaroundJUCEMissingSubscriptionIdIssue)
                return
        }

        when (sub.state) {
            SubscriptionActionState.Subscribed,
            SubscriptionActionState.Unsubscribed -> {
                logger.logError("Received Subscription Reply, but it is unexpected (existing subscription: property = ${sub.propertyId}, subscriptionId = ${sub.subscriptionId}, state = ${sub.state})")
                return
            }
            else -> {}
        }

        sub.subscriptionId = subscriptionId

        propertyRules.processPropertySubscriptionResult(sub, msg)

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

    // It is Common Rules specific
    fun sendGetPropertyData(resource: String, encoding: String? = null, paginateOffset: Int? = null, paginateLimit: Int? = null) {
        val header = propertyRules.createDataRequestHeader(resource, mapOf(
            PropertyCommonHeaderKeys.MUTUAL_ENCODING to encoding,
            PropertyCommonHeaderKeys.SET_PARTIAL to false,
            PropertyCommonHeaderKeys.OFFSET to paginateOffset,
            PropertyCommonHeaderKeys.LIMIT to paginateLimit
        ).filter { it.value != null })
        val msg = Message.GetPropertyData(Message.Common(muid, targetMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, device.config.group),
            messenger.requestIdSerial++, header)
        sendGetPropertyData(msg)
    }

    // unlike the other overload, it is not specific to Common Rules for PE
    fun sendGetPropertyData(msg: Message.GetPropertyData) {
        openRequests.add(msg)
        messenger.send(msg)
    }

    // It is Common Rules specific
    fun sendSetPropertyData(resource: String, data: List<Byte>, encoding: String? = null, isPartial: Boolean = false) {
        val header = propertyRules.createDataRequestHeader(resource, mapOf(
            PropertyCommonHeaderKeys.MUTUAL_ENCODING to encoding,
            PropertyCommonHeaderKeys.SET_PARTIAL to isPartial))
        val encodedBody = propertyRules.encodeBody(data, encoding)
        sendSetPropertyData(header, encodedBody)
    }
    // unlike the other overload, it is not specific to Common Rules for PE
    fun sendSetPropertyData(header: List<Byte>, body: List<Byte>) =
        messenger.send(Message.SetPropertyData(Message.Common(muid, targetMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, device.config.group),
            messenger.requestIdSerial++, header, body))

    fun sendSubscribeProperty(resource: String, mutualEncoding: String? = null, subscriptionId: String? = null) {
        val header = propertyRules.createSubscriptionHeader(resource, mapOf(
            PropertyCommonHeaderKeys.COMMAND to MidiCISubscriptionCommand.START,
            PropertyCommonHeaderKeys.MUTUAL_ENCODING to mutualEncoding))
        val msg = Message.SubscribeProperty(Message.Common(muid, targetMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, device.config.group),
            messenger.requestIdSerial++, header, listOf())
        addPendingSubscription(msg.requestId, subscriptionId, resource)
        messenger.send(msg)
    }

    fun sendUnsubscribeProperty(propertyId: String) {
        val newRequestId = messenger.requestIdSerial++
        val header = propertyRules.createSubscriptionHeader(propertyId, mapOf(
            PropertyCommonHeaderKeys.COMMAND to MidiCISubscriptionCommand.END,
            PropertyCommonHeaderKeys.SUBSCRIBE_ID to subscriptions.firstOrNull { it.propertyId == propertyId}?.subscriptionId))
        val msg = Message.SubscribeProperty(Message.Common(muid, targetMUID, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, device.config.group),
            newRequestId, header, listOf())
        promoteSubscriptionAsUnsubscribing(propertyId, newRequestId)
        messenger.send(msg)
    }

    // client PE event receivers

    internal fun processPropertyCapabilitiesReply(msg: Message.PropertyGetCapabilitiesReply) {
        conn.maxSimultaneousPropertyRequests = msg.maxSimultaneousRequests

        // proceed to query resource list
        if (device.config.autoSendGetResourceList)
            propertyRules.requestPropertyList(msg.group)
    }

    internal fun processGetDataReply(msg: Message.GetPropertyDataReply) {
        val req = openRequests.firstOrNull { it.requestId == msg.requestId }
            ?: return
        openRequests.remove(req)
        val status = propertyRules.getHeaderFieldInteger(msg.header, PropertyCommonHeaderKeys.STATUS)
            ?: return
        if (status == PropertyExchangeStatus.OK) {
            val propertyId = propertyRules.getPropertyIdForHeader(req.header)
            properties.updateValue(propertyId, msg)
            propertyRules.propertyValueUpdated(propertyId, msg.body)
        }
    }

    internal fun processSubscribeProperty(msg: Message.SubscribeProperty) {
        val reply = when (propertyRules.getHeaderFieldString(msg.header, PropertyCommonHeaderKeys.COMMAND)) {
            MidiCISubscriptionCommand.END -> handleUnsubscriptionNotification(muid, msg)
            else -> updatePropertyBySubscribe(msg)
        }
        if (reply.second != null)
            messenger.send(reply.second!!)
        // If the update was NOTIFY, then it is supposed to send Get Data request.
        if (reply.first == MidiCISubscriptionCommand.NOTIFY)
            sendGetPropertyData(propertyRules.getPropertyIdForHeader(msg.header))
    }
}