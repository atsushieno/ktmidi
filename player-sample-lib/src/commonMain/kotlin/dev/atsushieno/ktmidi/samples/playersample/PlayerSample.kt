package dev.atsushieno.ktmidi.samples.playersample

import dev.atsushieno.ktmidi.Midi1Player
import dev.atsushieno.ktmidi.Midi2Music
import dev.atsushieno.ktmidi.Midi2Player
import dev.atsushieno.ktmidi.MidiAccess
import dev.atsushieno.ktmidi.MidiCIProtocolType
import dev.atsushieno.ktmidi.MidiMusic
import dev.atsushieno.ktmidi.MidiPlayer
import dev.atsushieno.ktmidi.read

data class CommandLineOptions(val api: String? = null, val port: String? = null, val musicFile: String? = null, val midi2: Boolean = false)

fun parseCommandLineArgs(args: Array<String>) = CommandLineOptions(
    api = args.firstOrNull { it.startsWith("-a:") }?.substring(3),
    port = args.firstOrNull { it.startsWith("-p:") }?.substring(3),
    musicFile = args.firstOrNull { it.startsWith("-m:") }?.substring(3),
    midi2 = args.contains("-2")
)

expect fun getMidiAccessApi(api: String?): MidiAccess
expect fun exitApplication(code: Int)
expect fun canReadFile(file: String): Boolean
expect fun getFileExtension(file: String): String
expect fun readFileContents(file: String): List<Byte>

fun showUsage(api: String?) {
    println("USAGE: PlayerSample [-a:api] [-p:port] [-2] [-m:music-file]")
    println()
    println("-2 indicates that it specifies MIDI 2.0 protocol to the port")
    println()
    println("Available ports for -p option:")
    getMidiAccessApi(api).outputs.forEach { println(" - ${it.id} : ${it.name}") }
}

suspend fun runMain(args: Array<String>) {
    val opts = parseCommandLineArgs(args)

    if (args.contains("-?") || args.contains("-h") || args.contains("--help")) {
        showUsage(opts.api)
        return
    }

    val access = getMidiAccessApi(opts.api)
    val portDetails = access.outputs.firstOrNull { it.id == opts.port }
        ?: access.outputs.firstOrNull { !it.name!!.contains("Through") }
        ?: access.outputs.firstOrNull()
    if (portDetails == null) {
        println("Could not connect to output device.")
        exitApplication(2)
    }

    lateinit var player: MidiPlayer
    val midiOutput = access.openOutput(portDetails!!.id)

    // load music from file
    if (opts.musicFile != null) {
        player = if (canReadFile(opts.musicFile)) {
            try {
                if (getFileExtension(opts.musicFile).lowercase() == "mid")
                    // MIDI1 player
                    Midi1Player(MidiMusic().apply { this.read(readFileContents(opts.musicFile)) }, midiOutput)
                else {
                    // MIDI2 player
                    // If -2 is specified, then the MidiOutput will receive UMPs. Otherwise, UMPs are converted to MIDI1.
                    // (it is kind of complicated, as it is also possible that the device can receive MIDI1 UMPs,
                    // but it is out of scope in this version.)
                    if (opts.midi2)
                        midiOutput.midiProtocol = MidiCIProtocolType.MIDI2
                    Midi2Player(Midi2Music().apply { this.read(readFileContents(opts.musicFile)) }, midiOutput)
                }
            } catch (ex: Exception) {
                println("File \"${opts.musicFile}\" could not be loaded: $ex")
                ex.printStackTrace()
                exitApplication(1)
                throw Exception("Should not reach here")
            }
        } else {
            println("File \"${opts.musicFile}\" does not exist.")
            showUsage(opts.api)
            exitApplication(1)
            throw Exception("Should not reach here")
        }
    }
    else {
        println("Music file was not specified.")
        showUsage(opts.api)
        exitApplication(1)
    }

    println("Using ${portDetails.name}")

    player.play()

    println("Type something[CR] to quit.")
    readln()
    player.stop()

    (0xB0 until 0xBF).map { it.toByte() }.forEach { i ->
        midiOutput.send(byteArrayOf(i, 120, 0, i, 123, 0), 0, 6, 9) }
    println("done.")

    return
}

