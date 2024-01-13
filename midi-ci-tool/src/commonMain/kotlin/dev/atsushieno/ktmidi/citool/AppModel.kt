package dev.atsushieno.ktmidi.citool

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.statekeeper.StateKeeper
import dev.atsushieno.ktmidi.ci.MidiCIDeviceConfiguration
import getPlatform
import io.ktor.utils.io.core.*
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

    private var savedSettings = stateKeeper.consume("SavedSettings", SavedSettings.serializer()) ?: SavedSettings()
    private val savedSettingsInstance: SavedSettings
    val midiDeviceManager = MidiDeviceManager()
    val ciDeviceManager  = CIDeviceManager(midiDeviceManager)

    val common by savedSettings::common
    val initiator by savedSettings::initiator
    val recipient by savedSettings::recipient

    fun setInputDevice(id: String) {
        midiDeviceManager.midiInputDeviceId = id
    }

    fun setOutputDevice(id: String) {
        midiDeviceManager.midiOutputDeviceId = id
    }

    fun loadConfig(file: String) {
        val bytes = getPlatform().loadFileContent(file)
        savedSettings = Json.decodeFromString(SavedSettings.serializer(), bytes.decodeToString())
    }

    fun saveConfig(file: String) {
        getPlatform().saveFileContent(file, Json.encodeToString(savedSettings).toByteArray())
    }

    fun loadConfigDefault() = loadConfig("midi-ci-tool.settings.json")
    fun saveConfigDefault() = saveConfig("midi-ci-tool.settings.json")

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
