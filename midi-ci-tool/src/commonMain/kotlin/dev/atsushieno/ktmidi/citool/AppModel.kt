package dev.atsushieno.ktmidi.citool

import getPlatform
import io.ktor.utils.io.core.*
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

fun initializeAppModel(context: Any) { AppModel = CIToolRepository() }

// initializeAppModel() is supposed to initialize this
lateinit var AppModel: CIToolRepository

data class LogEntry(val timestamp: LocalDateTime, val data: Any) {
    override fun toString() = "[${timestamp.time.toString().substring(0, 8)}] $data}"
}

class CIToolRepository {

    private val logs = mutableListOf<LogEntry>()

    fun log(msg: Any, explicitTimestamp: LocalDateTime? = null) {
        val time = explicitTimestamp ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val entry = LogEntry(time, msg)
        logs.add(entry)
        logRecorded.forEach { it(entry) }
    }
    val logRecorded = mutableListOf<(LogEntry) -> Unit>()

    val muid: Int = Random.nextInt() and 0x7F7F7F7F

    private var savedSettings: SavedSettings = SavedSettings()
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

    val defaultConfigFile = "midi-ci-tool.settings.json"
    private fun getConfigDefault() = getConfigFromFile((defaultConfigFile))
    fun loadConfigDefault() = loadConfig(defaultConfigFile)
    fun saveConfigDefault() = saveConfig(defaultConfigFile)

    init {
        savedSettings = try { getConfigDefault() } catch(ex: Exception) { SavedSettings() }
    }
}
