package dev.atsushieno.ktmidi.samples.inputsample

import dev.atsushieno.ktmidi.EmptyMidiAccess
import dev.atsushieno.ktmidi.JvmMidiAccess
import dev.atsushieno.ktmidi.OnMidiReceivedEventListener
import dev.atsushieno.ktmidi.RtMidiAccess
import kotlinx.coroutines.runBlocking

data class CommandLineOptions(val api: String? = null, val port: String? = null)

fun parseCommandLineArgs(args: Array<String>) = CommandLineOptions(
    api = args.firstOrNull { it.startsWith("-a:") }?.substring(3),
    port = args.firstOrNull { it.startsWith("-p:") }?.substring(3)
)

fun getMidiAccessApi(api: String?) = when (api) {
    "EMPTY" -> EmptyMidiAccess()
    "JVM" -> JvmMidiAccess()
    else -> RtMidiAccess()
}

fun showUsage(api: String?) {
    println("USAGE: PlayerSample [-a:api] [-p:port]")
    println()
    println("Available ports for -p option:")
    getMidiAccessApi(api).outputs.forEach { println(" - ${it.id} : ${it.name}") }
}

fun main(args: Array<String>) {
    val opts = parseCommandLineArgs(args)

    if (args.contains("-?") || args.contains("-h") || args.contains("--help")) {
        showUsage(opts.api)
        return
    }

    val access = getMidiAccessApi(opts.api)
    val portDetails = access.inputs.firstOrNull { it.id == opts.port }
        ?: access.inputs.firstOrNull { !it.name!!.contains("Through") }
        ?: access.inputs.firstOrNull()
    if (portDetails == null) {
        println("Could not connect to input device.")
        return
    }

    println("Using ${portDetails.name}")

    val midiInput = runBlocking { access.openInputAsync(portDetails.id) }
    midiInput.setMessageReceivedListener(InputTester())

    println("Type something[CR] to quit.")
    readln()
    println("done.")
}

class InputTester : OnMidiReceivedEventListener {
    override fun onEventReceived(data: ByteArray, start: Int, length: Int, timestampInNanoseconds: Long) {
        print("received: ")
        data.sliceArray(start until start + length).forEach {
            print("${it.toUByte().toString(16)}h ") // print e.g. "received: 90h 35h 64h "
        }
        println()
    }
}
