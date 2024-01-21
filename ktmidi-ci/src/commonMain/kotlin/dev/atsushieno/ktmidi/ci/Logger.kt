package dev.atsushieno.ktmidi.ci

import kotlinx.datetime.LocalDateTime

enum class MessageDirection {
    None,
    In,
    Out
}

data class LogEntry(val timestamp: LocalDateTime, val direction: MessageDirection, val data: Any) {
    override fun toString() = "[${timestamp.time.toString().substring(0, 8)}] $data}"
}

class Logger {
    val logEventReceived = mutableListOf<(msg: Any, direction: MessageDirection)->Unit>()

    fun logMessage(msg: Any, direction: MessageDirection) {
        logEventReceived.forEach { it(msg, direction) }
    }

    fun nak(common: Message.Common, data: List<Byte>, direction: MessageDirection) {
        logMessage("Unrecognized message with unknown SubID2. ($common, ${data.joinToString { it.toString(16) }}", direction)
    }

    fun logError(message: String) = logMessage("Error: $message", MessageDirection.None)
}