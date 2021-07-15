package dev.atsushieno.ktmidi

internal fun Byte.toUnsigned() = if (this < 0) 0x100 + this else this.toInt()
internal fun Short.toUnsigned() = if (this < 0) 0x10000 + this else this.toInt()
internal fun Int.toUnsigned() = if (this < 0) 0x100000000 + this else this.toLong()

internal abstract class DeltaTimeComputer<T> {

    abstract fun messageToDeltaTime(message: T) : Int

    abstract fun isMetaEventMessage(message: T, metaType: Int) : Boolean

    abstract fun getTempoValue(message: T) : Int

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
                if (isMetaEventMessage(m, MidiMetaType.TEMPO))
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
