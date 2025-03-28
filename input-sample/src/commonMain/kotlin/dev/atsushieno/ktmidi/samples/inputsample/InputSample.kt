package dev.atsushieno.ktmidi.samples.inputsample

import dev.atsushieno.ktmidi.*
import kotlinx.coroutines.runBlocking

expect fun getMidiAccessApi(api: String?, midiTransportProtocol: Int): MidiAccess
expect fun exitApplication(code: Int)
expect fun runLoop(body: ()->Unit)

data class CommandLineOptions(val api: String? = null, val port: String? = null, val musicFile: String? = null, val midi2: Boolean = false)

fun parseCommandLineArgs(args: Array<String>) = CommandLineOptions(
    api = args.firstOrNull { it.startsWith("-a:") }?.substring(3),
    port = args.firstOrNull { it.startsWith("-p:") }?.substring(3),
    midi2 = args.contains("-2")
)

fun showUsage(api: String?, midiTransportProtocol: Int) {
    println("USAGE: InputSample [-a:api] [-p:port]")
    println()
    println("Available ports for -p option:")
    getMidiAccessApi(api, midiTransportProtocol).inputs.forEach { println(" - ${it.id} : ${it.name}") }
}

fun runMain(args: Array<String>) {
    val opts = parseCommandLineArgs(args)
    val protocol = if (opts.midi2) MidiTransportProtocol.UMP else MidiTransportProtocol.MIDI1

    if (args.contains("-?") || args.contains("-h") || args.contains("--help")) {
        showUsage(opts.api, protocol)
        return
    }

    val access = getMidiAccessApi(opts.api, protocol)
    val portDetails = access.inputs.firstOrNull { it.id == opts.port }
        ?: access.inputs.firstOrNull { !it.name!!.contains("Through") }
        ?: access.inputs.firstOrNull()
    if (portDetails == null) {
        println("Could not connect to input device.")
        return
    }

    access.stateChanged = { state, port ->
        println("Device State Changed: $state $port")
    }

    println("Using ${access.name}, port: ${portDetails.name}")

    val midiInput = runBlocking { access.openInput(portDetails.id) }
    midiInput.setMessageReceivedListener(InputTester())

    runLoop {
        println("Type something[CR] to quit.")
        readln()
        println("done.")
    }
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
