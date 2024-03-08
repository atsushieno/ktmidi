package dev.atsushieno.ktmidi

import android.app.Service
import android.media.midi.*
import android.content.Context
import android.os.Build
import kotlinx.coroutines.delay

class AndroidMidi2Access(applicationContext: Context, private val includeMidi1Transport: Boolean = false) : AndroidMidiAccess(applicationContext) {
    override val ports : List<MidiPortDetails>
        get() =
            (if (includeMidi1Transport) ports1 else listOf())
                .flatMap { d -> d.ports.map { port -> Pair(d, port) } }
                .map { pair -> AndroidPortDetails(pair.first, pair.second, 1) } +
                    ports2.flatMap { d -> d.ports.map { port -> Pair(d, port) } }
                        .map { pair -> AndroidPortDetails(pair.first, pair.second, 2) }
    private val ports2: List<MidiDeviceInfo>
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                manager.getDevicesForTransport(MidiManager.TRANSPORT_UNIVERSAL_MIDI_PACKETS).toList()
            else
                manager.devices.toList()
}
open class AndroidMidiAccess(applicationContext: Context) : MidiAccess() {
    override val name: String
        get() = "AndroidSDK"

    val manager: MidiManager = applicationContext.getSystemService(Service.MIDI_SERVICE) as MidiManager
    protected open val ports : List<MidiPortDetails>
        get() = ports1.flatMap { d -> d.ports.map { port -> Pair(d, port) } }
            .map { pair -> AndroidPortDetails(pair.first, pair.second, 1) }
    @Suppress("DEPRECATION") // cannot linter track this conditional code while it can detect unguarded invocation?
    val ports1: List<MidiDeviceInfo>
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                manager.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM).toList()
            else
                manager.devices.toList()
    internal val openDevices = mutableListOf<MidiDevice>()

    // Note that "input" and "output" are flip side in Android and javax.sound.midi. We choose saner direction.
    override val inputs: Iterable<MidiPortDetails>
        get() = ports.map { it as AndroidPortDetails }.filter { p -> p.portInfo.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }.asIterable()
    override val outputs: Iterable<MidiPortDetails>
        get() = ports.map { it as AndroidPortDetails }.filter { p -> p.portInfo.type == MidiDeviceInfo.PortInfo.TYPE_INPUT }.asIterable()

    override suspend fun openInput(portId: String): MidiInput {
        val ip = inputs.first { i -> i.id == portId } as AndroidPortDetails
        val dev = openDevices.firstOrNull { d -> ip.device.id == d.info.id }
        val l = OpenDeviceListener(this, dev, ip)
        return l.openInput()
    }

    override suspend fun openOutput(portId: String): MidiOutput {
        val ip = outputs.first { i -> i.id == portId } as AndroidPortDetails
        val dev = openDevices.firstOrNull { d -> ip.device.id == d.info.id }
        val l = OpenDeviceListener(this, dev, ip)
        return l.openOutput()
    }
}

class AndroidPortDetails(val device: MidiDeviceInfo, val portInfo: MidiDeviceInfo.PortInfo,
                                 override val midiTransportProtocol: Int
) : MidiPortDetails {
    private val significantPortName = if (portInfo.name != "input" && portInfo.name != "output") portInfo.name else null
    override val id: String
        get() = "${this.name}_${portInfo.type}_${portInfo.portNumber}"
    override val manufacturer
        get() = device.properties.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER)
    override val name: String?
        get() = device.properties.getString(MidiDeviceInfo.PROPERTY_NAME) + (if (significantPortName.isNullOrEmpty()) "" else ": $significantPortName")
    override val version: String?
        get() = device.properties.getString(MidiDeviceInfo.PROPERTY_VERSION)
}

private class OpenDeviceListener(val parent: AndroidMidiAccess, var device: MidiDevice?, val portToOpen: AndroidPortDetails)
    : MidiManager.OnDeviceOpenedListener {

    suspend fun openInput () : AndroidMidiInput {
        return open { dev -> AndroidMidiInput(portToOpen, dev.openOutputPort (portToOpen.portInfo.portNumber)) }
    }

    suspend fun openOutput() : AndroidMidiOutput {
        return open { dev -> AndroidMidiOutput (portToOpen, dev.openInputPort (portToOpen.portInfo.portNumber)) }
    }

    suspend fun <T>open (resultCreator: (MidiDevice) -> T) : T {
        if (device == null) {
            parent.manager.openDevice(portToOpen.device, this@OpenDeviceListener, null)
            for (i in 0 until 10)
                if (device == null)
                    delay(10)
            while (device == null)
                delay(100)
        }
        return resultCreator(device!!)
    }

    override fun onDeviceOpened (device:MidiDevice) {
        this.device = device
        parent.openDevices.add (device)
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
}

private class AndroidMidiInput(portDetails: AndroidPortDetails, private val impl: MidiOutputPort)
    : AndroidPort(portDetails, { impl.close() }), MidiInput {

    class Receiver(private val parent: AndroidMidiInput) : MidiReceiver() {

        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            parent.messageReceived.onEventReceived(msg, offset, count, timestamp)
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

    init {
        impl.connect(Receiver(this))
    }
}

private class AndroidMidiOutput(portDetails: AndroidPortDetails, private val impl: MidiInputPort)
    : AndroidPort(portDetails, { impl.close() }), MidiOutput {

    override fun send(mevent: ByteArray, offset: Int, length: Int, timestampInNanoseconds: Long) {
        impl.send (mevent, offset, length, timestampInNanoseconds);
    }
}
