package dev.atsushieno.ktmidi.ci

import kotlin.test.*

class MidiCIDeviceTest {
    @Test
    fun initialState() {
        val mediator = TestCIMediator()
        val device1 = mediator.device1

        assertEquals(0, device1.deviceInfo.manufacturerId)
        assertEquals(19474, device1.muid)
    }

    @Test
    fun basicRun() {
        val mediator = TestCIMediator()
        val device1 = mediator.device1
        val device2 = mediator.device2
        device1.sendDiscovery()
        assertEquals(1, device1.connections.size, "connections.size")
        val conn = device1.connections[device2.muid]
        assertNotNull(conn, "conn")
        assertEquals(device2.deviceInfo.manufacturerId, conn.deviceInfo.manufacturerId, "conn.device.manufacturer")
    }
}

