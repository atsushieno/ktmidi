package dev.atsushieno.ktmidi.ci

import kotlin.test.*

class ProfileConfigurationHostFacadeTest {
    @Test
    fun configureProfiles() {
        val mediator = TestCIMediator()
        val device1 = mediator.device1
        val device2 = mediator.device2
        device1.sendDiscovery()
        val conn = device1.connections[device2.muid]
        assertNotNull(conn)

        assertEquals(0, conn.profileClient.profiles.profiles.size, "profiles.size before addProfile")

        // add profile locally.
        val localProfile = MidiCIProfile(
            MidiCIProfileId(listOf(1, 2, 3, 4, 5)),
            group = 0,
            address = 1,
            enabled = true,
            numChannelsRequested = 1
        )
        device2.profileHost.addProfile(localProfile)
        // the above should result in Profile Added Report notification.
        assertEquals(1, conn.profileClient.profiles.profiles.size, "profiles.size after addProfile")

        val remoteProfile = conn.profileClient.profiles.profiles.first()
        assertEquals(localProfile.profile, remoteProfile.profile, "localProfile == remoteProfile: profile")
        assertEquals(localProfile.address, remoteProfile.address, "localProfile == remoteProfile: address")
        assertEquals(true, localProfile.enabled, "localProfile.enabled")
        assertEquals(true, remoteProfile.enabled, "remoteProfile.enabled")
        assertEquals(0, remoteProfile.group, "remoteProfile.group")

        // disable the profile
        var profileEnabledRemotely = false
        var profileDisabledRemotely = false
        device2.profileHost.onProfileSet.add {
            if (it.enabled)
                profileEnabledRemotely = true
            else
                profileDisabledRemotely = true
        }
        device1.profileHost.onProfileSet.add { throw IllegalStateException("should not happen") }

        device2.profileHost.disableProfile(localProfile.group, localProfile.address, localProfile.profile)
        assertTrue(profileDisabledRemotely, "profileDisabledRemotely")
        assertFalse(profileEnabledRemotely, "profileEnabledRemotely")

        val localProfileUpdated = device2.profileHost.profiles.profiles.first()
        assertFalse(localProfileUpdated.enabled, "local profile is disabled")
    }

    @Test
    fun configureProfiles2() {
        // The primary purpose of this test is to verify that Reply To Profile Inquiry contains
        // correct numbers of the enabled profiles and the disabled profiles.
        val mediator = TestCIMediator()
        val device1 = mediator.device1
        var numEnabledProfiles = 0
        var numDisabledProfiles = 0
        device1.messageReceived.add { msg ->
            if (msg is Message.ProfileReply) {
                numEnabledProfiles = msg.enabledProfiles.size
                numDisabledProfiles = msg.disabledProfiles.size
            }
        }
        val device2 = mediator.device2

        // add profile locally, before device1 sends Discovery.
        val localProfile = MidiCIProfile(
            MidiCIProfileId(listOf(1, 2, 3, 4, 5)),
            group = 0,
            address = 1,
            enabled = true,
            numChannelsRequested = 1
        )
        val localProfile2 = MidiCIProfile(
            MidiCIProfileId(listOf(2, 3, 4, 5, 6)),
            group = 0,
            address = 1,
            enabled = true,
            numChannelsRequested = 1
        )
        val localProfile3 = MidiCIProfile(
            MidiCIProfileId(listOf(3, 4, 5, 6, 7)),
            group = 0,
            address = 1,
            enabled = false,
            numChannelsRequested = 1
        )
        device2.profileHost.addProfile(localProfile)
        device2.profileHost.addProfile(localProfile2)
        device2.profileHost.addProfile(localProfile3)

        device1.sendDiscovery()
        assertEquals(2, numEnabledProfiles, "numEnabledProfiles")
        assertEquals(1, numDisabledProfiles, "numDisabledProfiles")

        val profileDetailsData = listOf<Byte>(1,3,5,7,9)
        val detailsTarget: Byte = 0x40
        device2.profileHost.profileDetailsEntries.add(
            MidiCIProfileDetails(
                localProfile.profile,
                detailsTarget,
                profileDetailsData
            )
        )
        assertContentEquals(
            profileDetailsData,
            device2.profileHost.getProfileDetails(localProfile.profile, detailsTarget),
            "getLocalProfileDetails"
        )
        var detailsValidated = false
        device1.messageReceived.add { msg ->
            if (msg is Message.ProfileDetailsReply) {
                assertContentEquals(profileDetailsData, msg.data, "ProfileDetailsReply data")
                detailsValidated = true
            }
        }
        device1.requestProfileDetails(localProfile.address, device2.muid, localProfile.profile, detailsTarget)
        assertTrue(detailsValidated, "detailsValidated")

        // Profile Specific Data - we have no particular API to handle them, except for `messageReceived`.
        val profileSpecificData = listOf<Byte>(9,8,7,6,5)
        var specificDataValidated = false
        device2.messageReceived.add { msg ->
            if (msg is Message.ProfileSpecificData) {
                assertContentEquals(profileSpecificData, msg.data, "ProfileSpecificData")
                specificDataValidated = true
            }
        }
        device1.sendProfileSpecificData(localProfile.address, device2.muid, localProfile.profile, profileSpecificData)
        assertTrue(specificDataValidated, "specificDataValidated")
    }

    @Test
    fun configureProfiles3() {
        val mediator = TestCIMediator()
        // disable auto-send profile inquiry at discovery
        val device1 = mediator.device1.apply { config.autoSendProfileInquiry = false }
        val device2 = mediator.device2

        // add profile locally.
        val localProfile = MidiCIProfile(
            MidiCIProfileId(listOf(1, 2, 3, 4, 5)),
            group = 0,
            address = 1,
            enabled = true,
            numChannelsRequested = 1
        )
        device2.profileHost.addProfile(localProfile)

        device1.sendDiscovery()
        val conn = device1.connections[device2.muid]
        assertNotNull(conn)

        // it is 0 because we do not query profiles yet (as disabled explicitly).
        assertEquals(0, conn.profileClient.profiles.profiles.size, "profiles.size before requesting")

        // it should be handled synchronously
        device1.requestProfiles(0, MidiCIConstants.ADDRESS_FUNCTION_BLOCK, conn.targetMUID)
        // we should have received the result.
        assertEquals(1, conn.profileClient.profiles.profiles.size, "profiles.size after requesting")
    }
}
