package dev.atsushieno.ktmidi.ci

/**
 * This class is the entry point for all the profile client features for ClientConnection.
 */
class ProfileClientFacade(private val device: MidiCIDevice, private val conn: ClientConnection) {

    val profiles = ObservableProfileList(mutableListOf())

    fun setProfile(
        group: Byte,
        address: Byte,
        profile: MidiCIProfileId,
        enabled: Boolean,
        numChannelsRequested: Short
    ) {
        val common = Message.Common(device.muid, conn.targetMUID, address, group)
        if (enabled) {
            val msg = Message.SetProfileOn(
                common, profile,
                // NOTE: juce_midi_ci has a bug that it expects 1 for 7E and 7F, whereas MIDI-CI v1.2 states:
                //   "When the Profile Destination field is set to address 0x7E or 0x7F, the number of Channels is determined
                //    by the width of the Group or Function Block. Set the Number of Channels Requested field to a value of 0x0000."
                if (address < 0x10 || ImplementationSettings.workaroundJUCEProfileNumChannelsIssue) {
                    if (numChannelsRequested < 1) 1 else numChannelsRequested
                } else numChannelsRequested
            )
            device.messenger.send(msg)
        } else {
            val msg = Message.SetProfileOff(common, profile)
            device.messenger.send(msg)
        }
    }

    internal fun processProfileReply(msg: Message.ProfileReply) {
        msg.enabledProfiles.forEach {
            profiles.add(MidiCIProfile(it, msg.group, msg.address, true, if (msg.address >= 0x7E) 0 else 1))
        }
        msg.disabledProfiles.forEach {
            profiles.add(MidiCIProfile(it, msg.group, msg.address, false, if (msg.address >= 0x7E) 0 else 1))
        }
    }

    internal fun processProfileAddedReport(msg: Message.ProfileAdded) {
        profiles.add(MidiCIProfile(msg.profile, msg.group, msg.address, false, if (msg.address >= 0x7E) 0 else 1))
    }

    internal fun processProfileRemovedReport(msg: Message.ProfileRemoved) {
        profiles.remove(MidiCIProfile(msg.profile, msg.group, msg.address, false, 0))
    }

    internal  fun processProfileEnabledReport(msg: Message.ProfileEnabled) {
        profiles.setEnabled(true, msg.address, msg.profile, msg.numChannelsEnabled)
    }

    internal fun processProfileDisabledReport(msg: Message.ProfileDisabled) {
        profiles.setEnabled(false, msg.address, msg.profile, msg.numChannelsDisabled)
    }

    internal fun processProfileDetailsReply(msg: Message.ProfileDetailsReply) {
        // nothing to perform so far - use events if you need anything further
    }
}