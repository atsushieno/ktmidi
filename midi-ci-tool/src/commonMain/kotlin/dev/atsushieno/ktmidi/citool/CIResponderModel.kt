package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.citool.view.MidiCIProfileState
import dev.atsushieno.ktmidi.citool.view.ViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random

class CIResponderModel(private val outputSender: (ciBytes: List<Byte>) -> Unit) {
    fun processCIMessage(data: List<Byte>) {
        val time = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        ViewModel.log("[${time.time.toString().substring(0, 8)}] SYSEX: " + data.joinToString { it.toString(16) } + "\n")
        responder.processInput(data)
    }

    private val logger = Logger()

    class Events {
        val discoveryReceived = mutableListOf<(msg: Message.DiscoveryInquiry) -> Unit>()
        val endpointInquiryReceived = mutableListOf<(msg: Message.EndpointInquiry) -> Unit>()
        val profileInquiryReceived = mutableListOf<(msg: Message.ProfileInquiry) -> Unit>()
        val setProfileOnReceived = mutableListOf<(profile: Message.SetProfileOn) -> Unit>()
        val setProfileOffReceived = mutableListOf<(profile: Message.SetProfileOff) -> Unit>()
        val unknownMessageReceived = mutableListOf<(data: List<Byte>) -> Unit>()
        val propertyCapabilityInquiryReceived = mutableListOf<(Message.PropertyGetCapabilities) -> Unit>()
        val getPropertyDataReceived = mutableListOf<(msg: Message.GetPropertyData) -> Unit>()
        val setPropertyDataReceived = mutableListOf<(msg: Message.SetPropertyData) -> Unit>()
        val subscribePropertyReceived = mutableListOf<(msg: Message.SubscribeProperty) -> Unit>()
    }

    private val events = Events()

    private val device = MidiCIDeviceInfo(0x123456,0x1234,0x5678,0x00000001,
        "atsushieno", "KtMidi", "KtMidi-CI-Tool Responder", "0.1")

    fun updateProfileTarget(profileState: MidiCIProfileState, address: Byte, enabled: Boolean, numChannelsRequested: Short) {
        val profile = responder.profiles.profiles.first { it.address == profileState.address.value && it.profile == profileState.profile }
        responder.profiles.update(profile, enabled, address, numChannelsRequested)
    }

    fun addProfile(profile: MidiCIProfile) {
        responder.profiles.add(profile)
        responder.sendProfileAddedReport(profile)
    }

    fun removeProfile(address: Byte, profile: MidiCIProfileId) {
        responder.profiles.removeProfileTarget(address, profile)
    }

    fun updateProfileName(oldProfile: MidiCIProfileId, newProfile: MidiCIProfileId) {
        val removed = responder.profiles.profiles.filter { it.profile == oldProfile }
        val added = removed.map { MidiCIProfile(newProfile, it.address, it.enabled) }
        removed.forEach { removeProfile(it.address, it.profile) }
        added.forEach { addProfile(it) }
    }

    val responder = MidiCIResponder(device, { data ->
        ViewModel.log("[R] REPLY SYSEX: " + data.joinToString { it.toString(16) })
        outputSender(data)
    }).apply {
        productInstanceId = "ktmidi-ci" + (Random.nextInt() % 65536)
        maxSimultaneousPropertyRequests = 127

        // Unknown
        processUnknownCIMessage = { data ->
            logger.nak(data)
            events.unknownMessageReceived.forEach { it(data) }
            sendNakForUnknownCIMessage(data)
        }
        // Discovery
        processDiscovery = { msg ->
            logger.discovery(msg)
            events.discoveryReceived.forEach { it(msg) }
            val reply = getDiscoveryReplyForInquiry(msg)
            logger.discoveryReply(reply)
            sendDiscoveryReply(reply)
        }
        processEndpointMessage = { msg ->
            logger.endpointInquiry(msg)
            events.endpointInquiryReceived.forEach { it(msg) }
            val reply = getEndpointReplyForInquiry(msg)
            logger.endpointReply(reply)
            sendEndpointReply(reply)
        }
        // Profile
        processProfileInquiry = { msg ->
            logger.profileInquiry(msg)
            events.profileInquiryReceived.forEach { it(msg) }
            getProfileRepliesForInquiry(msg).forEach { reply ->
                logger.profileReply(reply)
                sendProfileReply(reply)
            }
        }
        onProfileSet.add { profile, numChannelsRequested ->
            profiles.profileEnabledChanged.forEach { it(profile, numChannelsRequested) }
        }
        processSetProfileOn = { msg ->
            logger.setProfileOn(msg)
            events.setProfileOnReceived.forEach { it(msg) }
            defaultProcessSetProfileOn(msg)
        }
        processSetProfileOff = { msg ->
            logger.setProfileOff(msg)
            events.setProfileOffReceived.forEach { it(msg) }
            defaultProcessSetProfileOff(msg)
        }
        // PE
        processPropertyCapabilitiesInquiry = { msg ->
            logger.propertyGetCapabilitiesInquiry(msg)
            events.propertyCapabilityInquiryReceived.forEach { it(msg) }
            val reply = getPropertyCapabilitiesReplyFor(msg)
            logger.propertyGetCapabilitiesReply(reply)
            sendPropertyCapabilitiesReply(reply)
        }
        processGetPropertyData = { msg ->
            logger.getPropertyData(msg)
            events.getPropertyDataReceived.forEach { it(msg) }
            val reply = propertyService.getPropertyData(msg)
            logger.getPropertyDataReply(reply)
            sendPropertyGetDataReply(reply)
        }
        processSetPropertyData = { msg ->
            logger.setPropertyData(msg)
            events.setPropertyDataReceived.forEach { it(msg) }
            val reply = propertyService.setPropertyData(msg)
            sendPropertySetDataReply(reply)
        }
        processSubscribeProperty = { msg ->
            logger.subscribeProperty(msg)
            events.subscribePropertyReceived.forEach { it(msg) }
            val reply = propertyService.subscribeProperty(msg)
            logger.subscribePropertyReply(reply)
            sendPropertySubscribeReply(reply)
        }

        // FIXME: they are dummy items that should be removed.
        profiles.add(MidiCIProfile(MidiCIProfileId(0, 1, 2, 3, 4), 0x7E, true))
        profiles.add(MidiCIProfile(MidiCIProfileId(5, 6, 7, 8, 9), 0x7F, true))
        profiles.add(MidiCIProfile(DefaultControlChangesProfile.profileIdForPartial, 0, false))
        profiles.add(MidiCIProfile(DefaultControlChangesProfile.profileIdForPartial, 4, true))
    }
}
