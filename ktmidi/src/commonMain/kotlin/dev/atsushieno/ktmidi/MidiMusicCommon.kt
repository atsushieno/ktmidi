package dev.atsushieno.ktmidi

internal fun Byte.toUnsigned() = if (this < 0) 0x100 + this else this.toInt()
internal fun Short.toUnsigned() = if (this < 0) 0x10000 + this else this.toInt()
internal fun Int.toUnsigned() = if (this < 0) 0x100000000 + this else this.toLong()

// DeltaClockstamp time unit
data class Dc(val value: Int)

val Int.dc
    get() = Dc(this)

data class Timed<T>(val duration: Dc, val value: T)

internal abstract class DeltaTimeComputer<T> {

    // Note that this does not mention any "unit".
    // Therefore, in UMP Delta Clockstamps and JR Timestamps must be used *exclusively*.
    abstract fun messageToDeltaTime(message: T) : Int

    @Deprecated("It is going to be impossible to support in SMF2 so we will remove it")
    abstract fun isMetaEventMessage(message: T, metaType: Int) : Boolean

    abstract fun isTempoMessage(message: T): Boolean

    abstract fun getTempoValue(message: T) : Int

    fun filterEvents(messages: Iterable<T>, filter: (T) -> Boolean) : Sequence<Timed<T>> = sequence {
        var v = 0
        for (m in messages) {
            v += messageToDeltaTime(m)
            if (filter(m))
                yield(Timed(v.dc, m))
        }
    }

    @Deprecated("It is going to be impossible to support in SMF2 so we will remove it")
    fun getMetaEventsOfType(messages: Iterable<T>, metaType: Int) : Sequence<Pair<Int,T>> = sequence {
        var v = 0
        for (m in messages) {
            v += messageToDeltaTime(m)
            if (isMetaEventMessage(m, metaType))
                yield(Pair(v, m))
        }
    }

    fun getTotalPlayTimeMilliseconds(messages: Iterable<T>, deltaTimeSpec: Int): Int {
        return getPlayTimeMillisecondsAtTick(messages, messages.sumOf { m -> messageToDeltaTime(m) }, deltaTimeSpec)
    }

    fun getPlayTimeMillisecondsAtTick(messages: Iterable<T>, ticks: Int, deltaTimeSpec: Int): Int {
        if (deltaTimeSpec < 0)
            throw UnsupportedOperationException("non-tick based DeltaTime")
        else {
            var tempo: Int = MidiMusic.DEFAULT_TEMPO
            var v = 0.0
            var t = 0
            for (m in messages) {
                val messageDeltaTime = messageToDeltaTime(m)
                val deltaTime = if (t + messageDeltaTime < ticks) messageDeltaTime else ticks - t
                v += tempo.toDouble() / 1000 * deltaTime / deltaTimeSpec
                if (deltaTime != messageDeltaTime)
                    break
                t += messageDeltaTime
                if (isTempoMessage(m))
                    tempo = getTempoValue(m)
            }
            return v.toInt()
        }
    }
}

@Deprecated("Use MidiChannelStatus or MidiSystemStatus")
object MidiEventType {
    // MIDI 1.0/2.0 common
    const val NOTE_OFF: Byte = 0x80.toByte()
    const val NOTE_ON: Byte = 0x90.toByte()
    const val PAF: Byte = 0xA0.toByte()
    const val CC: Byte = 0xB0.toByte()
    const val PROGRAM: Byte = 0xC0.toByte()
    const val CAF: Byte = 0xD0.toByte()
    const val PITCH: Byte = 0xE0.toByte()
}
