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

    private var savedSettings: SavedSettings = SavedSettings()
    private val savedSettingsInstance: SavedSettings
    val midiDeviceManager = MidiDeviceManager()
    val ciDeviceManager  = CIDeviceManager(midiDeviceManager)

    val common by lazy { savedSettings.common }
    val initiator by lazy { savedSettings.initiator }
    val recipient by lazy { savedSettings.recipient }

    fun setInputDevice(id: String) {
        midiDeviceManager.midiInputDeviceId = id
    }

    fun setOutputDevice(id: String) {
        midiDeviceManager.midiOutputDeviceId = id
    }

    private fun getConfigFromFile(file: String): SavedSettings {
        val bytes = getPlatform().loadFileContent(file)
        return Json.decodeFromString(SavedSettings.serializer(), bytes.decodeToString())
    }
    fun loadConfig(file: String) {
        savedSettings = getConfigFromFile(file)
    }

    fun saveConfig(file: String) {
        getPlatform().saveFileContent(file, Json.encodeToString(savedSettings).toByteArray())
    }

    private val defaultConfigFile = "midi-ci-tool.settings.json"
    private fun getConfigDefault() = getConfigFromFile((defaultConfigFile))
    fun loadConfigDefault() = loadConfig(defaultConfigFile)
    fun saveConfigDefault() = saveConfig(defaultConfigFile)

    init {
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onCreate() {
                println("!!!! Lifecycle onCreate")
            }

            override fun onDestroy() {
                println("!!!! Lifecycle onDestroy")
            }
        })

        savedSettings = stateKeeper.consume("SavedSettings", SavedSettings.serializer())
            ?: try { getConfigDefault() } catch(ex: Exception) { SavedSettings() }
        stateKeeper.register("SavedSettings", strategy = SavedSettings.serializer()) {
            try { getConfigDefault() } catch(ex: Exception) { SavedSettings() }
        }

        savedSettingsInstance = instanceKeeper.getOrCreate { savedSettings }
    }
}
