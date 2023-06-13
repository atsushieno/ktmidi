package dev.atsushieno.ktmidi

object MidiMessageType { // MIDI 2.0
    const val UTILITY = 0
    const val SYSTEM = 1
    const val MIDI1 = 2
    const val SYSEX7 = 3
    const val MIDI2 = 4
    const val SYSEX8_MDS = 5
    const val FlexData = 0xD // June 2023 updates
    const val UMP_STREAM = 0xF // June 2023 updates
}

object MidiUtilityStatus {
    const val NOP = 0x0000
    const val JR_CLOCK = 0x0010
    const val JR_TIMESTAMP = 0x0020
    const val DCTPQ = 0x0030
    const val DELTA_CLOCKSTAMP = 0x0040
}

object MidiSystemStatus {
    const val MIDI_TIME_CODE = 0xF1
    const val SONG_POSITION = 0xF2
    const val SONG_SELECT = 0xF3
    const val TUNE_REQUEST = 0xF6
    const val TIMING_CLOCK = 0xF8
    const val START = 0xFA
    const val CONTINUE = 0xFB
    const val STOP = 0xFC
    const val ACTIVE_SENSING = 0xFE
    const val RESET = 0xFF
}

object MidiChannelStatus {
    const val NOTE_OFF = 0x80
    const val NOTE_ON = 0x90
    const val PAF = 0xA0
    const val CC = 0xB0
    const val PROGRAM = 0xC0
    const val CAF = 0xD0
    const val PITCH_BEND = 0xE0
    const val PER_NOTE_RCC = 0x00
    const val PER_NOTE_ACC = 0x10
    const val RPN = 0x20
    const val NRPN = 0x30
    const val RELATIVE_RPN = 0x40
    const val RELATIVE_NRPN = 0x50
    const val PER_NOTE_PITCH_BEND = 0x60
    const val PER_NOTE_MANAGEMENT = 0xF0
}

object Midi2BinaryChunkStatus {
    const val SYSEX_IN_ONE_UMP = 0
    const val SYSEX_START = 0x10
    const val SYSEX_CONTINUE = 0x20
    const val SYSEX_END = 0x30
    const val MDS_HEADER = 0x80
    const val MDS_PAYLOAD = 0x90
}

object FlexDataAddress {
    const val CHANNEL_FIELD: Byte = 0
    const val GROUP: Byte = 1
    // 2, 3 -> reserved
}

object FlexDataStatusBank {
    const val SETUP_AND_PERFORMANCE: Byte = 0
    const val METADATA_TEXT: Byte = 1
    const val PERFORMANCE_TEXT: Byte = 2
    // 3-FF -> reserved
}

object FlexDataStatus {
    const val TEMPO: Byte = 0
    const val TIME_SIGNATURE: Byte = 1
    const val METRONOME: Byte = 2
    const val KEY_SIGNATURE: Byte = 5
    const val CHORD_NAME: Byte = 6
}

object MetadataTextStatus {
    const val UNKNOWN: Byte = 0
    const val PROJECT_NAME: Byte = 1
    const val COMPOSITION_NAME: Byte = 2
    const val MIDI_CLIP_NAME: Byte = 3
    const val COPYRIGHT: Byte = 4
    const val AUTHOR: Byte = 5
    const val LYRICIST: Byte = 6
    const val ARRANGER: Byte = 7
    const val PUBLISHER: Byte = 8
    const val PRIMARY_PERFORMER: Byte = 9
    const val ACCOMPANYING_PERFORMER: Byte = 0xA
    const val RECORDING_CONCERT_DATE: Byte = 0xB
    const val RECORDING_CONCERT_LOCATION: Byte = 0xC
}

object PerformanceTextStatus {
    const val UNKNOWN: Byte = 0
    const val LYRICS: Byte = 1
    const val LYRICS_LANGUAGE: Byte = 2
    const val RUBY: Byte = 3
    const val RUBY_LANGUAGE: Byte = 4
}

// Used by "Key Signature" Flex Data messages
object TonicNoteField {
    const val UNKNOWN: Byte = 0
    const val NON_STANDARD: Byte = 0
    const val A: Byte = 1
    const val B: Byte = 2
    const val C: Byte = 3
    const val D: Byte = 4
    const val E: Byte = 5
    const val F: Byte = 6
    const val G: Byte = 7
    // 8-FF -> reserved
}

object ChordSharpFlatsField {
    const val DOUBLE_SHARP: Byte = 2
    const val SHARP: Byte = 1
    const val NATURAL: Byte = 0
    const val FLAT: Byte = -1
    const val DOUBLE_FLAT: Byte = -2
    const val BASS_NOTE_AS_CHORD_TONIC_NOTE: Byte = -8
}

object ChordTypeField {
    const val CLEAR_CHORD: Byte = 0
    const val NO_CHORD: Byte = 0
    const val MAJOR: Byte = 1
    const val MAJOR_6TH: Byte = 2
    const val MAJOR_7TH: Byte = 3
    const val MAJOR_9TH: Byte = 4
    const val MAJOR_11TH: Byte = 5
    const val MAJOR_13TH: Byte = 6
    const val MINOR: Byte = 7
    const val MINOR_6TH: Byte = 8
    const val MINOR_7TH: Byte = 9
    const val MINOR_9TH: Byte = 0xA
    const val MINOR_11TH: Byte = 0xB
    const val MINOR_13TH: Byte = 0xC
    const val DOMINANT: Byte = 0xD
    const val DOMINANT_9TH: Byte = 0xE
    const val DOMINANT_11TH: Byte = 0xF
    const val DOMINANT_13TH: Byte = 0x10
    const val AUGMENTED: Byte = 0x11
    const val AUGMENTED_7TH: Byte = 0x12
    const val DIMINISHED: Byte = 0x13
    const val DIMINISHED_7TH: Byte = 0x14
    const val HALF_DIMINISHED: Byte = 0x15
    const val MAJOR_MINOR: Byte = 0x16
    const val MINOR_MAJOR: Byte = 0x16
    const val PEDAL: Byte = 0x17
    const val POWER: Byte = 0x18
    const val SUSPENDED_2ND: Byte = 0x19
    const val SUSPENDED_4TH: Byte = 0x1A
    const val SEVENTH_SUSPENDED_4TH: Byte = 0x1B
}

// I choose Int because there will be degree to be added, then the results will become Int anyway!
object ChordAlterationType {
    const val NO_ALTERATION: UByte = 0x00U
    const val ADD_DEGREE: UByte = 0x10U
    const val SUBTRACT_DEGREE: UByte = 0x20U
    const val RAISE_DEGREE: UByte = 0x30U
    const val LOWER_DEGREE: UByte = 0x40U
}

object MidiCIProtocolBytes { // MIDI 2.0
    const val TYPE = 0
    const val VERSION = 1
    const val EXTENSIONS = 2
}

object MidiCIProtocolType { // MIDI 2.0
    const val MIDI1 = 1
    const val MIDI2 = 2
}

object MidiCIProtocolValue { // MIDI 2.0
    const val MIDI1 = 0
    const val MIDI2_V1 = 0
}

object MidiCIProtocolExtensions { // MIDI 2.0
    const val JITTER = 1
    const val LARGER = 2
}

object MidiNoteAttributeType { // MIDI 2.0
    const val NONE = 0
    const val MANUFACTURER_SPECIFIC = 1
    const val PROFILE_SPECIFIC = 2
    const val Pitch7_9 = 3
}

object MidiPerNoteManagementFlags { // MIDI 2.0
    const val RESET = 1
    const val DETACH = 2
}

object MidiProgramChangeOptions {
    const val NONE = 0
    const val BANK_VALID = 1
}

object MidiCC {
    const val BANK_SELECT = 0x00
    const val MODULATION = 0x01
    const val BREATH = 0x02
    const val FOOT = 0x04
    const val PORTAMENTO_TIME = 0x05
    const val DTE_MSB = 0x06
    const val VOLUME = 0x07
    const val BALANCE = 0x08
    const val PAN = 0x0A
    const val EXPRESSION = 0x0B
    const val EFFECT_CONTROL_1 = 0x0C
    const val EFFECT_CONTROL_2 = 0x0D
    const val GENERAL_1 = 0x10
    const val GENERAL_2 = 0x11
    const val GENERAL_3 = 0x12
    const val GENERAL_4 = 0x13
    const val BANK_SELECT_LSB = 0x20
    const val MODULATION_LSB = 0x21
    const val BREATH_LSB = 0x22
    const val FOOT_LSB = 0x24
    const val PORTAMENTO_TIME_LSB = 0x25
    const val DTE_LSB = 0x26
    const val VOLUME_LSB = 0x27
    const val BALANCE_LSB = 0x28
    const val PAN_LSB = 0x2A
    const val EXPRESSION_LSB = 0x2B
    const val EFFECT_1_LSB = 0x2C
    const val EFFECT_2_LSB = 0x2D
    const val GENERAL_1_LSB = 0x30
    const val GENERAL_2_LSB = 0x31
    const val GENERAL_3_LSB = 0x32
    const val GENERAL_4_LSB = 0x33
    const val HOLD = 0x40
    const val PORTAMENTO_SWITCH = 0x41
    const val SOSTENUTO = 0x42
    const val SOFT_PEDAL = 0x43
    const val LEGATO = 0x44
    const val HOLD_2 = 0x45
    const val SOUND_CONTROLLER_1 = 0x46
    const val SOUND_CONTROLLER_2 = 0x47
    const val SOUND_CONTROLLER_3 = 0x48
    const val SOUND_CONTROLLER_4 = 0x49
    const val SOUND_CONTROLLER_5 = 0x4A
    const val SOUND_CONTROLLER_6 = 0x4B
    const val SOUND_CONTROLLER_7 = 0x4C
    const val SOUND_CONTROLLER_8 = 0x4D
    const val SOUND_CONTROLLER_9 = 0x4E
    const val SOUND_CONTROLLER_10 = 0x4F
    const val GENERAL_5 = 0x50
    const val GENERAL_6 = 0x51
    const val GENERAL_7 = 0x52
    const val GENERAL_8 = 0x53
    const val PORTAMENTO_CONTROL = 0x54
    const val RSD = 0x5B
    const val EFFECT_1 = 0x5B
    const val TREMOLO = 0x5C
    const val EFFECT_2 = 0x5C
    const val CSD = 0x5D
    const val EFFECT_3 = 0x5D
    const val CELESTE = 0x5E
    const val EFFECT_4 = 0x5E
    const val PHASER = 0x5F
    const val EFFECT_5 = 0x5F
    const val DTE_INCREMENT = 0x60
    const val DTE_DECREMENT = 0x61
    const val NRPN_LSB = 0x62
    const val NRPN_MSB = 0x63
    const val RPN_LSB = 0x64
    const val RPN_MSB = 0x65

    // Channel mode messages
    const val ALL_SOUND_OFF = 0x78
    const val RESET_ALL_CONTROLLERS = 0x79
    const val LOCAL_CONTROL = 0x7A
    const val ALL_NOTES_OFF = 0x7B
    const val OMNI_MODE_OFF = 0x7C
    const val OMNI_MODE_ON = 0x7D
    const val POLY_MODE_OFF = 0x7E
    const val POLY_MODE_ON = 0x7F
}

object MidiRpn {
    const val PITCH_BEND_SENSITIVITY = 0
    const val FINE_TUNING = 1
    const val COARSE_TUNING = 2
    const val TUNING_PROGRAM = 3
    const val TUNING_BANK_SELECT = 4
    const val MODULATION_DEPTH = 5
}

object MidiMetaType {
    const val SEQUENCE_NUMBER = 0x00
    const val TEXT = 0x01
    const val COPYRIGHT = 0x02
    const val TRACK_NAME = 0x03
    const val INSTRUMENT_NAME = 0x04
    const val LYRIC = 0x05
    const val MARKER = 0x06
    const val CUE = 0x07
    const val CHANNEL_PREFIX = 0x20
    const val END_OF_TRACK = 0x2F
    const val TEMPO = 0x51
    const val SMTPE_OFFSET = 0x54
    const val TIME_SIGNATURE = 0x58
    const val KEY_SIGNATURE = 0x59
    const val SEQUENCER_SPECIFIC = 0x7F
}
