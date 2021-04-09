package dev.atsushieno.ktmidi

fun Byte.toUnsigned() = if (this < 0) 0x100 + this else this.toInt()
fun Short.toUnsigned() = if (this < 0) 0x10000 + this else this.toInt()
fun Int.toUnsigned() = if (this < 0) 0x100000000 + this else this.toLong()

abstract class DeltaTimeComputer<T> {

    abstract fun messageToDeltaTime(message: T) : Int

    abstract fun isMetaEventMessage(message: T, metaType: Byte) : Boolean

    abstract fun getTempoValue(message: T) : Int

    fun getMetaEventsOfType(messages: Iterable<T>, metaType: Byte) : Sequence<Pair<Int,T>> = sequence {
        var v = 0
        for (m in messages) {
            v += messageToDeltaTime(m)
            if (isMetaEventMessage(m, metaType))
                yield(Pair(v, m))
        }
    }

    fun getTotalPlayTimeMilliseconds(messages: Iterable<T>, deltaTimeSpec: Int): Int {
        return getPlayTimeMillisecondsAtTick(messages, messages.sumBy { m -> messageToDeltaTime(m) }, deltaTimeSpec)
    }

    fun getPlayTimeMillisecondsAtTick(messages: Iterable<T>, ticks: Int, deltaTimeSpec: Int): Int {
        if (deltaTimeSpec < 0)
            throw UnsupportedOperationException("non-tick based DeltaTime")
        else {
            var tempo: Int = MidiMetaType.DEFAULT_TEMPO
            var v = 0.0
            var t = 0
            for (m in messages) {
                val messageDeltaTime = messageToDeltaTime(m)
                val deltaTime = if (t + messageDeltaTime < ticks) messageDeltaTime else ticks - t
                v += tempo.toDouble() / 1000 * deltaTime / deltaTimeSpec
                if (deltaTime != messageDeltaTime)
                    break
                t += messageDeltaTime
                if (isMetaEventMessage(m, MidiMetaType.TEMPO))
                    tempo = getTempoValue(m)
            }
            return v.toInt()
        }
    }
}


class MidiCC {
    companion object {

        const val BANK_SELECT = 0x00.toByte()
        const val MODULATION = 0x01.toByte()
        const val BREATH = 0x02.toByte()
        const val FOOT = 0x04.toByte()
        const val PORTAMENTO_TIME = 0x05.toByte()
        const val DTE_MSB = 0x06.toByte()
        const val VOLUME = 0x07.toByte()
        const val BALANCE = 0x08.toByte()
        const val PAN = 0x0A.toByte()
        const val EXPRESSION = 0x0B.toByte()
        const val EFFECT_CONTROL_1 = 0x0C.toByte()
        const val EFFECT_CONTROL_2 = 0x0D.toByte()
        const val GENERAL_1 = 0x10.toByte()
        const val GENERAL_2 = 0x11.toByte()
        const val GENERAL_3 = 0x12.toByte()
        const val GENERAL_4 = 0x13.toByte()
        const val BANK_SELECT_LSB = 0x20.toByte()
        const val MODULATION_LSB = 0x21.toByte()
        const val BREATH_LSB = 0x22.toByte()
        const val FOOT_LSB = 0x24.toByte()
        const val PORTAMENTO_TIME_LSB = 0x25.toByte()
        const val DTE_LSB = 0x26.toByte()
        const val VOLUME_LSB = 0x27.toByte()
        const val BALANCE_LSB = 0x28.toByte()
        const val PAN_LSB = 0x2A.toByte()
        const val EXPRESSION_LSB = 0x2B.toByte()
        const val EFFECT_1_LSB = 0x2C.toByte()
        const val EFFECT_2_LSB = 0x2D.toByte()
        const val GENERAL_1_LSB = 0x30.toByte()
        const val GENERAL_2_LSB = 0x31.toByte()
        const val GENERAL_3_LSB = 0x32.toByte()
        const val GENERAL_4_LSB = 0x33.toByte()
        const val HOLD = 0x40.toByte()
        const val PORTAMENTO_SWITCH = 0x41.toByte()
        const val SOSTENUTO = 0x42.toByte()
        const val SOFT_PEDAL = 0x43.toByte()
        const val LEGATO = 0x44.toByte()
        const val HOLD_2 = 0x45.toByte()
        const val SOUND_CONTROLLER_1 = 0x46.toByte()
        const val SOUND_CONTROLLER_2 = 0x47.toByte()
        const val SOUND_CONTROLLER_3 = 0x48.toByte()
        const val SOUND_CONTROLLER_4 = 0x49.toByte()
        const val SOUND_CONTROLLER_5 = 0x4A.toByte()
        const val SOUND_CONTROLLER_6 = 0x4B.toByte()
        const val SOUND_CONTROLLER_7 = 0x4C.toByte()
        const val SOUND_CONTROLLER_8 = 0x4D.toByte()
        const val SOUND_CONTROLLER_9 = 0x4E.toByte()
        const val SOUND_CONTROLLER_10 = 0x4F.toByte()
        const val GENERAL_5 = 0x50.toByte()
        const val GENERAL_6 = 0x51.toByte()
        const val GENERAL_7 = 0x52.toByte()
        const val GENERAL_8 = 0x53.toByte()
        const val PORTAMENTO_CONTROL = 0x54.toByte()
        const val RSD = 0x5B.toByte()
        const val EFFECT_1 = 0x5B.toByte()
        const val TREMOLO = 0x5C.toByte()
        const val EFFECT_2 = 0x5C.toByte()
        const val CSD = 0x5D.toByte()
        const val EFFECT_3 = 0x5D.toByte()
        const val CELESTE = 0x5E.toByte()
        const val EFFECT_4 = 0x5E.toByte()
        const val PHASER = 0x5F.toByte()
        const val EFFECT_5 = 0x5F.toByte()
        const val DTE_INCREMENT = 0x60.toByte()
        const val DTE_DECREMENT = 0x61.toByte()
        const val NRPN_LSB = 0x62.toByte()
        const val NRPN_MSB = 0x63.toByte()
        const val RPN_LSB = 0x64.toByte()
        const val RPN_MSB = 0x65.toByte()

        // Channel mode messages
        const val ALL_SOUND_OFF = 0x78.toByte()
        const val RESET_ALL_CONTROLLERS = 0x79.toByte()
        const val LOCAL_CONTROL = 0x7A.toByte()
        const val ALL_NOTES_OFF = 0x7B.toByte()
        const val OMNI_MODE_OFF = 0x7C.toByte()
        const val OMNI_MODE_ON = 0x7D.toByte()
        const val POLY_MODE_OFF = 0x7E.toByte()
        const val POLY_MODE_ON = 0x7F.toByte()
    }
}

class MidiRpnType {
    companion object {

        const val PITCH_BEND_SENSITIVITY = 0.toByte()
        const val FINE_TUNING = 1.toByte()
        const val COARSE_TUNING = 2.toByte()
        const val TUNING_PROGRAM = 3.toByte()
        const val TUNING_BANK_SELECT = 4.toByte()
        const val MODULATION_DEPTH = 5.toByte()
    }
}

class MidiMetaType {
    companion object {

        const val SEQUENCE_NUMBER = 0x00.toByte()
        const val TEXT = 0x01.toByte()
        const val COPYRIGHT = 0x02.toByte()
        const val TRACK_NAME = 0x03.toByte()
        const val INSTRUMENT_NAME = 0x04.toByte()
        const val LYRIC = 0x05.toByte()
        const val MARKER = 0x06.toByte()
        const val CUE = 0x07.toByte()
        const val CHANNEL_PREFIX = 0x20.toByte()
        const val END_OF_TRACK = 0x2F.toByte()
        const val TEMPO = 0x51.toByte()
        const val SMTPE_OFFSET = 0x54.toByte()
        const val TIME_SIGNATURE = 0x58.toByte()
        const val KEY_SIGNATURE = 0x59.toByte()
        const val SEQUENCER_SPECIFIC = 0x7F.toByte()

        const val DEFAULT_TEMPO = 500000

        // FIXME: this should be move out of common parts, as it is MIDI 1.0 specific.
        fun getTempo(data: ByteArray, offset: Int): Int {
            if (data.size < offset + 2)
                throw IndexOutOfBoundsException("data array must be longer than argument offset $offset + 2")
            return (data[offset].toUnsigned() shl 16) + (data[offset + 1].toUnsigned() shl 8) + data[offset + 2]
        }

        // FIXME: this should be move out of common parts, as it is MIDI 1.0 specific.
        fun getBpm(data: ByteArray, offset: Int): Double {
            return 60000000.0 / getTempo(data, offset)
        }
    }
}

class MidiEventType {
    companion object {

        // MIDI 2.0-specific
        const val PER_NOTE_RCC: Byte = 0x00.toByte()
        const val PER_NOTE_ACC: Byte = 0x10.toByte()
        const val RPN: Byte = 0x20.toByte()
        const val NRPN: Byte = 0x30.toByte()
        const val RELATIVE_RPN: Byte = 0x40.toByte()
        const val RELATIVE_NRPN: Byte = 0x50.toByte()
        const val PER_NOTE_PITCH: Byte = 0x60.toByte()
        const val PER_NOTE_MANAGEMENT: Byte = 0xF0.toByte()

        // MIDI 1.0/2.0 common
        const val NOTE_OFF: Byte = 0x80.toByte()
        const val NOTE_ON: Byte = 0x90.toByte()
        const val PAF: Byte = 0xA0.toByte()
        const val CC: Byte = 0xB0.toByte()
        const val PROGRAM: Byte = 0xC0.toByte()
        const val CAF: Byte = 0xD0.toByte()
        const val PITCH: Byte = 0xE0.toByte()

        // MIDI 1.0-specific
        const val SYSEX: Byte = 0xF0.toByte()
        const val MTC_QUARTER_FRAME: Byte = 0xF1.toByte()
        const val SONG_POSITION_POINTER: Byte = 0xF2.toByte()
        const val SONG_SELECT: Byte = 0xF3.toByte()
        const val TUNE_REQUEST: Byte = 0xF6.toByte()
        const val SYSEX_END: Byte = 0xF7.toByte()
        const val MIDI_CLOCK: Byte = 0xF8.toByte()
        const val MIDI_TICK: Byte = 0xF9.toByte()
        const val MIDI_START: Byte = 0xFA.toByte()
        const val MIDI_CONTINUE: Byte = 0xFB.toByte()
        const val MIDI_STOP: Byte = 0xFC.toByte()
        const val ACTIVE_SENSE: Byte = 0xFE.toByte()
        const val RESET: Byte = 0xFF.toByte()
        const val META: Byte = 0xFF.toByte()
    }
}
