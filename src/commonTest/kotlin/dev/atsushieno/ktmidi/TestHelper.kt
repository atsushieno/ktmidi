package dev.atsushieno.ktmidi

class TestHelper {
    companion object {
        @OptIn(ExperimentalUnsignedTypes::class)
        fun getMidiMusic(): MidiMusic {
            var music = MidiMusic()
            music.deltaTimeSpec = 192
            var track = MidiTrack()
            var ch = 1
            track.messages.add(MidiMessage(188, MidiEvent(MidiEventType.PROGRAM + ch, 1, 0, null, 0, 0)))
            for (i in 0 until 100) {
                track.messages.add(
                    MidiMessage(4, MidiEvent(MidiEventType.NOTE_ON + ch, 60, 120, null, 0, 0))
                )
                track.messages.add(
                    MidiMessage(44, MidiEvent(MidiEventType.NOTE_OFF + ch, 60, 0, null, 0, 0))
                )
            }

            music.tracks.add(track)
            return music
        }

        fun getMidiMusic(resourceId: String): MidiMusic {
            TODO()
            //using (var stream = typeof (TestHelper).Assembly.GetManifestResourceStream (resourceId))
            //return MidiMusic.Read (stream)
        }

        fun getMidiPlayer(
            timeManager: MidiPlayerTimer?,
            midiMusic: MidiMusic?,
            midiAccess: MidiAccess? = null
        ): MidiPlayer {
            val access = midiAccess ?: MidiAccessManager.empty
            val music = midiMusic ?: getMidiMusic()
            val tm = timeManager ?: VirtualMidiPlayerTimer()
            return MidiPlayer(music, access, tm)
        }

        fun getMidiPlayer(
            timeManager: MidiPlayerTimer? = null,
            midiAccess: MidiAccess? = null,
            resourceId: String? = null
        ): MidiPlayer {
            val access = midiAccess ?: MidiAccessManager.empty
            val music = if (resourceId != null) getMidiMusic(resourceId) else getMidiMusic()
            val tm = timeManager ?: VirtualMidiPlayerTimer()
            return MidiPlayer(music, access, tm)
        }
    }
}
