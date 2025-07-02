package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.json.Json
import dev.atsushieno.ktmidi.ci.propertycommonrules.CommonRulesPropertyMetadata
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PropertyFacadesTest {
    @Test
    fun propertyExchange1() {
        val mediator = TestCIMediator()
        val device1 = mediator.device1
        val device2 = mediator.device2
        val host = device2.propertyHost

        val id = "X-01"
        val prop1 = CommonRulesPropertyMetadata(id).apply {
            canSet = "partial"
            canSubscribe = true
        }
        host.addProperty(prop1)
        val bytes = Json.serialize(Json.JsonValue("FOO")).toASCIIByteArray().toList()
        val bytes2 = Json.serialize(Json.JsonValue("BAR")).toASCIIByteArray().toList()
        host.setPropertyValue(id, null, bytes, false)

        device1.sendDiscovery()

        // test get property
        val conn = device1.connections[device2.muid]
        assertNotNull(conn)
        // It should contain `DeviceInfo`, `ChannelList`, `JSONSchema`, and `X-01` (may increase along with other predefined resources)
        assertEquals(4, conn.propertyClient.properties.getMetadataList()!!.size, "client MetadataList size")

        val client = conn.propertyClient

        client.sendGetPropertyData(id)
        assertContentEquals(bytes, client.properties.getProperty(id), "client.getProperty")

        // test set property
        client.sendSetPropertyData(id, null, bytes2)
        assertContentEquals(bytes2, host.properties.getProperty(id), "host.getProperty")
        assertContentEquals(bytes2, host.properties.values.first { it.id == id }.body, "host.properties.values entry")
        assertContentEquals(bytes2, client.properties.getProperty(id), "client.getProperty2")

        // subscribe -> update value -> notify
        client.sendSubscribeProperty(id)
        assertEquals(1, host.subscriptions.items.size, "subscriptions.size after subscription")
        host.setPropertyValue(id, null, bytes, false)
        // it should be reflected on the client side
        assertContentEquals(bytes, client.properties.getProperty(id), "getProperty at client after subscribed property update")
        client.sendUnsubscribeProperty(id)
        assertEquals(0, host.subscriptions.items.size, "subscriptions.size after unsubscription")

        // subscribe again, but this time unsubscribe from host
        client.sendSubscribeProperty(id)
        assertEquals(1, host.subscriptions.items.size, "subscriptions.size after subscription, 2nd")
        val sub = host.subscriptions.items.first()
        host.shutdownSubscription(sub.muid, sub.resource)
        assertEquals(0, client.subscriptions.size, "client subscriptions.size after unsubscription by host")
        assertEquals(0, host.subscriptions.items.size, "host subscriptions.size after unsubscription by host")
    }

    @Test
    fun propertyExchange2() {
        val mediator = TestCIMediator()
        val device1 = mediator.device1
        val device2 = mediator.device2

        device2.config.channelList.channels.add(MidiCIChannel("TestChannel1", 1))

        device1.sendDiscovery()
        val conn = device1.connections[device2.muid]!!
        val channelList = conn.propertyClient.properties.getProperty("ChannelList")
        assertNotNull(channelList, "ChannelList should not be null")
    }
}
