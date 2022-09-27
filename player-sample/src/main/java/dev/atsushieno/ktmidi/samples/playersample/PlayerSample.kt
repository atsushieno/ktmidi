package dev.atsushieno.ktmidi.samples.playersample

import dev.atsushieno.ktmidi.EmptyMidiAccess
import dev.atsushieno.ktmidi.JvmMidiAccess
import dev.atsushieno.ktmidi.Midi1Player
import dev.atsushieno.ktmidi.Midi2Music
import dev.atsushieno.ktmidi.Midi2Player
import dev.atsushieno.ktmidi.MidiMusic
import dev.atsushieno.ktmidi.MidiPlayer
import dev.atsushieno.ktmidi.RtMidiAccess
import dev.atsushieno.ktmidi.read
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

data class CommandLineOptions(val api: String? = null, val port: String? = null, val musicFile: String? = null)

fun parseCommandLineArgs(args: Array<String>) = CommandLineOptions(
    api = args.firstOrNull { it.startsWith("-a:") }?.substring(3),
    port = args.firstOrNull { it.startsWith("-p:") }?.substring(3),
    musicFile = args.firstOrNull { it.startsWith("-m:") }?.substring(3)
)

fun getMidiAccessApi(api: String?) = when (api) {
    "EMPTY" -> EmptyMidiAccess()
    "JVM" -> JvmMidiAccess()
    else -> RtMidiAccess()
}

fun showUsage(api: String?) {
    println("USAGE: PlayerSample [-a:api] [-p:port] [-m:music-file]")
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

    val access = RtMidiAccess()
    val portDetails = access.outputs.firstOrNull { it.id == opts.port }
        ?: access.outputs.firstOrNull { !it.name!!.contains("Through") }
        ?: access.outputs.firstOrNull()
    if (portDetails == null) {
        println("Could not connect to output device.")
        exitProcess(2)
    }

    lateinit var player: MidiPlayer
    val midiOutput = runBlocking { access.openOutputAsync(portDetails.id) }

    // load music from file
    if (opts.musicFile != null) {
        val file = File(opts.musicFile)
        player = if (file.exists() && File(opts.musicFile).canRead()) {
            try {
                if (file.extension.lowercase() == "mid")
                // MIDI1 player
                    Midi1Player(MidiMusic().apply { this.read(file.readBytes().asList()) }, midiOutput)
                else
                // MIDI2 player
                    Midi2Player(Midi2Music().apply { this.read(file.readBytes().asList()) }, midiOutput)
            } catch(ex: Exception) {
                println("File \"${opts.musicFile}\" could not be loaded: $ex")
                ex.printStackTrace()
                exitProcess(1)
            }
        } else {
            println("File \"${opts.musicFile}\" does not exist.")
            showUsage(opts.api)
            exitProcess(1)
        }
    }
    else {
        println("Music file was not specified.")
        showUsage(opts.api)
        exitProcess(1)
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
