package dev.atsushieno.ktmidi.umpdevice

import dev.atsushieno.ktmidi.FunctionBlockDirection
import dev.atsushieno.ktmidi.FunctionBlockMidi1Bandwidth
import dev.atsushieno.ktmidi.FunctionBlockUiHint
import kotlin.test.*

class UmpEndpointTest {
    @Test
    fun initialState() {
        val mediator = TestUmpMediator()
        val device1 = mediator.device1

        assertEquals(0, device1.config.deviceIdentity.manufacturer, "config.deviceIdentity.manufacturer")
        assertEquals("", device1.targetEndpoint.name, "target endpoint name")
        assertEquals("", device1.targetEndpoint.productInstanceId, "target productInstanceId")
    }

    @Test
    fun basicRun() {
        val mediator = TestUmpMediator()
        val device1 = mediator.device1
        val device2 = mediator.device2

        val fb2l1 = FunctionBlock(0, "fb2_1",
            FunctionBlockMidi1Bandwidth.NO_LIMITATION,
            FunctionBlockDirection.INPUT,
            FunctionBlockUiHint.RECEIVER)
        val fb2l2 = FunctionBlock(0, "fb2_2",
            FunctionBlockMidi1Bandwidth.UP_TO_31250BPS,
            FunctionBlockDirection.OUTPUT,
            FunctionBlockUiHint.SENDER,
            groupIndex = 1,
            groupCount = 2)
        assertEquals("fb2_1", fb2l1.name, "fb2l1.name")
        assertTrue(fb2l1.isActive, "fb2l1.isActive")
        assertEquals(1, fb2l1.groupCount, "fb2l1.groupCount")
        device2.config.functionBlocks.add(fb2l1)
        device2.config.functionBlocks.add(fb2l2)

        assertEquals("device2", device2.config.name, "local endpoint name")
        assertEquals("Device2", device2.config.productInstanceId, "local productInstanceId")
        device1.sendDiscovery()
        assertEquals("device2", device1.targetEndpoint.name, "target endpoint name")
        assertEquals("Device2", device1.targetEndpoint.productInstanceId, "target productInstanceId")
        assertEquals(2, device1.targetEndpoint.functionBlocks.size, "target fbs.size")

        val fb2r1 = device1.targetEndpoint.functionBlocks[0]
        assertEquals("fb2_1", fb2r1.name, "fb2r1.name")
        assertTrue(fb2r1.isActive, "fb2r1.isActive")
        assertEquals(1, fb2r1.groupCount, "fb2r1.groupCount")

        val fb2r2 = device1.targetEndpoint.functionBlocks[1]
        assertEquals("fb2_2", fb2r2.name, "fb2r2.name")
        assertEquals(1, fb2r2.groupIndex, "fb2r2.groupIndex")
        assertTrue(fb2r2.isActive, "fb2r2.isActive")
        assertEquals(2, fb2r2.groupCount, "fb2r2.groupCount")
    }
}
