@file:JsModule("js-synthesizer")

package dev.atsushieno.ktmidi

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

external interface SynthesizerSettings : JsAny
external interface InterpolationValues : JsAny
external interface PlayerSetTempoType : JsAny

external interface ISynthesizer : JsAny {
    fun isInitialized(): Boolean
    fun init(sampleRate: Double, settings: SynthesizerSettings? = definedExternally)
    fun close()
    fun isPlaying(): Boolean
    fun setInterpolation(value: InterpolationValues, channel: Double?)
    fun getGain(): Double
    fun setGain(gain: Double)
    fun setChannelType(channel: Double, isDrum: Boolean)
    fun waitForVoicesStopped(): Promise<JsAny?> // void

    fun loadSFont(bin: ArrayBuffer): Promise<JsAny?> // number
    fun unloadSFont(id: Double)
    fun unloadSFontAsync(id: Double): Promise<JsAny?> // void
    fun getSFontBankOffset(id: Double): Promise<JsAny?> // number
    fun setSFontBankOffset(id: Double, offset: Double)

    fun render(outBuffer: JsAny?) // AudioBuffer | Float32Array[]

    fun midiNoteOn(chan: Byte, key: Byte, vel: Byte)
    fun midiNoteOff(chan: Byte, key: Byte)
    fun midiKeyPressure(chan: Byte, key: Byte, value: Byte)
    fun midiControl(chan: Byte, ctrl: Byte, value: Byte)
    fun midiProgramChange(chan: Byte, prognum: Byte)
    fun midiChannelPressure(chan: Byte, value: Byte)
    fun midiPitchBend(chan: Byte, value: Short)
    fun midiSysEx(data: Uint8Array)

    fun midiPitchWheelSensitivity(chan: Byte, value: Byte)
    fun midiBankSelect(chan: Byte, bank: Byte)
    fun midiSFontSelect(chan: Byte, sfontId: Int)
    fun midiProgramSelect(chan: Byte, sfontId: Int, bank: Byte, presetNum: Byte)
    fun midiUnsetProgram(chan: Byte)
    fun midiProgramReset()
    fun midiSystemReset()
    fun midiAllNotesOff(chan: Byte?)
    fun midiAllSoundsOff(chan: Byte?)
    fun midiSetChannelType(chan: Byte, isDrum: Boolean)

    fun resetPlayer(): Promise<JsAny?> // void
    fun closePlayer()
    fun isPlayerPlaying(): Boolean
    fun addSMFDataToPlayer(bin: ArrayBuffer): Promise<JsAny?> // void
    fun playPlayer(): Promise<JsAny?> // void
    fun stopPlayer()
    fun retrievePlayerCurrentTick(): Promise<JsAny?> // number
    fun retrievePlayerTotalTicks(): Promise<JsAny?> // number
    fun retrievePlayerBpm(): Promise<JsAny?> // number
    fun retrievePlayerMIDITempo(): Promise<JsAny?> // number
    fun seekPlayer(ticks: Double)
    fun setPlayerLoop(loopTimes: Double)
    fun setPlayerTempo(tempoType: PlayerSetTempoType, tempo: Double)
    fun waitForPlayerStopped(): Promise<JsAny?> // void
}
