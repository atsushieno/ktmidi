package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.json.Json
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
    var jsonSchema: Json.JsonValue? = null

    val profileClient = ProfileClientFacade(parent, this)

    val propertyClient = PropertyClientFacade(parent, this)
}

