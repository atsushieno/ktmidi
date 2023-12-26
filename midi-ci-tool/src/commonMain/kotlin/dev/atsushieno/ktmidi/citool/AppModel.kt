package dev.atsushieno.ktmidi.citool

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

object AppModel {
    val midiDeviceManager = MidiDeviceManager()
    val ciDeviceManager = CIDeviceManager(midiDeviceManager)

    fun setInputDevice(id: String) {
        midiDeviceManager.midiInputDeviceId = id
    }

    fun setOutputDevice(id: String) {
        midiDeviceManager.midiOutputDeviceId = id
    }
}
