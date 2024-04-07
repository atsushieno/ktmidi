package dev.atsushieno.ktmidi.umpdevice

import dev.atsushieno.ktmidi.Ump

class TestUmpMediator {
    private val device1Sender: (Sequence<Ump>)->Unit = { data: Sequence<Ump> -> device2.processInput(data) }
    private val device2Sender: (Sequence<Ump>)->Unit = { data: Sequence<Ump> -> device1.processInput(data) }

    val device1 = UmpEndpoint(
        UmpEndpointConfiguration("device1", "Device1", UmpDeviceIdentity.empty,
            UmpEndpointConfiguration.UmpStreamConfiguration(true, true, false, false),
            true, mutableListOf()))
        .apply {
            outputSenders.add(device1Sender)
        }
    val device2 = UmpEndpoint(
        UmpEndpointConfiguration("device2", "Device2", UmpDeviceIdentity.empty,
            UmpEndpointConfiguration.UmpStreamConfiguration(true, true, false, false),
            true, mutableListOf()))
        .apply {
            outputSenders.add(device2Sender)
        }
}
