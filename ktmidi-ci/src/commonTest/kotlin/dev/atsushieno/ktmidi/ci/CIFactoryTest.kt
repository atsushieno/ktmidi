package dev.atsushieno.ktmidi.ci

import kotlin.test.Test
import kotlin.test.assertEquals

class CIFactoryTest {

    @Test
    fun testDiscoveryMessages() {
        val all_supported = (MidiCISupportedCategories.THREE_P)

        // Service Discovery (Inquiry)
        val expected1 = mutableListOf<Byte>(
            0x7E, 0x7F, 0x0D, 0x70, 1,
            0x10, 0x10, 0x10, 0x10, 0x7F, 0x7F, 0x7F, 0x7F,
            0x56, 0x34, 0x12, 0x57, 0x13, 0x68, 0x24,
            // LAMESPEC: Software Revision Level does not mention in which endianness this field is stored.
            0x7F, 0x5F, 0x3F, 0x1F,
            0b00011100,
            0x00, 0x02, 0, 0, 0
        )
        val actual1 = MutableList<Byte>(30) { 0 }
        CIFactory.midiCIDiscovery(
            actual1, 1, 0x10101010,
            0x123456, 0x1357, 0x2468, 0x1F3F5F7F,
            all_supported,
            512,
            0
        )
        assertEquals(expected1, actual1)

        // Service Discovery Reply
        val actual2 = MutableList<Byte>(31) { 0 }
        CIFactory.midiCIDiscoveryReply(
            actual2, 1, 0x10101010, 0x20202020,
            0x123456, 0x1357, 0x2468, 0x1F3F5F7F,
            all_supported,
            512,
            0,
            0
        )
        assertEquals(0x71, actual2[3])
        for (i in 9..12)
            assertEquals(0x20, actual2[i]) // destination ID is not 7F7F7F7F.

        // Invalidate MUID
        val expected3 = mutableListOf<Byte>(
            0x7E, 0x7F, 0x0D, 0x7E, 1,
            0x10, 0x10, 0x10, 0x10, 0x7F, 0x7F, 0x7F, 0x7F, 0x20, 0x20, 0x20, 0x20
        )
        val actual3 = MutableList<Byte>(17) { 0 }
        CIFactory.midiCIInvalidateMuid(actual3, 1, 0x10101010, 0x20202020)
        assertEquals(expected3, actual3)

        // NAK
        val expected4 = mutableListOf<Byte>(
            0x7E, 5, 0x0D, 0x7F, 1,
            0x10, 0x10, 0x10, 0x10, 0x20, 0x20, 0x20, 0x20
        )
        val actual4 = MutableList<Byte>(13) { 0 }
        CIFactory.midiCIDiscoveryNak(actual4, 5, 1, 0x10101010, 0x20202020)
        assertEquals(expected4, actual4)
    }

    @Test
    fun testProfileConfigurationMessages() {
        // Profile Inquiry
        val expected1 = mutableListOf<Byte>(
            0x7E, 5, 0x0D, 0x20, 2,
            0x10, 0x10, 0x10, 0x10, 0x20, 0x20, 0x20, 0x20
        )
        val actual1 = MutableList<Byte>(13) { 0 }
        CIFactory.midiCIProfileInquiry(actual1, 5, 0x10101010, 0x20202020)
        assertEquals(expected1, actual1)

        // Profile Inquiry Reply
        val b7E: Byte = 0x7E
        val profiles1 = mutableListOf(MidiCIProfileId(b7E, 2, 3, 4, 5), MidiCIProfileId(b7E, 7, 8, 9, 10))
        val profiles2 = mutableListOf(MidiCIProfileId(b7E, 12, 13, 14, 15), MidiCIProfileId(b7E, 17, 18, 19, 20))
        val expected2 = mutableListOf(
            0x7E, 5, 0x0D, 0x21, 2,
            0x10, 0x10, 0x10, 0x10, 0x20, 0x20, 0x20, 0x20,
            2,
            0,
            b7E, 2, 3, 4, 5,
            b7E, 7, 8, 9, 10,
            2,
            0,
            b7E, 12, 13, 14, 15,
            b7E, 17, 18, 19, 20
        )
        val actual2 = MutableList<Byte>(37) { 0 }
        CIFactory.midiCIProfileInquiryReply(
            actual2, 5, 0x10101010, 0x20202020, profiles1, profiles2)
        assertEquals(expected2, actual2)

        // Set Profile On
        val expected3 = mutableListOf<Byte>(
            0x7E, 5, 0x0D, 0x22, 2,
            0x10, 0x10, 0x10, 0x10, 0x20, 0x20, 0x20, 0x20,
            0x7E, 2, 3, 4, 5, 1, 0
        )
        val actual3 = MutableList<Byte>(20) { 0 }
        CIFactory.midiCIProfileSet(
            actual3, 5, true, 0x10101010, 0x20202020,
            profiles1[0], 1
        )
        assertEquals(expected3, actual3)
        assertEquals(1, actual3[18])

        // Set Profile Off
        val actual4 = MutableList<Byte>(20) { 0 }
        CIFactory.midiCIProfileSet(
            actual4, 5, false, 0x10101010, 0x20202020,
            profiles1[0], 1
        )
        assertEquals(0x23, actual4[3])

        // Profile Enabled Report
        val expected5 = mutableListOf<Byte>(
            0x7E, 5, 0x0D, 0x24, 2,
            0x10, 0x10, 0x10, 0x10, 0x7F, 0x7F, 0x7F, 0x7F,
            0x7E, 2, 3, 4, 5, 0, 0
        )
        val actual5 = MutableList<Byte>(20) { 0 }
        CIFactory.midiCIProfileReport(
            actual5, 5, true, 0x10101010,
            profiles1[0]
        )
        assertEquals(expected5, actual5)

        // Profile Disabled Report
        val actual6 = MutableList<Byte>(20) { 0 }
        CIFactory.midiCIProfileReport(
            actual6, 5, false, 0x10101010,
            profiles1[0]
        )
        assertEquals(0x25, actual6[3])

        // Profile Specific Data
        val expected7 = mutableListOf<Byte>(
            0x7E, 5, 0x0D, 0x2F, 2,
            0x10, 0x10, 0x10, 0x10, 0x20, 0x20, 0x20, 0x20,
            0x7E, 2, 3, 4, 5,
            8, 0, 0, 0,
            8, 7, 6, 5, 4, 3, 2, 1
        )
        val actual7 = MutableList<Byte>(30) { 0 }
        val data = mutableListOf<Byte>(8, 7, 6, 5, 4, 3, 2, 1)
        CIFactory.midiCIProfileSpecificData(
            actual7, 5, 0x10101010, 0x20202020,
            profiles1[0], 8, data
        )
        assertEquals(expected7, actual7)
    }

    @Test
    fun testPropertyExchangeMessages() {
        val header = mutableListOf<Byte>(11, 22, 33, 44)
        val data = mutableListOf<Byte>(55, 66, 77, 88, 99)

        // Property Inquiry
        val expected1 = mutableListOf<Byte>(
            0x7E, 5, 0x0D, 0x30, 2,
            0x10, 0x10, 0x10, 0x10, 0x20, 0x20, 0x20, 0x20,
            16, 0, 0
        )
        val actual1 = MutableList<Byte>(16) { 0 }
        CIFactory.midiCIPropertyGetCapabilities(actual1, 5, false, 0x10101010, 0x20202020, 16)
        assertEquals(expected1, actual1)

        // Property Inquiry Reply
        val actual2 = MutableList<Byte>(16) { 0 }
        CIFactory.midiCIPropertyGetCapabilities(actual2, 5, true, 0x10101010, 0x20202020, 16)
        assertEquals(0x31, actual2[3])

        // Get Property Data
        val actual5 = MutableList<Byte>(31) { 0 }
        CIFactory.midiCIPropertyCommon(
            actual5, 5, CISubId2.PROPERTY_GET_DATA_INQUIRY,
            0x10101010, 0x20202020,
            2, header, 3, 1, data
        )
        assertEquals(0x34, actual5[3])

        // Reply to Get Property Data
        val actual6 = MutableList<Byte>(31) { 0 }
        CIFactory.midiCIPropertyCommon(
            actual6, 5, CISubId2.PROPERTY_GET_DATA_REPLY,
            0x10101010, 0x20202020,
            2, header, 3, 1, data
        )
        assertEquals(0x35, actual6[3])

        // Set Property Data
        val actual7 = MutableList<Byte>(31) { 0 }
        CIFactory.midiCIPropertyCommon(
            actual7, 5, CISubId2.PROPERTY_SET_DATA_INQUIRY,
            0x10101010, 0x20202020,
            2, header, 3, 1, data
        )
        assertEquals(0x36, actual7[3])

        // Reply to Set Property Data
        val actual8 = MutableList<Byte>(31) { 0 }
        CIFactory.midiCIPropertyCommon(
            actual8, 5, CISubId2.PROPERTY_SET_DATA_REPLY,
            0x10101010, 0x20202020,
            2, header, 3, 1, data
        )
        assertEquals(0x37, actual8[3])

        // Subscription
        val actual9 = MutableList<Byte>(31) { 0 }
        CIFactory.midiCIPropertyCommon(
            actual9, 5, CISubId2.PROPERTY_SUBSCRIBE,
            0x10101010, 0x20202020,
            2, header, 3, 1, data
        )
        assertEquals(0x38, actual9[3])

        // Reply to Subscription
        val actual10 = MutableList<Byte>(31) { 0 }
        CIFactory.midiCIPropertyCommon(
            actual10, 5, CISubId2.PROPERTY_SUBSCRIBE_REPLY,
            0x10101010, 0x20202020,
            2, header, 3, 1, data
        )
        assertEquals(0x39, actual10[3])

        // Notify
        val actual11 = MutableList<Byte>(31) { 0 }
        CIFactory.midiCIPropertyCommon(
            actual11, 5, CISubId2.PROPERTY_NOTIFY,
            0x10101010, 0x20202020,
            2, header, 3, 1, data
        )
        assertEquals(0x3F, actual11[3])
    }
}
