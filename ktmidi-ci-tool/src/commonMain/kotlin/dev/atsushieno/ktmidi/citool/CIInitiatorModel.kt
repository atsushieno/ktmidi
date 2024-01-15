package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.citool.view.ViewModel
import kotlin.random.Random

class CIInitiatorModel(private val outputSender: (ciBytes: List<Byte>) -> Unit) {
    val initiator by lazy {
        MidiCIInitiator(AppModel.muid, AppModel.initiator) { data ->
            AppModel.log("[Initiator sent SYSEX] " + data.joinToString { it.toString(16) },
                MessageDirection.Out)
            outputSender(data)
        }.apply {
            config.productInstanceId = "ktmidi-ci" + (Random.nextInt() % 65536)
        }
    }

    fun processCIMessage(data: List<Byte>) {
        AppModel.log("[Initiator received SYSEX] " + data.joinToString { it.toString(16) },
            MessageDirection.In)
        initiator.processInput(data)
    }

    /*
    var device: MidiCIDeviceInfo
        get() = AppModel.savedSettings.initiator.common.device
        set(value) {
            AppModel.savedSettings.initiator.common.device = value
            initiator.device = value
        }
    */

    fun sendDiscovery() {
        initiator.sendDiscovery()
    }

    // FIXME: we need to make MidiCIInitiator EndpointInquiry hook-able.
    fun sendEndpointMessage(targetMUID: Int) {
        initiator.sendEndpointMessage(targetMUID)
    }

    fun setProfile(destinationMUID: Int, address: Byte, profile: MidiCIProfileId, nextEnabled: Boolean) {
        if (nextEnabled) {
            // FIXME: maybe we should pass number of channels somehow?
            val msg = Message.SetProfileOn(address, initiator.muid, destinationMUID, profile,
                // NOTE: juce_midi_ci has a bug that it expects 1 for 7E and 7F, whereas MIDI-CI v1.2 states:
                //   "When the Profile Destination field is set to address 0x7E or 0x7F, the number of Channels is determined
                //    by the width of the Group or Function Block. Set the Number of Channels Requested field to a value of 0x0000."
                if (address < 0x10 || ViewModel.settings.workaroundJUCEProfileNumChannelsIssue.value) 1
                else 0)
            initiator.setProfileOn(msg)
        } else {
            val msg = Message.SetProfileOff(address, initiator.muid, destinationMUID, profile)
            initiator.setProfileOff(msg)
        }
    }

    fun sendProfileDetailsInquiry(address: Byte, muid: Int, profile: MidiCIProfileId, target: Byte) {
        initiator.requestProfileDetails(address, muid, profile, target)
    }

    fun sendGetPropertyDataRequest(destinationMUID: Int, resource: String, encoding: String?) {
        initiator.sendGetPropertyData(destinationMUID, resource, encoding)
    }
    fun sendSetPropertyDataRequest(destinationMUID: Int, resource: String, data: List<Byte>, encoding: String?, isPartial: Boolean) {
        initiator.sendSetPropertyData(destinationMUID, resource, data, encoding, isPartial)
    }
    fun sendSubscribeProperty(destinationMUID: Int, resource: String, mutualEncoding: String?) {
        initiator.sendSubscribeProperty(destinationMUID, resource, mutualEncoding)
    }

    init {
        initiator.logger.logEventReceived.add { msg, direction ->
            AppModel.log(msg, direction)
        }
    }
}

