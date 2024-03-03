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

        val id = "X-01"
        val prop1 = CommonRulesPropertyMetadata(id).apply {
            canSet = "partial"
            canSubscribe = true
        }
        device2.propertyHost.addProperty(prop1)
        val bytes = Json.serialize(Json.JsonValue("FOO")).toASCIIByteArray().toList()
        val bytes2 = Json.serialize(Json.JsonValue("BAR")).toASCIIByteArray().toList()
        device2.propertyHost.setPropertyValue(id, bytes, false)

        device1.sendDiscovery()

        // test get property
        val conn = device1.connections[device2.muid]
        assertNotNull(conn)
        conn.sendGetPropertyData(id)
        assertContentEquals(bytes, conn.properties.getProperty(id), "getProperty")

        // test set property
        conn.sendSetPropertyData(id, bytes2)
        assertContentEquals(bytes2, device2.propertyHost.properties.getProperty(id), "getProperty2")

        // subscribe -> update value -> notify
        conn.sendSubscribeProperty(id)
        assertEquals(1, device2.propertyHost.subscriptions.size, "subscriptions.size after subscription")
        device2.propertyHost.setPropertyValue(id, bytes, false)
        // it should be reflected on the client side
        assertContentEquals(bytes, conn.properties.getProperty(id), "getProperty at client after subscribed property update")
        conn.sendUnsubscribeProperty(id)
        assertEquals(0, device2.propertyHost.subscriptions.size, "subscriptions.size after unsubscription")

        // subscribe again, but this time unsubscribe from host
        conn.sendSubscribeProperty(id)
        assertEquals(1, device2.propertyHost.subscriptions.size, "subscriptions.size after subscription, 2nd")
        val sub = device2.propertyHost.subscriptions.first()
        device2.propertyHost.shutdownSubscription(sub.muid, sub.resource)
        assertEquals(0, conn.subscriptions.size, "client subscriptions.size after unsubscription by host")
        assertEquals(0, device2.propertyHost.subscriptions.size, "host subscriptions.size after unsubscription by host")
    }
}
