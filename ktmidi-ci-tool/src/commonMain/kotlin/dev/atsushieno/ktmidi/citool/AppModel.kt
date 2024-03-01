package dev.atsushieno.ktmidi.citool

import dev.atsushieno.ktmidi.ci.LogEntry
import dev.atsushieno.ktmidi.ci.MessageDirection
import dev.atsushieno.ktmidi.toUtf8ByteArray
import getPlatform
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.random.Random

private var appInitialized = false
fun initializeAppModel(context: Any?) {
    if (!appInitialized)
        AppModel = CIToolRepository()
    appInitialized = true
}

// initializeAppModel() is supposed to initialize this
lateinit var AppModel: CIToolRepository

class CIToolRepository {

    private val logs = mutableListOf<LogEntry>()

    fun log(msg: Any, direction: MessageDirection, explicitTimestamp: LocalDateTime? = null) {
        val time = explicitTimestamp ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val entry = LogEntry(time, direction, msg)
        logs.add(entry)
        logRecorded.forEach { it(entry) }
    }
    val logRecorded = mutableListOf<(LogEntry) -> Unit>()

    val muid: Int = Random.nextInt() and 0x7F7F7F7F

    private var savedSettings: SavedSettings = try {
        getConfigDefault()
    } catch (ex: Exception) {
        println(ex)
        println(ex.printStackTrace())
        SavedSettings()
    }
    val midiDeviceManager = MidiDeviceManager()
    val ciDeviceManager  = CIDeviceManager(this, savedSettings.device, midiDeviceManager)

    val device by savedSettings::device

    private fun getConfigFromFile(file: String): SavedSettings {
        val bytes = getPlatform().loadFileContent(file)
        return Json.decodeFromString(SavedSettings.serializer(), bytes.decodeToString())
    }
    fun loadConfig(file: String) {
        savedSettings = getConfigFromFile(file)
    }

    fun saveConfig(file: String) {
        getPlatform().saveFileContent(file, Json.encodeToString(savedSettings).toUtf8ByteArray())
    }

    private fun getConfigDefault() = getConfigFromFile((DEFAULT_CONFIG_FILE))
    fun loadConfigDefault() = loadConfig(DEFAULT_CONFIG_FILE)
    fun saveConfigDefault() = saveConfig(DEFAULT_CONFIG_FILE)

    companion object {
        const val DEFAULT_CONFIG_FILE = "ktmidi-ci-tool.settings.json"
    }
}
