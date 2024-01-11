package dev.atsushieno.ktmidi.citool

import androidx.compose.runtime.MutableState
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import dev.atsushieno.ktmidi.citool.view.ViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

expect fun initializeAppModel(context: Any)

// initializeAppModel() is supposed to initialize this
lateinit var AppModel: CIToolRepository

data class LogEntry(val timestamp: LocalDateTime, val data: Any) {
    override fun toString() = "[${timestamp.time.toString().substring(0, 8)}] $data}"
}

class CIToolRepository(instanceKeeper: InstanceKeeper) {

    private val logs = mutableListOf<LogEntry>()

    fun log(msg: Any, explicitTimestamp: LocalDateTime? = null) {
        val time = explicitTimestamp ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val entry = LogEntry(time, msg)
        logs.add(entry)
        logRecorded.forEach { it(entry) }
    }
    val logRecorded = mutableListOf<(LogEntry) -> Unit>()

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
