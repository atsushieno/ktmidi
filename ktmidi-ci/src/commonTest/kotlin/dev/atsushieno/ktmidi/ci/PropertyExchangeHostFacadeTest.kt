package dev.atsushieno.ktmidi.ci

import dev.atsushieno.ktmidi.ci.json.Json
import dev.atsushieno.ktmidi.ci.propertycommonrules.CommonRulesPropertyMetadata
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PropertyExchangeHostFacadeTest {
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
        host.setPropertyValue(id, bytes, false)

        device1.sendDiscovery()

        // test get property
        val conn = device1.connections[device2.muid]
        assertNotNull(conn)
        val client = conn.propertyClient

        client.sendGetPropertyData(id)
        assertContentEquals(bytes, client.properties.getProperty(id), "getProperty")

        // test set property
        client.sendSetPropertyData(id, bytes2)
        assertContentEquals(bytes2, host.properties.getProperty(id), "getProperty2")

        // subscribe -> update value -> notify
        client.sendSubscribeProperty(id)
        assertEquals(1, host.subscriptions.size, "subscriptions.size after subscription")
        host.setPropertyValue(id, bytes, false)
        // it should be reflected on the client side
        assertContentEquals(bytes, client.properties.getProperty(id), "getProperty at client after subscribed property update")
        client.sendUnsubscribeProperty(id)
        assertEquals(0, host.subscriptions.size, "subscriptions.size after unsubscription")

        // subscribe again, but this time unsubscribe from host
        client.sendSubscribeProperty(id)
        assertEquals(1, host.subscriptions.size, "subscriptions.size after subscription, 2nd")
        val sub = host.subscriptions.first()
        host.shutdownSubscription(sub.muid, sub.resource)
        assertEquals(0, client.subscriptions.size, "client subscriptions.size after unsubscription by host")
        assertEquals(0, host.subscriptions.size, "host subscriptions.size after unsubscription by host")
    }
}
