package dev.atsushieno.ktmidi.citool

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate

expect fun initializeAppModel(context: Any)

// initializeAppModel() is supposed to initialize this
lateinit var AppModel: CIToolRepository

class CIToolRepository(instanceKeeper: InstanceKeeper) {
    val savedSettings = instanceKeeper.getOrCreate { SavedSettings() }
    val midiDeviceManager = MidiDeviceManager()
    val ciDeviceManager  = CIDeviceManager(midiDeviceManager)

    fun setInputDevice(id: String) {
        midiDeviceManager.midiInputDeviceId = id
    }

    fun setOutputDevice(id: String) {
        midiDeviceManager.midiOutputDeviceId = id
    }
}
