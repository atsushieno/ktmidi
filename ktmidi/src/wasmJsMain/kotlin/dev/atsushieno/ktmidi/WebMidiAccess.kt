package dev.atsushieno.ktmidi

import MIDIAccess
import MIDIInput
import MIDIMessageEvent
import MIDIOutput
import MIDIPort
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

fun processRequestMidiAccess(): Unit = js("""{
navigator.permissions.query({ name: "midi" }).then((result) => {
    console.log("ktmidi: requesting permission for Web MIDI Access: " + result.state);
    if (result.state == "prompt" || result.state =="granted") {
        navigator.requestMIDIAccess({"sysex": true, "software": true})
            .then((access) => {
                console.log("ktmidi: Web MIDI Access is ready for ktmidi-ci-tool");
                document["ktmidi_wasmJs_midiAccess"] = access;
            });
    }
});
}""")

private val midiAccess : MIDIAccess?
    get() = getCurrentMidiAccess()
private fun getCurrentMidiAccess() : MIDIAccess? = js("document['ktmidi_wasmJs_midiAccess']")

class WebMidiAccess : MidiAccess() {
    override val name: String
        get() = "WebMIDI"
    override val inputs: Iterable<MidiPortDetails>
        get() {
            val list = mutableListOf<MIDIInput>()
            midiAccess?.inputs?.forEach({i, _, _ -> list.add(i) })
            return list.map { WebMidiPortDetails(it) }
        }
    override val outputs: Iterable<MidiPortDetails>
        get() {
            val list = mutableListOf<MIDIOutput>()
            midiAccess?.outputs?.forEach({i, _, _ -> list.add(i) })
            return list.map { WebMidiPortDetails(it) }
        }

    override suspend fun openInput(portId: String): MidiInput =
        WebMidiInput(inputs.first { it.id == portId } as WebMidiPortDetails)

    override suspend fun openOutput(portId: String): MidiOutput =
        WebMidiOutput(outputs.first { it.id == portId } as WebMidiPortDetails)

    init {
        processRequestMidiAccess()
    }
}

private class WebMidiPortDetails(val port: MIDIPort) : MidiPortDetails {
    override val id: String
        get() = port.id
    override val manufacturer: String?
        get() = port.manufacturer
    override val name: String?
        get() = port.name
    override val version: String?
        get() = port.version
    override val midiTransportProtocol: Int
        get() = 1
}

internal abstract class WebMidiPort(final override val details: MidiPortDetails) : MidiPort {
    protected val port = (details as WebMidiPortDetails).port

    override val connectionState: MidiPortConnectionState
        get() = MidiPortConnectionState.OPEN // always

    override fun close() {
        // Web MIDI API does not "close" ports
    }
}

private class WebMidiInput(
    details: WebMidiPortDetails
) : WebMidiPort(details), MidiInput {
    val input = port as MIDIInput
    private var listener: OnMidiReceivedEventListener? = null
    override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
        this.listener = listener
    }

    init {
        input.addEventListener("midimessage") { ev ->
            val l = listener
            if (l != null) {
                val midiEvent = ev as MIDIMessageEvent
                val array: ByteArray = midiEvent.data.toKotlinByteArray()
                l.onEventReceived(array, 0, array.size, midiEvent.timeStamp.toDouble().toLong() * 1000)
            }
        }
    }
}

private class WebMidiOutput(
    details: WebMidiPortDetails
) : WebMidiPort(details), MidiOutput {
    val output = port as MIDIOutput
    override fun send(mevent: ByteArray, offset: Int, length: Int, timestampInNanoseconds: Long) {
        val array = mevent.drop(offset).take(length).toUint8Array()
        // FIXME: enable timestamp.
        //  If we enable it - "TypeError: Cannot convert a BigInt value to a number"
        output.send(array)//, timestampInNanoseconds / 1000)
    }
}

// These converters are not very efficient, but they seem the best we can do, so far.
internal fun Uint8Array.toKotlinByteArray(): ByteArray =
    (byteOffset until byteOffset + byteLength).map { this[it] }.toByteArray()

internal fun List<Byte>.toUint8Array(): Uint8Array {
    val ret = Uint8Array(size)
    val src = JsArray<JsNumber>()
    this@toUint8Array.map { it.toUnsigned() }.forEachIndexed { index, it -> src.set(index, it.toJsNumber()) }
    ret.set(src)
    return ret
}
