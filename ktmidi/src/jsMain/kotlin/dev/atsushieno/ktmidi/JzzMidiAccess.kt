package dev.atsushieno.ktmidi

import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.Promise

private external fun require(module: String): dynamic


private suspend fun <T> Promise<T>.await(): T = suspendCoroutine { cont ->
    then({ cont.resumeWith(it.unsafeCast<Result<T>>()) }, { cont.resumeWithException(it) })
}

class JzzMidiAccess private constructor(val useSysex: Boolean, private val jzz: dynamic) : MidiAccess() {
    override val name: String
        get() = "JZZ"

    companion object {
        // We avoid exposing mismatches around suspend functions within this class itself by
        //  leaving asynchronous parts out of the class.
        suspend fun create(useSysex: Boolean) : JzzMidiAccess {
            val jzz = require("jzz") (useSysex)
            return JzzMidiAccess(useSysex, jzz)
        }
    }

    override val inputs: Iterable<MidiPortDetails>
        get() = jzz.info().inputs.map { item, index, _ -> JzzMidiPortDetails(item, index as Int, "jzz.in.$index") }
            .iterator().asSequence<MidiPortDetails>().asIterable()

    override val outputs: Iterable<MidiPortDetails>
        get() = jzz.info().outputs.map { item, index, _ -> JzzMidiPortDetails(item, index as Int, "jzz.out.$index") }
            .iterator().asSequence<MidiPortDetails>().asIterable()

    override suspend fun openInputAsync(portId: String): MidiInput {
        val details = inputs.firstOrNull { it.id == portId }
            ?: throw IllegalArgumentException("Invalid port ID was requested: $portId")
        val port = jzz.openMidiIn(details.name)
        return JzzMidiInput(port, details, MidiCIProtocolType.MIDI1)
    }

    override suspend fun openOutputAsync(portId: String): MidiOutput {
        val details = outputs.firstOrNull { it.id == portId }
            ?: throw IllegalArgumentException("Invalid port ID was requested: $portId")
        val port = jzz.openMidiOut(details.name)
        return JzzMidiOutput(port, details, MidiCIProtocolType.MIDI1)
    }
}

internal class JzzMidiPortDetails(
    val item: dynamic,
    val index: Int,
    override val id: String
) : MidiPortDetails {
    override val manufacturer: String? = item.manufacturer
    override val name: String? = item.name
    override val version: String? = item.version
}

internal abstract class JzzMidiPort(
    override val details: MidiPortDetails,
    override var midiProtocol: Int
) : MidiPort {
    private var state: MidiPortConnectionState = MidiPortConnectionState.OPEN
    override val connectionState
        get() = state
    override fun close() {
        state = MidiPortConnectionState.CLOSED
    }
}

internal class JzzMidiInput(private val impl: dynamic, details: MidiPortDetails, midiProtocol: Int)
    : JzzMidiPort(details, midiProtocol), MidiInput {

    private var listener: OnMidiReceivedEventListener? = null

    override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
        this.listener = listener
    }

    override fun close() {
        impl.close()
        super.close()
    }

    private val outFunc : dynamic = { midi : dynamic ->
        val arr = midi.data
        this.listener?.onEventReceived(arr, 0, arr.length, 0)
    }

    init {
        impl.connect(outFunc)
    }
}

internal class JzzMidiOutput(private val impl: dynamic, details: MidiPortDetails, midiProtocol: Int)
    : JzzMidiPort(details, midiProtocol), MidiOutput {

    override fun close() {
        impl.close()
        super.close()
    }

    // FIXME: we should come up with certain delaying queue instead of immediately delaying the processing...
    override fun send(mevent: ByteArray, offset: Int, length: Int, timestampInNanoseconds: Long) {
        // Due to signed-unsigned mismatch, we have to convert ByteArray to (unsigned) number array
        val arr = mevent.drop(offset).take(length).map { it.toUByte().toInt() }.toTypedArray().asDynamic()
        impl.wait(timestampInNanoseconds / 1000).send(arr, offset, length)
    }
}