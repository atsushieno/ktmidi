package dev.atsushieno.ktmidi.samples.playersample

import dev.atsushieno.ktmidi.*

data class CommandLineOptions(val api: String? = null, val port: String? = null, val musicFile: String? = null, val midi2: Boolean = false, val ump: Boolean = false)

fun parseCommandLineArgs(args: Array<String>) = CommandLineOptions(
    api = args.firstOrNull { it.startsWith("-a:") }?.substring(3),
    port = args.firstOrNull { it.startsWith("-p:") }?.substring(3),
    musicFile = args.firstOrNull { it.startsWith("-m:") }?.substring(3),
    midi2 = args.contains("-2"),
    ump = args.contains("-u")
)

expect fun getMidiAccessApi(api: String?, midiTransportProtocol: Int): MidiAccess
expect fun exitApplication(code: Int)
expect fun canReadFile(file: String): Boolean
expect fun getFileExtension(file: String): String
expect fun readFileContents(file: String): List<Byte>

fun showUsage(api: String?, midiTransportProtocol: Int) {
    println("USAGE: PlayerSample [-a:api] [-p:port] [-2] [-m:music-file]")
    println()
    println("-2 indicates that it specifies MIDI 2.0 protocol to the port")
    println()
    println("Available ports for -p option:")
    getMidiAccessApi(api, midiTransportProtocol).outputs.forEach { println(" - ${it.id} : ${it.name}") }
}

suspend fun runMain(args: Array<String>) {
    val opts = parseCommandLineArgs(args)
    val protocol = if (opts.ump) MidiTransportProtocol.UMP else MidiTransportProtocol.MIDI1

    if (args.contains("-?") || args.contains("-h") || args.contains("--help")) {
        showUsage(opts.api, protocol)
        return
    }

    // If -2 is specified, then the MidiOutput will receive UMPs. Otherwise, UMPs are converted to MIDI1.
    val access = getMidiAccessApi(opts.api, protocol)
    val portDetails = access.outputs.firstOrNull {
        it.id == opts.port
    }
        ?: access.outputs.firstOrNull { !it.name!!.contains("Through") }
        ?: access.outputs.firstOrNull()
    if (portDetails == null) {
        println("Could not connect to output device.")
        exitApplication(2)
        return
    }

    lateinit var player: MidiPlayer
    val midiOutput = access.openOutput(portDetails.id)

    // load music from file
    if (opts.musicFile != null) {
        player = if (canReadFile(opts.musicFile)) {
            try {
                if (getFileExtension(opts.musicFile).lowercase() == "mid")
                    // MIDI1 player
                    Midi1Player(Midi1Music().apply { this.read(readFileContents(opts.musicFile)) }, midiOutput)
                else {
                    // MIDI2 player.
                    // Note that the device transport might be MIDI1, where it will down-translate UMP to MIDI1 bytestream.
                    // Also note that the UMP-based song file might contain MIDI1 UMP messages, which is still handled by this player.
                    Midi2Player(Midi2Music().apply { this.read(readFileContents(opts.musicFile), true) }, midiOutput)
                }
            } catch (ex: Exception) {
                println("File \"${opts.musicFile}\" could not be loaded: $ex")
                ex.printStackTrace()
                exitApplication(1)
                throw Exception("Should not reach here")
            }
        } else {
            println("File \"${opts.musicFile}\" does not exist.")
            showUsage(opts.api, protocol)
            exitApplication(1)
            throw Exception("Should not reach here")
        }
    }
    else {
        println("Music file was not specified.")
        showUsage(opts.api, protocol)
        exitApplication(1)
        return
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

