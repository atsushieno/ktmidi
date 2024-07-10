package dev.atsushieno.ktmidi

import dev.atsushieno.alsakt.*

internal fun Byte.toUnsigned() : Int = if (this < 0) this + 0x100 else this.toInt()

class AlsaMidiAccess : MidiAccess() {

    companion object {
        private const val midi_port_type = AlsaPortType.MidiGeneric or AlsaPortType.Application

        private val system_watcher: AlsaSequencer = AlsaSequencer(AlsaIOType.Duplex, AlsaIOMode.NonBlocking)

        private const val input_requirements = AlsaPortCapabilities.Read or AlsaPortCapabilities.SubsRead
        private const val output_requirements = AlsaPortCapabilities.Write or AlsaPortCapabilities.SubsWrite
        private const val output_connected_cap = AlsaPortCapabilities.Read or AlsaPortCapabilities.NoExport
        private const val input_connected_cap = AlsaPortCapabilities.Write or AlsaPortCapabilities.NoExport
        private const val virtual_output_receiver_connected_cap = AlsaPortCapabilities.Write or AlsaPortCapabilities.SubsWrite
        private const val virtual_input_sender_connected_cap = AlsaPortCapabilities.Read or AlsaPortCapabilities.SubsRead
    }

    override val name: String
        get() = "ALSA"
    override val canCreateVirtualPort: Boolean
        get() = true
    override val supportsUmpTransport: Boolean
        get() = true

    private fun enumerateMatchingPorts ( seq: AlsaSequencer, cap:  Int) : Iterable<AlsaPortInfo> {
        val clientInfo = AlsaClientInfo().apply { client = -1 }
        return sequence {
            while (seq.queryNextClient(clientInfo)) {
                val portInfo = AlsaPortInfo().apply {
                    client = clientInfo.client
                    port = -1
                }
                while (seq.queryNextPort(portInfo))
                    if ((portInfo.portType and midi_port_type) != 0 &&
                (portInfo.capabilities and cap) == cap)
                yield( portInfo.clone())
            }
        }.asIterable()
    }

    private fun enumerateAvailableInputPorts (): Iterable<AlsaPortInfo> {
        return enumerateMatchingPorts (system_watcher, input_requirements)
    }

    private fun enumerateAvailableOutputPorts () : Iterable<AlsaPortInfo> {
        return enumerateMatchingPorts (system_watcher, output_requirements)
    }

    // [input device port] --> [RETURNED PORT] --> app handles messages
    private fun createInputConnectedPort (seq: AlsaSequencer, midiProtocol: Int, group: Int, pinfo:  AlsaPortInfo, portName: String = "ktmidi ALSA input") : AlsaPortInfo {
        val isUmp = midiProtocol == MidiTransportProtocol.UMP
        val portCap = input_connected_cap or if (isUmp) AlsaPortCapabilities.UmpEndpoint else 0
        val portType = midi_port_type or if (isUmp) AlsaPortType.Ump else 0
        val portId = seq.createSimplePort (portName, portCap, portType)
        updateClientProtocol(seq, midiProtocol, group)
        val sub =  AlsaPortSubscription ()
        sub.destination.client = seq.currentClientId.toByte()
        sub.destination.port = portId.toByte()
        sub.sender.client = pinfo.client.toByte()
        sub.sender.port = pinfo.port.toByte()
        seq.subscribePort (sub)
        return seq.getPortInfo (sub.destination.port.toUnsigned())
    }

    // app generates messages --> [RETURNED PORT] --> [output device port]
    private fun createOutputConnectedPort ( seq: AlsaSequencer, midiProtocol: Int, group: Int, pinfo: AlsaPortInfo, portName: String = "ktmidi ALSA output") : AlsaPortInfo {
        val isUmp = midiProtocol == MidiTransportProtocol.UMP
        val portCap = output_connected_cap or if (isUmp) AlsaPortCapabilities.UmpEndpoint else 0
        val portType = midi_port_type or if (isUmp) AlsaPortType.Ump else 0
        val portId = seq.createSimplePort (portName, portCap, portType)
        updateClientProtocol(seq, midiProtocol, group)
        val sub = AlsaPortSubscription ()
        sub.sender.client = seq.currentClientId.toByte()
        sub.sender.port = portId.toByte()
        sub.destination.client = pinfo.client.toByte()
        sub.destination.port = pinfo.port.toByte()
        seq.subscribePort (sub)
        return seq.getPortInfo (sub.sender.port.toUnsigned())
    }

    override val inputs : Iterable<MidiPortDetails>
        get() = enumerateAvailableInputPorts ().map { p -> AlsaMidiPortDetails (p) }

    override val outputs : Iterable<MidiPortDetails>
        get() = enumerateAvailableOutputPorts ().map { p -> AlsaMidiPortDetails (p) }

    override suspend fun openInput (portId: String): MidiInput {
        val sourcePort = inputs.firstOrNull { p -> p.id == portId } as AlsaMidiPortDetails?
            ?: throw IllegalArgumentException ("Port '$portId' does not exist.")
        val seq = AlsaSequencer (AlsaIOType.Input, AlsaIOMode.NonBlocking)
        // FIXME: get UMP group?
        val appPort = createInputConnectedPort (seq, sourcePort.midiTransportProtocol, 0, sourcePort.portInfo)
        return AlsaMidiInput (seq, AlsaMidiPortDetails (appPort), sourcePort)
    }

    override suspend fun openOutput ( portId:String) : MidiOutput {
        val destPort = outputs.firstOrNull { p -> p.id == portId } as AlsaMidiPortDetails?
            ?: throw IllegalArgumentException ("Port '$portId' does not exist.")
        val seq = AlsaSequencer (AlsaIOType.Output, AlsaIOMode.None)
        // FIXME: get UMP group?
        val appPort = createOutputConnectedPort (seq, destPort.midiTransportProtocol, 0, destPort.portInfo)
        return AlsaMidiOutput (seq, AlsaMidiPortDetails (appPort), destPort)
    }

    private val seqIn1: AlsaSequencer by lazy { AlsaSequencer (AlsaIOType.Duplex, AlsaIOMode.NonBlocking) }
    private val seqIn2: AlsaSequencer by lazy { AlsaSequencer (AlsaIOType.Duplex, AlsaIOMode.NonBlocking) }

    override suspend fun createVirtualInputSender ( context: PortCreatorContext) : MidiOutput {
        val seq = if (context.midiProtocol == MidiTransportProtocol.UMP) seqIn2 else seqIn1

        val isUmp = context.midiProtocol == MidiTransportProtocol.UMP
        val portCap = virtual_input_sender_connected_cap or
                if (isUmp) AlsaPortCapabilities.UmpEndpoint else 0
        val portType = midi_port_type or if (isUmp) AlsaPortType.Ump else 0
        seq.setClientName (context.applicationName)
        updateClientProtocol(seq, context.midiProtocol, context.umpGroup)
        val portNumber = seq.createSimplePort(context.portName, portCap, portType)
        if (portNumber < 0)
            throw AlsaException(portNumber)
        val port = seq.getPortInfo(portNumber)
        if (context.midiProtocol == MidiTransportProtocol.UMP) {
            port.umpGroup = context.umpGroup
            seq.setPortInfo(portNumber, port)
        }
        val details =  AlsaMidiPortDetails (port)
        val send : (ByteArray, Int, Int, Long) -> Unit = { buffer, start, length, timestampInNanoSeconds ->
            if (timestampInNanoSeconds > 0)
                Thread.sleep(timestampInNanoSeconds / 1000000, (timestampInNanoSeconds % 1000000).toInt())
            seq.send(portNumber, buffer, start, length)
        }
        return AlsaVirtualMidiOutput (seq, details, { seq.deleteSimplePort(portNumber) }, send)
    }

    // should we have this as a property? It seems to cause internal crash.
    private val seqOut1: AlsaSequencer by lazy { AlsaSequencer (AlsaIOType.Duplex, AlsaIOMode.NonBlocking) }
    private val seqOut2: AlsaSequencer by lazy { AlsaSequencer (AlsaIOType.Duplex, AlsaIOMode.NonBlocking) }

    override suspend fun createVirtualOutputReceiver ( context:PortCreatorContext): MidiInput {
        //val seq = if (context.midiProtocol == MidiTransportProtocol.UMP) seqOut2 else seqOut1
        val seq = AlsaSequencer (AlsaIOType.Duplex, AlsaIOMode.NonBlocking)

        val portCap = virtual_output_receiver_connected_cap or
                if (context.midiProtocol == MidiTransportProtocol.UMP) AlsaPortCapabilities.UmpEndpoint else 0
        val portType = midi_port_type or if (context.midiProtocol == MidiTransportProtocol.UMP) AlsaPortType.Ump else 0
        seq.setClientName (context.applicationName)
        updateClientProtocol(seq, context.midiProtocol, context.umpGroup)
        val portNumber = seq.createSimplePort (context.portName, portCap, portType)
        if (portNumber < 0)
            throw AlsaException(portNumber)
        val port = seq.getPortInfo (portNumber)
        if (context.midiProtocol == MidiTransportProtocol.UMP) {
            port.umpGroup = context.umpGroup
            seq.setPortInfo(portNumber, port)
        }
        val details = AlsaMidiPortDetails (port)

        return AlsaVirtualMidiInput(seq, details) { seq.deleteSimplePort(port.port) }
    }

    private fun updateClientProtocol(seq: AlsaSequencer, midiProtocol: Int, group: Int) {
        if (midiProtocol == MidiTransportProtocol.UMP) {
            val client = seq.clientInfo
            client.midiVersion = midiProtocol
            client.isUmpGrouplessEnabled = true
            if (group > 0)
                client.setUmpGroupEnabled(group, true)
            seq.clientInfo = client // snd_seq_set_client_info()
        }
    }
}

class AlsaVirtualMidiOutput (
    private val seq: AlsaSequencer,
    private val portDetails: AlsaMidiPortDetails,
    private val onClose: ()->Unit,
    private val onSend: (ByteArray,Int,Int, Long) -> Unit) : MidiOutput {
    private var state = MidiPortConnectionState.OPEN

    override val details: MidiPortDetails
        get() = portDetails
    override val connectionState: MidiPortConnectionState
        get() = state

    override fun close() {
        onClose()
        seq.close()
        state = MidiPortConnectionState.CLOSED
    }

    override fun send (mevent: ByteArray, offset: Int, length: Int, timestampInNanoseconds: Long) =
        onSend(mevent, offset, length, timestampInNanoseconds)
}

class AlsaVirtualMidiInput (private val seq: AlsaSequencer, private val portDetails: AlsaMidiPortDetails, private val onClose: ()->Unit) : MidiInput {
    var messageReceived: OnMidiReceivedEventListener? = null
    var state = MidiPortConnectionState.OPEN
    val loop: AlsaSequencer.SequencerLoopContext

    override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
        this.messageReceived = listener
    }

    override val details: MidiPortDetails
        get() = portDetails
    override val connectionState: MidiPortConnectionState
        get() = state

    override fun close() {
        loop.stopListening()
        onClose()
        seq.close()
        state = MidiPortConnectionState.CLOSED
    }

    init {
        val buffer = ByteArray(0x200)
        val received : (ByteArray, Int, Int) -> Unit = { buf, start, len ->
            messageReceived?.onEventReceived (buf, start, len, 0)
        }
        loop = seq.startListening (portDetails.portInfo.port, buffer, onReceived = received, timeout = -1)
    }
}

class AlsaMidiPortDetails(private val port: AlsaPortInfo) : MidiPortDetails {

    internal val portInfo: AlsaPortInfo
        get() = port

    override val id: String
        get() = port.id

    override val manufacturer: String
        get() = port.manufacturer

    override val name: String
        get() = port.name

    override val version: String
        get() = port.version

    override val midiTransportProtocol: Int
        get() = if (port.portType and AlsaPortType.Ump != 0) MidiTransportProtocol.UMP else MidiTransportProtocol.MIDI1
}

class AlsaMidiInput(private val seq: AlsaSequencer, private val appPort: AlsaMidiPortDetails, private val sourcePort: AlsaMidiPortDetails) : MidiInput {
    val loop: AlsaSequencer.SequencerLoopContext

    override val details: MidiPortDetails
        get() = sourcePort

    override var connectionState: MidiPortConnectionState = MidiPortConnectionState.OPEN

    private var messageReceived: OnMidiReceivedEventListener? = null

    override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) {
        this.messageReceived = listener
    }

    override fun close () {
        loop.stopListening()
        // unsubscribe the app port from the MIDI input, and then delete the port.
        val q = AlsaSubscriptionQuery().apply {
            type = AlsaSubscriptionQueryType.Write
            index = 0
        }
        q.address.client = appPort.portInfo.client.toByte()
        q.address.port = appPort.portInfo.port.toByte()
        if (seq.queryPortSubscribers (q))
            seq.disconnectDestination (appPort.portInfo.port, q.address.client.toUnsigned(), q.address.port.toUnsigned())
        seq.deleteSimplePort (appPort.portInfo.port)
    }

    init {
        val buffer = ByteArray(0x200)
        val received : (ByteArray, Int, Int) -> Unit = { buf, start, len ->
            messageReceived?.onEventReceived (buf, start, len, 0)
        }
        loop = seq.startListening (appPort.portInfo.port, buffer, onReceived = received, timeout = -1)
    }
}

class AlsaMidiOutput(private val seq: AlsaSequencer, private val appPort: AlsaMidiPortDetails, private val targetPort: AlsaMidiPortDetails) : MidiOutput {

    override val details: MidiPortDetails
        get() = targetPort

    override var connectionState: MidiPortConnectionState = MidiPortConnectionState.OPEN

    override fun close() {
        // unsubscribe the app port from the MIDI output, and then delete the port.
        val q = AlsaSubscriptionQuery().apply {
            type = AlsaSubscriptionQueryType.Read
            index = 0
        }
        q.address.client = appPort.portInfo.client.toByte()
        q.address.port = appPort.portInfo.port.toByte()
        if (seq.queryPortSubscribers(q))
            seq.disconnectDestination(appPort.portInfo.port, q.address.client.toUnsigned(), q.address.port.toUnsigned())
        seq.deleteSimplePort(appPort.portInfo.port)
    }

    override fun send(mevent: ByteArray, offset: Int, length: Int, timestamp: Long) {
        seq.send(appPort.portInfo.port, mevent, offset, length)
    }
}