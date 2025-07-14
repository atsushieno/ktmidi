package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.json.Json
import dev.atsushieno.ktmidi.ci.propertycommonrules.channelList
import dev.atsushieno.ktmidi.ci.propertycommonrules.deviceInfo
import dev.atsushieno.ktmidi.ci.propertycommonrules.jsonSchema

enum class SubscriptionActionState {
    Subscribing,
    Subscribed,
    Unsubscribing,
    Unsubscribed
}
data class ClientSubscription(var pendingRequestId: Byte?, var subscriptionId: String?, val propertyId: String, var state: SubscriptionActionState)

class ClientConnection(
    parent: MidiCIDevice,
    val targetMUID: Int,
    private val deviceDetails: DeviceDetails,
    var maxSimultaneousPropertyRequests: Byte = 0,
    var productInstanceId: String = "",
) {
    val deviceInfo: MidiCIDeviceInfo
        get() = propertyClient.properties.deviceInfo ?: MidiCIDeviceInfo(
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
    val channelList: MidiCIChannelList
        get() = propertyClient.properties.channelList ?: MidiCIChannelList()
    val jsonSchema: Json.JsonValue
        get() = propertyClient.properties.jsonSchema ?: Json.JsonValue(mapOf())

    val profileClient = ProfileClientFacade(parent, this)

    val propertyClient = PropertyClientFacade(parent, this)
}

