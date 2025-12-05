package dev.atsushieno.ktmidi.musicdevice

import dev.atsushieno.ktmidi.MidiInput
import dev.atsushieno.ktmidi.MidiOutput
import dev.atsushieno.ktmidi.OnMidiReceivedEventListener
import dev.atsushieno.ktmidi.ci.ClientConnection
import dev.atsushieno.ktmidi.ci.MidiCIChannelList
import dev.atsushieno.ktmidi.ci.MidiCIDevice
import dev.atsushieno.ktmidi.ci.MidiCIDeviceInfo
import dev.atsushieno.ktmidi.ci.MidiCIException
import dev.atsushieno.ktmidi.ci.ObservablePropertyList
import dev.atsushieno.ktmidi.ci.json.Json
import dev.atsushieno.ktmidi.ci.propertycommonrules.MidiCIControl
import dev.atsushieno.ktmidi.ci.propertycommonrules.MidiCIStateEntry
import dev.atsushieno.ktmidi.ci.propertycommonrules.allCtrlList
import dev.atsushieno.ktmidi.ci.propertycommonrules.chCtrlList
import dev.atsushieno.ktmidi.ci.propertycommonrules.stateList
import kotlinx.coroutines.delay

interface MusicDeviceInputReceiver {
    val inputReceivers: List<(bytes: List<Byte>, offset: Int, length: Int, timestampInNanoseconds: Long) -> Unit>
}

interface MusicDeviceOutputSender {
    fun send(bytes: List<Byte>, offset: Int, length: Int, timestampInNanoseconds: Long)
}

// It helps determine which MIDI-CI to connect among the endpoints it discovered.
class MusicDeviceConnector(
    private val receiver: MusicDeviceInputReceiver,
    private val sender: MusicDeviceOutputSender,
    private val ciSession: MidiCISession
) {
    class MidiInputReceiver(input: MidiInput) : MusicDeviceInputReceiver {
        val r = OnMidiReceivedEventListener { data, start, length, timestampInNanoseconds ->
            inputReceivers.forEach { it(data.asList(), start, length, timestampInNanoseconds) }
        }

        override val inputReceivers = mutableListOf<(bytes: List<Byte>, offset: Int, length: Int, timestampInNanoseconds: Long) -> Unit>()

        init {
            input.setMessageReceivedListener(r)
        }
    }

    class MidiOutputSender(private val output: MidiOutput) : MusicDeviceOutputSender {
        override fun send(bytes: List<Byte>, offset: Int, length: Int, timestampInNanoseconds: Long) =
            output.send(bytes.toByteArray(), offset, length, timestampInNanoseconds)
    }

    suspend fun connect(): MusicDevice {
        var time: Long = 0
        ciDevice.sendDiscovery()
        while (true) {
            delay(discoveryWaitInMilliseconds)
            time += discoveryTimeoutMilliseconds
            if (time >= discoveryTimeoutMilliseconds)
                throw MidiCIException("MIDI-CI discovery timeout")
            val muid = selectTargetEndpoint(ciDevice)
            if (muid != 0)
                return MusicDevice(sender, muid, ciSession)
        }
    }

    fun send(data: List<Byte>, offset: Int, length: Int, timestampInNanoseconds: Long) =
        sender.send(data, offset, length, timestampInNanoseconds)

    private val ciDevice by ciSession::device

    // determine which MIDI-CI device among detected endpoints
    // By default, it selects the first MIDI-CI device it found.
    var selectTargetEndpoint: (device: MidiCIDevice) -> Int =
        { device -> device.connections.values.firstOrNull()?.targetMUID ?: 0 }

    var discoveryWaitInMilliseconds: Long = 100
    var discoveryTimeoutMilliseconds: Long = 10000
}

// MidiOutput delegate that supports MIDI-CI property retrieval.
class MusicDevice(
    private val sender: MusicDeviceOutputSender,
    private val targetMUID: Int,
    private val ciSession: MidiCISession
) {
    private val connection: ClientConnection?
        get() = ciSession.device.connections[targetMUID]

    val deviceInfo: MidiCIDeviceInfo?
        get() = connection?.deviceInfo
    val channelList: MidiCIChannelList?
        get() = connection?.channelList
    val jsonSchema: Json.JsonValue?
        get() = connection?.jsonSchema
    private val properties: ObservablePropertyList?
        get() = connection?.propertyClient?.properties
    val stateList: List<MidiCIStateEntry>?
        get() = properties?.stateList
    val allCtrlList: List<MidiCIControl>?
        get() = properties?.allCtrlList
    val chCtrlList: List<MidiCIControl>?
        get() = properties?.chCtrlList

    var propertyBinaryGetter: (propertyId: String, resId: String?) -> List<Byte>?
        get() = ciSession.device.propertyHost.propertyBinaryGetter
        set(value) { ciSession.device.propertyHost.propertyBinaryGetter = value }

    fun send(data: List<Byte>, offset: Int, length: Int, timestampInNanoseconds: Long) =
        sender.send(data, offset, length, timestampInNanoseconds)
}
