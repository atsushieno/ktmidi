package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.ci.*
import dev.atsushieno.ktmidi.citool.view.ViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random

class CIInitiatorModel(private val outputSender: (ciBytes: List<Byte>) -> Unit) {
    fun processCIMessage(data: List<Byte>) {
        AppModel.log("[Initiator received SYSEX] " + data.joinToString { it.toString(16) })
        initiator.processInput(data)
    }

    var device: MidiCIDeviceInfo
        get() = AppModel.savedSettings.initiator.device
        set(value) {
            AppModel.savedSettings.initiator.device = value
            initiator.device = value
        }

    val initiator by lazy {
        MidiCIInitiator(AppModel.savedSettings.initiator) { data ->
            AppModel.log("[Initiator sent SYSEX] " + data.joinToString { it.toString(16) } + "\n")
            outputSender(data)
        }.apply {
            config.productInstanceId = "ktmidi-ci" + (Random.nextInt() % 65536)
        }
    }

    fun sendDiscovery() {
        val msg = initiator.createDiscoveryInquiry()
        initiator.logger.logMessage(msg)
        initiator.sendDiscovery(msg)
    }

    // FIXME: we need to make MidiCIInitiator EndpointInquiry hook-able.
    fun sendEndpointMessage(targetMUID: Int) {
        val msg = initiator.createEndpointMessage(targetMUID)
        initiator.logger.logMessage(msg)
        initiator.sendEndpointMessage(msg)
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
            initiator.logger.logMessage(msg)
            initiator.setProfileOn(msg)
        } else {
            val msg = Message.SetProfileOff(address, initiator.muid, destinationMUID, profile)
            initiator.logger.logMessage(msg)
            initiator.setProfileOff(msg)
        }
    }

    fun sendProfileDetailsInquiry(address: Byte, muid: Int, profile: MidiCIProfileId, target: Byte) {
        initiator.sendProfileDetailsInquiry(address, muid, profile, target)
    }

    fun sendGetPropertyDataRequest(destinationMUID: Int, resource: String) {
        initiator.sendGetPropertyData(destinationMUID, resource)
    }
    fun sendSetPropertyDataRequest(destinationMUID: Int, resource: String, data: List<Byte>, isPartial: Boolean) {
        initiator.sendSetPropertyData(destinationMUID, resource, data, isPartial)
    }
    fun sendSubscribeProperty(destinationMUID: Int, resource: String) {
        initiator.sendSubscribeProperty(destinationMUID, resource)
    }

    init {
        initiator.logger.logEventReceived.add {
            AppModel.log(it)
        }
    }
}

