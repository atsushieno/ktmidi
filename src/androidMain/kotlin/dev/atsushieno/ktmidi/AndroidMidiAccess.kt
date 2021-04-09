package dev.atsushieno.ktmidi

import android.app.Service
import android.media.midi.*
import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore

class AndroidMidiAccess(applicationContext: Context) : MidiAccess() {
    internal val manager: MidiManager = applicationContext.getSystemService(Service.MIDI_SERVICE) as MidiManager
    private val ports : List<AndroidPortDetails> = manager.devices.flatMap { d -> d.ports.map { port -> Pair(d, port) } }
        .map { pair -> AndroidPortDetails(pair.first, pair.second) }

    internal val openDevices = mutableListOf<MidiDevice>()

    // Note that "input" and "output" are flip side in Android and javax.sound.midi. We choose saner direction.
    override val inputs: Iterable<MidiPortDetails>
        get() = ports.filter { p -> p.portInfo.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }.asIterable()
    override val outputs: Iterable<MidiPortDetails>
        get() = ports.filter { p -> p.portInfo.type == MidiDeviceInfo.PortInfo.TYPE_INPUT }.asIterable()

    override fun openInputAsync(portId: String): MidiInput {
        val ip = inputs.first { i -> i.id == portId } as AndroidPortDetails
        val dev = openDevices.firstOrNull { d -> ip.device.id == d.info.id }
        val l = OpenDeviceListener(this, dev, ip)
        return l.openInput()
    }

    override fun openOutputAsync(portId: String): MidiOutput {
        val ip = outputs.first { i -> i.id == portId } as AndroidPortDetails
        val dev = openDevices.firstOrNull { d -> ip.device.id == d.info.id }
        val l = OpenDeviceListener(this, dev, ip)
        return l.openOutput()
    }
}

private class AndroidPortDetails(val device: MidiDeviceInfo, val portInfo: MidiDeviceInfo.PortInfo) : MidiPortDetails {
    override val id: String
        get() = "${this.name}_${portInfo.portNumber}"
    override val manufacturer
        get() = device.properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER)
    override val name: String?
        get() = device.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
    override val version: String?
        get() = device.properties.getString(MidiDeviceInfo.PROPERTY_VERSION)
}

private class OpenDeviceListener(val parent: AndroidMidiAccess, var device: MidiDevice?, val portToOpen: AndroidPortDetails)
    : MidiManager.OnDeviceOpenedListener {

    fun openInput () : AndroidMidiInput {
        return open { dev -> AndroidMidiInput(portToOpen, dev.openOutputPort (portToOpen.portInfo.portNumber)) }
    }

    fun openOutput() : AndroidMidiOutput {
        return open { dev -> AndroidMidiOutput (portToOpen, dev.openInputPort (portToOpen.portInfo.portNumber)) }
    }

    fun <T>open (resultCreator: (MidiDevice) -> T) : T {
        runBlocking {
            if (device == null) {
                parent.manager.openDevice(portToOpen.device, this@OpenDeviceListener, null)
                creatorLock.acquire()
            }
        }
        return resultCreator(device!!)
    }

    private val creatorLock = Semaphore(1, 0)

    override fun onDeviceOpened (device:MidiDevice) {
        this.device = device
        parent.openDevices.add (device)
        creatorLock.release()
    }
}


private abstract class AndroidPort(override val details: AndroidPortDetails, private val onClose: () -> Unit) : MidiPort {

    private var state: MidiPortConnectionState = MidiPortConnectionState.OPEN
    override val connectionState: MidiPortConnectionState
        get() = state

    override fun close() {
        onClose ()
        state = MidiPortConnectionState.CLOSED
    }

    override var midiProtocol: Int
        get() = MidiCIProtocolValue.MIDI1
        set(_) = throw UnsupportedOperationException("This MidiPort implementation does not support promoting MIDI protocols")
}

private class AndroidMidiInput(portDetails: AndroidPortDetails, private val impl: MidiOutputPort)
    : AndroidPort(portDetails, { impl.close() }), MidiInput {

    class Receiver(private val parent: AndroidMidiInput) : MidiReceiver() {

        override fun onSend(msg: ByteArray?, offset: Int, count: Int, timestamp: Long) {
            parent.messageReceived.onEventReceived(msg!!, timestamp.toInt(), offset, count.toLong())
        }
    }

    override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
        messageReceived = listener
    }

    private val receiver = Receiver(this)
    var messageReceived : OnMidiReceivedEventListener = object: OnMidiReceivedEventListener {
        override fun onEventReceived(data: ByteArray, start: Int, length: Int, timestamp: Long) {}
        }

    override fun close() {
        impl.disconnect(receiver)
        super.close()
    }
}

private class AndroidMidiOutput(portDetails: AndroidPortDetails, private val impl: MidiInputPort)
    : AndroidPort(portDetails, { impl.close() }), MidiOutput {

    override fun send(mevent: ByteArray, offset: Int, length: Int, timestamp: Long) {
        impl.send (mevent, offset, length, timestamp);
    }
}
