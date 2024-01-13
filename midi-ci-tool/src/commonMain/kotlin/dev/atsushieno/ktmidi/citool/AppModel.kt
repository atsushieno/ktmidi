package dev.atsushieno.ktmidi.citool

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.statekeeper.StateKeeper
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString

expect fun initializeAppModel(context: Any)

// initializeAppModel() is supposed to initialize this
lateinit var AppModel: CIToolRepository

data class LogEntry(val timestamp: LocalDateTime, val data: Any) {
    override fun toString() = "[${timestamp.time.toString().substring(0, 8)}] $data}"
}

class CIToolRepository(private val lifecycle: Lifecycle, private val stateKeeper: StateKeeper, instanceKeeper: InstanceKeeper) {

    private val logs = mutableListOf<LogEntry>()

    fun log(msg: Any, explicitTimestamp: LocalDateTime? = null) {
        val time = explicitTimestamp ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val entry = LogEntry(time, msg)
        logs.add(entry)
        logRecorded.forEach { it(entry) }
    }
    val logRecorded = mutableListOf<(LogEntry) -> Unit>()

    val savedSettings = stateKeeper.consume("SavedSettings", SavedSettings.serializer()) ?: SavedSettings()
    private val savedSettingsInstance: SavedSettings
    val midiDeviceManager = MidiDeviceManager()
    val ciDeviceManager  = CIDeviceManager(midiDeviceManager)

    fun setInputDevice(id: String) {
        midiDeviceManager.midiInputDeviceId = id
    }

    fun setOutputDevice(id: String) {
        midiDeviceManager.midiOutputDeviceId = id
    }

    init {
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onCreate() {
                println("!!!! Lifecycle onCreate")
            }

            override fun onDestroy() {
                println("!!!! Lifecycle onDestroy")
            }
        })
        stateKeeper.register("SavedSettings", strategy = SavedSettings.serializer()) { savedSettings }

        savedSettingsInstance = instanceKeeper.getOrCreate { savedSettings }
    }
}
