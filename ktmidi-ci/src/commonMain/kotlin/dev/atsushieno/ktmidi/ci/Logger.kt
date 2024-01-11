package dev.atsushieno.ktmidi.ci

class Logger {
    val logEventReceived = mutableListOf<(msg: Any)->Unit>()

    fun logMessage(msg: Any) {
        logEventReceived.forEach { it(msg) }
    }

    fun nak(data: List<Byte>) {
        logMessage("- NAK(${data.joinToString { it.toString(16) }}")
    }

    fun getPropertyData(msg: Message.GetPropertyData) = logMessage(msg)
    fun logError(message: String) = logMessage("Error: $message")
}