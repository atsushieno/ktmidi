package dev.atsushieno.ktmidi

import javax.sound.midi.*

internal typealias JvmMidiMessage = javax.sound.midi.MidiMessage

class JvmMidiAccess : MidiAccess() {
    override val name: String
        get() = "JVM"
    override val inputs: Iterable<MidiPortDetails>
        get() = MidiSystem.getMidiDeviceInfo().map { i -> MidiSystem.getMidiDevice(i) }
            .flatMap {
                // make sure the device has an input port that can be retrieved
                if (it.maxTransmitters == -1 || it.maxTransmitters > it.transmitters.count()) {
                    listOf(it to it.transmitter)
                } else {
                    emptyList()
                }
            }
            .mapIndexed { i, p -> JvmMidiTransmitterPortDetails(p.first, i, p.second) }
    override val outputs: Iterable<MidiPortDetails>
        get() = MidiSystem.getMidiDeviceInfo().map { i -> MidiSystem.getMidiDevice(i) }
            .flatMap {
                // make sure the device has an output port that can be retrieved
                if (it.maxReceivers == -1 || it.maxReceivers > it.receivers.count()) {
                    listOf(it to it.receiver)
                } else {
                    emptyList()
                }
            }
            .mapIndexed { i, p -> JvmMidiReceiverPortDetails(p.first, i, p.second) }

    override suspend fun openInput(portId: String): MidiInput {
        val port = inputs.firstOrNull { i -> i.id == portId }
        if (port == null || port !is JvmMidiTransmitterPortDetails)
            throw IllegalArgumentException("Input port $portId was not found")
        if (!port.device.isOpen)
            port.device.open()
        return JvmMidiInput(port)
    }

    override suspend fun openOutput(portId: String): MidiOutput {
        val port = outputs.firstOrNull { i -> i.id == portId }
        if (port == null || port !is JvmMidiReceiverPortDetails)
            throw IllegalArgumentException("Output port $portId was not found")
        if (!port.device.isOpen)
            port.device.open()
        return JvmMidiOutput(port)
    }

    override suspend fun createVirtualInputSender(context: PortCreatorContext): MidiOutput {
        throw UnsupportedOperationException()
    }

    override suspend fun createVirtualOutputReceiver(context: PortCreatorContext): MidiInput {
        throw UnsupportedOperationException()
    }
}

internal abstract class JvmMidiPortDetails(override val id: String, info: MidiDevice.Info) : MidiPortDetails {
    override val manufacturer: String? = info.vendor
    override val name: String? = info.name
    override val version: String? = info.version
}

private class JvmMidiTransmitterPortDetails(val device: MidiDevice, portIndex: Int, val transmitter: Transmitter) :
    JvmMidiPortDetails("InPort$portIndex", device.deviceInfo)

private class JvmMidiReceiverPortDetails(val device: MidiDevice, portIndex: Int, val receiver: Receiver) :
    JvmMidiPortDetails("OutPort$portIndex", device.deviceInfo)

private fun toJvmMidiMessage(data: ByteArray, start: Int, length: Int): JvmMidiMessage {
    if (length <= 0) throw IllegalArgumentException("non-positive length")
    val arr = if (start == 0 && length == data.size) data else data.drop(start).take(length - start).toByteArray()
    return when (arr[0]) {
        0xF0.toByte() -> SysexMessage(arr, length)
        0xFF.toByte() -> MetaMessage(arr[1].toInt(), arr.drop(2).toByteArray(), length - 2)
        else -> ShortMessage(arr[0].toInt(), arr[1].toInt(), if (length > 2) arr[2].toInt() else 0)
    }
}

private class JvmMidiInput(val port: JvmMidiTransmitterPortDetails) : MidiInput {

    override val details: MidiPortDetails = port

    private val state: MidiPortConnectionState = MidiPortConnectionState.OPEN

    override val connectionState: MidiPortConnectionState
        get() = state

    override fun close() {
        port.transmitter.close()
    }

    override var midiProtocol: Int
        get() = MidiCIProtocolValue.MIDI1
        set(_) = throw UnsupportedOperationException("This MidiPort implementation does not support promoting MIDI protocols")

    private var listener: OnMidiReceivedEventListener? = null

    override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
        this.listener = listener
    }

    init {
        port.transmitter.receiver = object : Receiver {
            override fun close() {}

            override fun send(msg: JvmMidiMessage?, timestampInMicroseconds: Long) {
                if (msg == null)
                    return
                listener?.onEventReceived(msg.message, 0, msg.length, timestampInMicroseconds * 1000)
            }
        }
    }
}

private class JvmMidiOutput(val port: JvmMidiReceiverPortDetails) : MidiOutput {

    override val details: MidiPortDetails
        get() = port

    private val state: MidiPortConnectionState = MidiPortConnectionState.OPEN

    override val connectionState: MidiPortConnectionState
        get() = state

    override var midiProtocol: Int
        get() = MidiCIProtocolValue.MIDI1
        set(_) = throw UnsupportedOperationException("This MidiPort implementation does not support promoting MIDI protocols")

    override fun close() {
        port.receiver.close()
    }

    override fun send(mevent: ByteArray, offset: Int, length: Int, timestampInNanoseconds: Long) {
        val msg = toJvmMidiMessage(mevent, offset, length)
        port.receiver.send(msg, timestampInNanoseconds)
    }
}
