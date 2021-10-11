package dev.atsushieno.ktmidi

object MidiMessageType { // MIDI 2.0
    const val UTILITY = 0
    const val SYSTEM = 1
    const val MIDI1 = 2
    const val SYSEX7 = 3
    const val MIDI2 = 4
    const val SYSEX8_MDS = 5
}

object MidiUtilityStatus {
    const val NOP = 0
    const val JR_CLOCK = 0x10
    const val JR_TIMESTAMP = 0x20
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
