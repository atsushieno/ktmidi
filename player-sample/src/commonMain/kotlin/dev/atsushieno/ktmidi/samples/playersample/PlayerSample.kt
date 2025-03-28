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
    println("USAGE: PlayerSample [-a:api] [-p:port] [-u] [-2] [-m:music-file]")
    println()
    println("-u indicates that it uses MIDI 2.0 UMP")
    println("-2 indicates that it specifies MIDI 2.0 protocol to the port (UMP only)")
    println()
    println("Available ports for -p option:")
    getMidiAccessApi(api, midiTransportProtocol).outputs.forEach { println(" - ${it.id} : ${it.name}") }
}

suspend fun runMain(args: Array<String>) {
    val opts = parseCommandLineArgs(args)
    val protocol = if (opts.midi2) MidiTransportProtocol.UMP else MidiTransportProtocol.MIDI1

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
    player = if (opts.musicFile != null && canReadFile(opts.musicFile)) {
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
        if (opts.ump)
            Midi2Player(Midi2Music().apply {
                tracks.add(Midi2Track().apply {
                    if (opts.midi2) {
                        messages.add(Ump(UmpFactory.midi2CC(0, 0, MidiCC.VOLUME, 0xF8000000)))
                        messages.add(Ump(UmpFactory.midi2CC(0, 0, MidiCC.EXPRESSION, 0xF8000000)))
                        messages.add(Ump(UmpFactory.deltaClockstamp(16)))
                        messages.add(Ump(UmpFactory.midi2NoteOn(0, 0, 0x40, 0, 0xF800, 0)))
                        messages.add(Ump(UmpFactory.deltaClockstamp(192)))
                        messages.add(Ump(UmpFactory.midi2NoteOff(0, 0, 0x40, 0, 0, 0)))
                    } else {
                        messages.add(Ump(UmpFactory.midi1CC(0, 0, MidiCC.VOLUME.toByte(), 0x78)))
                        messages.add(Ump(UmpFactory.midi1CC(0, 0, MidiCC.EXPRESSION.toByte(), 0x78)))
                        messages.add(Ump(UmpFactory.deltaClockstamp(16)))
                        messages.add(Ump(UmpFactory.midi1NoteOn(0, 0, 0x40, 0x78)))
                        messages.add(Ump(UmpFactory.deltaClockstamp(192)))
                        messages.add(Ump(UmpFactory.midi1NoteOff(0, 0, 0x40, 0)))
                    }
                })
            }, midiOutput)
        else
            Midi1Player(Midi1Music().apply {
                tracks.add(Midi1Track().apply {
                    events.add(Midi1Event(0, Midi1SimpleMessage(MidiChannelStatus.CC, MidiCC.VOLUME, 0x78))) // volume 120
                    events.add(Midi1Event(0, Midi1SimpleMessage(MidiChannelStatus.CC, MidiCC.EXPRESSION, 0x78))) // expression 120
                    events.add(Midi1Event(16, Midi1SimpleMessage(MidiChannelStatus.NOTE_ON, 0x40, 0x78)))
                    events.add(Midi1Event(192, Midi1SimpleMessage(MidiChannelStatus.NOTE_OFF, 0x40, 0)))
                })
            }, midiOutput)
    }

    println("Using ${access.name}, port: ${portDetails.name}")

    player.play()

    println("Type something[CR] to quit.")
    readln()
    player.stop()

    // all sound off / all notes off
    (0xB0 until 0xBF).map { it.toByte() }.forEach { i ->
        midiOutput.send(byteArrayOf(i, 120, 0, i, 123, 0), 0, 6, 9) }
    println("done.")

    return
}

