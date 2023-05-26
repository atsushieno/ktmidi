package dev.atsushieno.ktmidi

private class EmptyClass {
    companion object {

        val INSTRUMENT_NAMES =
            arrayOf(
                "Acoustic Piano",
                "Bright Piano",
                "Electric Grand Piano",
                "Honky-tonk Piano",
                "Electric Piano",
                "Electric Piano 2",
                "Harpsichord",
                "Clavi",
                "Celesta",
                "Glockenspiel",
                "Musical Box",
                "Vibraphone",
                "Marimba",
                "Xylophone",
                "Tubular Bell",
                "Dulcimer",
                "Drawbar Organ",
                "Percussive Organ",
                "Rock Organ",
                "Church Organ",
                "Reed Organ",
                "Accordion",
                "Harmonica",
                "Tango Accordion",
                "Acoustic Guitar (nylon)",
                "Acoustic Guitar (steel)",
                "Electric Guitar (jazz)",
                "Electric Guitar (clean)",
                "Electric Guitar (muted)",
                "Overdriven Guitar",
                "Distortion Guitar",
                "Guitar Harmonics",
                "Acoustic Bass",
                "Electric Bass (finger)",
                "Electric Bass (pick)",
                "Fretless Bass",
                "Slap Bass 1",
                "Slap Bass 2",
                "Synth Bass 1",
                "Synth Bass 2",
                "Violin",
                "Viola",
                "Cello",
                "Double bass",
                "Tremelo Strings",
                "Pizzicato Strings",
                "Orchestral Harp",
                "Timpani",
                "String Ensemble 1",
                "String Ensemble 2",
                "Synth Strings 1",
                "Synth Strings 2",
                "Voice Aahs",
                "Voice Oohs",
                "Synth Voice",
                "Orchestra Hit",
                "Trumpet",
                "Trombone",
                "Tuba",
                "Muted Trumpet",
                "French Horn",
                "Brass Section",
                "Synth Brass 1",
                "Synth Brass 2",
                "Soprano Sax",
                "Alto Sax",
                "Tenor Sax",
                "Baritone Sax",
                "Oboe",
                "English Horn",
                "Bassoon",
                "Clarinet",
                "Piccolo",
                "Flute",
                "Recorder",
                "Pan Flute",
                "Brown Bottle",
                "Shakuhachi",
                "Whistle",
                "Ocarina",
                "Lead 1 (square)",
                "Lead 2 (sawtooth)",
                "Lead 3 (calliope)",
                "Lead 4 (chiff)",
                "Lead 5 (charang)",
                "Lead 6 (voice)",
                "Lead 7 (fifths)",
                "Lead 8 (bass + lead)",
                "Pad 1 (fantasia)",
                "Pad 2 (warm)",
                "Pad 3 (polysynth)",
                "Pad 4 (choir)",
                "Pad 5 (bowed)",
                "Pad 6 (metallic)",
                "Pad 7 (halo)",
                "Pad 8 (sweep)",
                "FX 1 (rain)",
                "FX 2 (soundtrack)",
                "FX 3 (crystal)",
                "FX 4 (atmosphere)",
                "FX 5 (brightness)",
                "FX 6 (goblins)",
                "FX 7 (echoes)",
                "FX 8 (sci-fi)",
                "Sitar",
                "Banjo",
                "Shamisen",
                "Koto",
                "Kalimba",
                "Bagpipe",
                "Fiddle",
                "Shanai",
                "Tinkle Bell",
                "Agogo",
                "Steel Drums",
                "Woodblock",
                "Taiko Drum",
                "Melodic Tom",
                "Synth Drum",
                "Reverse Cymbal",
                "Guitar Fret Noise",
                "Breath Noise",
                "Seashore",
                "Bird Tweet",
                "Telephone Ring",
                "Helicopter",
                "Applause",
                "Gunshot"
            )

        val DRUM_KITS_GM2 = arrayOf(
            "Standard Kit",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "Room Kit",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "Power Kit",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "Electronic Kit",
            "TR-808 Kit",
            "",
            "",
            "",
            "",
            "",
            "",
            "Jazz Kit",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "Brush Kit",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "Orchestra Kit",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "Sound FX Kit"
        )

        val INSTRUMENT_CATEGORIES = arrayOf(
            "Piano",
            "Chromatic Percussion",
            "Organ",
            "Guitar",
            "Bass",
            "Orchestra Solo",
            "Orchestra Ensemble",
            "Brass",
            "Reed",
            "Pipe",
            "Synth Lead",
            "Synth Pad",
            "Synth Sound FX",
            "Ethnic",
            "Percussive",
            "Sound Effect"
        )

        // Too lengthy to type "GeneralMidi.Instruments.AcousticGrandPiano" ?
        // Use "using static": using static GMInst = Commons.Music.Midi.GeneralMidi.Instruments;
        class Instruments {
            companion object {

                const val ACOUSTIC_GRAND_PIANO = 0
                const val BRIGHT_ACOUSTIC_PIANO = 1
                const val ELECTRIC_GRAND_PIANO = 2
                const val HONKYTONK_PIANO = 3
                const val ELECTRIC_PIANO_1 = 4
                const val ELECTRIC_PIANO_2 = 5
                const val HARPSICHORD = 6
                const val CLAVI = 7
                const val CELESTA = 8
                const val GLOCKENSPIEL = 9
                const val MUSIC_BOX = 10
                const val VIBRAPHONE = 11
                const val MARIMBA = 12
                const val XYLOPHONE = 13
                const val TUBULAR_BELLS = 14
                const val DULCIMER = 15
                const val DRAWBAR_ORGAN = 16
                const val PERCUSSIVE_ORGAN = 17
                const val ROCK_ORGAN = 18
                const val CHURCH_ORGAN = 29
                const val REED_ORGAN = 20
                const val ACCORDION = 21
                const val HARMONICA = 22
                const val TANGO_ACCORDION = 23
                const val ACOUSTIC_GUITAR_NYLON = 24
                const val ACOUSTIC_GUITAR_STEEL = 25
                const val ELECTRIC_GUITAR_JAZZ = 26
                const val ELECTRIC_GUITAR_CLEAN = 27
                const val ELECTRIC_GUITAR_MUTED = 28
                const val OVERDRIVEN_GUITAR = 29
                const val DISTORTION_GUITAR = 30
                const val GUITARHARMONICS = 31
                const val ACOUSTIC_BASS = 32
                const val ELECTRIC_BASS_FINGER = 33
                const val ELECTRIC_BASS_PICK = 34
                const val FRETLESS_BASS = 35
                const val SLAP_BASS_1 = 36
                const val SLAP_BASS_2 = 37
                const val SYNTH_BASS_1 = 38
                const val SYNTH_BASS_2 = 39
                const val VIOLIN = 40
                const val VIOLA = 41
                const val CELLO = 42
                const val CONTRABASS = 43
                const val TREMOLO_STRINGS = 44
                const val PIZZICATO_STRINGS = 45
                const val ORCHESTRAL_HARP = 46
                const val TIMPANI = 47
                const val STRING_ENSEMBLE_1 = 48
                const val STRING_ENSEMBLE_2 = 49
                const val SYNTH_STRINGS_1 = 50
                const val SYNTH_STRINGS_2 = 51
                const val CHOIR_AAHS = 52
                const val VOICE_OOHS = 53
                const val SYNTH_VOICE = 54
                const val ORCHESTRA_HIT = 55
                const val TRUMPET = 56
                const val TROMBONE = 57
                const val TUBA = 58
                const val MUTED_TRUMPET = 59
                const val FRENCH_HORN = 60
                const val BRASS_SECTION = 61
                const val SYNTH_BRASS_1 = 62
                const val SYNTH_BRASS_2 = 63
                const val SOPRANO_SAX = 64
                const val ALTO_SAX = 65
                const val TENOR_SAX = 66
                const val BARITONE_SAX = 67
                const val OBOE = 68
                const val ENGLISH_HORN = 69
                const val BASSOON = 70
                const val CLARINET = 71
                const val PICCOLO = 72
                const val FLUTE = 73
                const val RECORDER = 74
                const val PAN_FLUTE = 75
                const val BLOWN_BOTTLE = 76
                const val SHAKUHACHI = 77
                const val WHISTLE = 78
                const val OCARINA = 79
                const val LEAD_SQUARE = 80
                const val LEAD_SAWTOOTH = 81
                const val LEAD_CALLIOPE = 82
                const val LEAD_CHIFF = 83
                const val LEAD_CHARANG = 84
                const val LEAD_VOICE = 85
                const val LEAD_FIFTHS = 86
                const val LEAD_BASS_AND_LEAD = 87
                const val PAD_NEWAGE = 88
                const val PAD_WARM = 89
                const val PAD_POLYSYNTH = 90
                const val PAD_CHOIR = 91
                const val PAD_BOWED = 92
                const val PAD_METALLIC = 93
                const val PAD_HALO = 94
                const val PAD_SWEEP = 95
                const val FX_RAIN = 96
                const val FX_SOUNDTRACK = 97
                const val FX_CRYSTAL = 98
                const val FX_ATMOSPHERE = 99
                const val FX_BRIGHTNESS = 100
                const val FX_GOBLINS = 101
                const val FX_ECHOES = 102
                const val FX_SCIFI = 103
                const val SITAR = 104
                const val BANJO = 105
                const val SHAMISEN = 106
                const val KOTO = 107
                const val KALIMBA = 108
                const val BAGPIPE = 109
                const val FIDDLE = 110
                const val SHANAI = 111
                const val TINKLE_BELL = 112
                const val AGOGO = 113
                const val STEEL_DRUMS = 114
                const val WOODBLOCK = 115
                const val TAIKO_DRUM = 116
                const val MELODIC_TOM = 117
                const val SYNTH_DRUM = 118
                const val REVERSE_CYMBAL = 119
                const val GUITAR_FRET_NOISE = 120
                const val BREATH_NOISE = 121
                const val SEASHORE = 122
                const val BIRD_TWEET = 123
                const val TELEPHONE_RING = 124
                const val HELICOPTER = 125
                const val APPLAUSE = 126
                const val GUNSHOT = 127
            }
        }

        class Percussions {
            companion object {

                const val ACOUSTIC_BASS_DRUM = 34
                const val BASS_DRUM_1 = 35
                const val SIDE_STICK = 36
                const val ACOUSTIC_SNARE = 37
                const val HAND_CLAP = 38
                const val ELECTRIC_SNARE = 39
                const val LOW_FLOOR_TOM = 40
                const val CLOSED_HI_HAT = 41
                const val HIGH_FLOOR_TOM = 42
                const val PEDAL_HI_HAT = 43
                const val LOW_TOM = 44
                const val OPEN_HI_HAT = 45
                const val LOW_MID_TOM = 46
                const val HI_MID_TOM = 47
                const val CRASH_CYMBAL_1 = 48
                const val HIGH_TOM = 49
                const val RIDE_CYMBAL_1 = 50
                const val CHINESE_CYMBAL = 51
                const val RIDE_BELL = 52
                const val TAMBOURINE = 53
                const val SPLASH_CYMBAL = 54
                const val COWBELL = 55
                const val CRASH_CYMBAL_2 = 56
                const val VIBRASLAP = 57
                const val RIDE_CYMBAL_2 = 58
                const val HI_BONGO = 59
                const val LOW_BONGO = 60
                const val MUTE_HI_CONGA = 61
                const val OPEN_HI_CONGA = 62
                const val LOW_CONGA = 63
                const val HIGH_TIMBALE = 64
                const val LOW_TIMBALE = 65
                const val HIGH_AGOGO = 66
                const val LOW_AGOGO = 67
                const val CABASA = 68
                const val MARACAS = 69
                const val SHORT_WHISTLE = 70
                const val LONG_WHISTLE = 71
                const val SHORT_GUIRO = 72
                const val LONG_GUIRO = 73
                const val CLAVES = 74
                const val HI_WOOD_BLOCK = 75
                const val LOW_WOOD_BLOCK = 76
                const val MUTE_CUICA = 77
                const val OPEN_CUICA = 78
                const val MUTE_TRIANGLE = 79
                const val OPEN_TRIANGLE = 80
            }
        }
    }
}
