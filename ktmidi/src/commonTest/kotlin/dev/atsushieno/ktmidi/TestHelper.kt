package dev.atsushieno.ktmidi

class TestHelper {
    companion object {
        @OptIn(ExperimentalUnsignedTypes::class)
        fun getMidiMusic(): Midi1Music {
            var music = Midi1Music()
            music.deltaTimeSpec = 192
            var track = Midi1Track()
            var ch = 1
            track.events.add(Midi1Event(188, Midi1SimpleMessage(MidiChannelStatus.PROGRAM + ch, 1, 0)))
            for (i in 0 until 100) {
                track.events.add(
                    Midi1Event(4, Midi1SimpleMessage(MidiChannelStatus.NOTE_ON + ch, 60, 120))
                )
                track.events.add(
                    Midi1Event(44, Midi1SimpleMessage(MidiChannelStatus.NOTE_OFF + ch, 60, 0))
                )
            }

            music.tracks.add(track)
            return music
        }

        fun getMidiMusic(resourceId: String): Midi1Music {
            TODO()
            //using (var stream = typeof (TestHelper).Assembly.GetManifestResourceStream (resourceId))
            //return MidiMusic.Read (stream)
        }

        suspend fun getMidiPlayer(
            timeManager: MidiPlayerTimer?,
            midiMusic: Midi1Music?,
            midiAccess: MidiAccess? = null
        ): Midi1Player {
            val access = midiAccess ?: emptyMidiAccess
            val music = midiMusic ?: getMidiMusic()
            val tm = timeManager ?: VirtualMidiPlayerTimer()
            return Midi1Player.create(music, access, tm)
        }

        suspend fun getMidiPlayer(
            timeManager: MidiPlayerTimer? = null,
            midiAccess: MidiAccess? = null,
            resourceId: String? = null
        ): Midi1Player {
            val access = midiAccess ?: emptyMidiAccess
            val music = if (resourceId != null) getMidiMusic(resourceId) else getMidiMusic()
            val tm = timeManager ?: VirtualMidiPlayerTimer()
            return Midi1Player.create(music, access, tm)
        }
    }
}
