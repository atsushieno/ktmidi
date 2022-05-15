package dev.atsushieno.ktmidi.samples.inputsample

import dev.atsushieno.ktmidi.OnMidiReceivedEventListener
import dev.atsushieno.ktmidi.RtMidiAccess
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    val access = RtMidiAccess()
    val portDetails = access.inputs.firstOrNull { !it.name!!.contains("Through") }
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
