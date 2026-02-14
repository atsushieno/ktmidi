package dev.atsushieno.alsakt
import AlsaClientInfo
import dev.atsushieno.ktmidi.MidiTransportProtocol
import dev.atsushieno.ktmidi.Ump
import dev.atsushieno.ktmidi.sizeInInts
import dev.atsushieno.ktmidi.umpSizeInInts
import dev.atsushieno.panama.alsa.alsa_seq_h
import dev.atsushieno.panama.alsa.alsa_seq_midi_event_h
import dev.atsushieno.panama.alsa.poll_h
import dev.atsushieno.panama.alsa.snd_midi_event_t
import dev.atsushieno.panama.alsa.snd_seq_addr_t
import dev.atsushieno.panama.alsa.snd_seq_event_t
import dev.atsushieno.panama.alsa.snd_seq_t
import dev.atsushieno.panama.alsa.snd_seq_ump_event_payload_t
import dev.atsushieno.panama.alsa.snd_seq_ump_event_t
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.nio.ByteBuffer

@Suppress("unused")
class AlsaSequencer(
    val ioType: Int, ioMode: Int,
    val driverName: String = "default"
) : AutoCloseable {

    private val seq: MemorySegment

    internal val sequencerHandle : MemorySegment
        get() = seq

    override fun close() {
        if (midiEventParserOutput != null) {
            alsa_seq_midi_event_h.snd_midi_event_free(midiEventParserOutput)
            midiEventParserOutput = null
        }
        if (seq != null)
            alsa_seq_h.snd_seq_close(seq)
    }

    val name :String
        get() = alsa_seq_h.snd_seq_name(seq).getString(0)

    val sequencerType: Int
        get() = alsa_seq_h.snd_seq_type(seq)

    fun setNonBlockingMode(toNonBlockingMode: Boolean) {
        alsa_seq_h.snd_seq_nonblock(seq, if (toNonBlockingMode) 1 else 0)
    }

    val currentClientId: Int
        get() = alsa_seq_h.snd_seq_client_id(seq)

    var inputBufferSize: Long
        get() = alsa_seq_h.snd_seq_get_input_buffer_size(seq)
        set(value) { alsa_seq_h.snd_seq_set_input_buffer_size(seq, value) }

    var outputBufferSize: Long
        get() = alsa_seq_h.snd_seq_get_output_buffer_size(seq)
        set(value) { alsa_seq_h.snd_seq_set_output_buffer_size(seq, value) }

    val targetPortType: Int
        get() = AlsaPortType.MidiGeneric or AlsaPortType.Synth or AlsaPortType.Application

    fun queryNextClient(client: AlsaClientInfo): Boolean {
        val ret = alsa_seq_h.snd_seq_query_next_client(seq, client.handle)
        return ret >= 0
    }

    fun queryNextPort(port: AlsaPortInfo): Boolean {
        val ret = alsa_seq_h.snd_seq_query_next_port(seq, port.handle)
        return ret >= 0
    }

    var clientInfo: AlsaClientInfo
        get() {
            val info = AlsaClientInfo()
            val ret = alsa_seq_h.snd_seq_get_client_info(seq, info.handle)
            if (ret != 0)
                throw AlsaException(ret)
            return info
        }
        set(value) {
            val ret = alsa_seq_h.snd_seq_set_client_info(seq, value.handle)
            if (ret != 0)
                throw AlsaException(ret)
        }

    @Deprecated("Use getAnyClient()", ReplaceWith("getAnyClient(client)"))
    fun getClient(client: Int): AlsaClientInfo = getAnyClient(client)

    fun getAnyClient(client: Int): AlsaClientInfo {
        val info = AlsaClientInfo()
        val ret = alsa_seq_h.snd_seq_get_any_client_info(seq, client, info.handle)
        if (ret != 0)
            throw AlsaException(ret)
        return info
    }

    fun getPortInfo(port: Int): AlsaPortInfo {
        val info = AlsaPortInfo()
        val err = alsa_seq_h.snd_seq_get_port_info(seq, port, info.handle)
        if (err != 0)
            throw AlsaException(err)
        return info
    }

    @Deprecated("Use getAnyPortInfo() instead", ReplaceWith("getAnyPortInfo(client, port)"))
    fun getPort(client: Int, port: Int): AlsaPortInfo = getAnyPortInfo(client, port)

    fun getAnyPortInfo(client: Int, port: Int): AlsaPortInfo {
        val info = AlsaPortInfo()
        val err = alsa_seq_h.snd_seq_get_any_port_info(seq, client, port, info.handle)
        if (err != 0)
            throw AlsaException(err)
        return info
    }

    fun setPortInfo(port: Int, info: AlsaPortInfo) {
        val err = alsa_seq_h.snd_seq_set_port_info(seq, port, info.handle)
        if (err != 0)
            throw AlsaException(err)
    }

    private var portNameHandle = Arena.ofShared().allocate(256)

    fun createSimplePort(name: String?, caps: Int, type: Int): Int {
        if (name != null)
            portNameHandle.setString(0, name)
        return alsa_seq_h.snd_seq_create_simple_port(seq, portNameHandle, caps, type)
    }

    fun deleteSimplePort(port: Int) {
        val ret = alsa_seq_h.snd_seq_delete_simple_port(seq, port)
        if (ret != 0)
            throw AlsaException(ret)
    }

    private var nameHandle = Arena.ofShared().allocate(256)

    fun setClientName(name: String) {
        if (name == null)
            throw IllegalArgumentException("name is null")

        nameHandle.setString(0, name)
        alsa_seq_h.snd_seq_set_client_name(seq, nameHandle)
    }

    //#region Subscription

    fun subscribePort(subs: AlsaPortSubscription) {
        val err = alsa_seq_h.snd_seq_subscribe_port(seq, subs.handle)
        if (err != 0)
            throw AlsaException(err)
    }

    fun unsubscribePort(sub: AlsaPortSubscription) {
        alsa_seq_h.snd_seq_unsubscribe_port(seq, sub.handle)
    }

    fun queryPortSubscribers(query: AlsaSubscriptionQuery): Boolean {
        val ret = alsa_seq_h.snd_seq_query_port_subscribers(seq, query.handle)
        return ret == 0
    }

    // simplified SubscribePort()
    // formerly connectFrom()
    fun connectSource(portToReceive: Int, sourceClient: Int, sourcePort: Int) {
        val err = alsa_seq_h.snd_seq_connect_from(seq, portToReceive, sourceClient, sourcePort)
        if (err != 0)
            throw  AlsaException(err)
    }

    // simplified SubscribePort()
    // formerly connectTo()
    fun connectDestination(portToSendFrom: Int, destinationClient: Int, destinationPort: Int) {
        val err = alsa_seq_h.snd_seq_connect_to(seq, portToSendFrom, destinationClient, destinationPort)
        if (err != 0)
            throw AlsaException (err)
    }

    // simplified UnsubscribePort()
    // formerly disconnectFrom()
    fun disconnectSource(portToReceive: Int, sourceClient: Int, sourcePort: Int) {
        val err = alsa_seq_h.snd_seq_disconnect_from(seq, portToReceive, sourceClient, sourcePort)
        if (err != 0)
            throw  AlsaException(err)
    }

    // simplified UnsubscribePort()
    // formerly disconnectTo
    fun disconnectDestination(portToSendFrom: Int, destinationClient: Int, destinationPort: Int) {
        val err = alsa_seq_h.snd_seq_disconnect_to(seq, portToSendFrom, destinationClient, destinationPort)
        if (err != 0)
            throw  AlsaException(err)
    }

    //#endregion // Subscription

    fun resetPoolInput() {
        alsa_seq_h.snd_seq_reset_pool_input(seq)
    }

    fun resetPoolOutput() {
        alsa_seq_h.snd_seq_reset_pool_output(seq)
    }

    //#region Events

    private val midiEventBufferSize : Long = 256
    private var eventBufferOutput = Arena.ofShared().allocate(midiEventBufferSize)
    private var midiEventParserOutput: MemorySegment? = null

    // FIXME: should this be moved to AlsaMidiApi? It's a bit too high level.
    fun send(port: Int, data: ByteArray, index: Int, count: Int) {

        val midiProtocol = clientInfo.midiVersion
        if (midiProtocol == MidiTransportProtocol.UMP)
            sendUmp(port, data, index, count)
        else
            sendMidi1(port, data, index, count)
    }

    private fun sendMidi1(port: Int, data: ByteArray, index: Int, count: Int) {
        if (midiEventParserOutput == null) {
            val ptr = MemorySegment.NULL
            val ret = alsa_seq_midi_event_h.snd_midi_event_new(midiEventBufferSize, ptr)
            if (ret < 0)
                throw AlsaException(ret.toInt())
            midiEventParserOutput = ptr
        }

        val buffer = ByteBuffer.wrap(data)
        val pointer = MemorySegment.ofBuffer(buffer)
        val ev = eventBufferOutput
        var i = index
        while (i < index + count) {
            val ret = alsa_seq_midi_event_h.snd_midi_event_encode(
                midiEventParserOutput,  pointer.asSlice(i.toLong()), index + count - i.toLong(), ev)
            if (ret < 0)
                throw AlsaException(ret.toInt())
            if (ret > 0) {
                val b = eventBufferOutput.asByteBuffer()
                b.alignedSlice(seq_evt_off_source_port).put(port.toByte())
                b.alignedSlice(seq_evt_off_dest_client).put(AddressSubscribers.toByte())
                b.alignedSlice(seq_evt_off_dest_port).put(AddressUnknown.toByte())
                b.alignedSlice(seq_evt_off_queue).put(QueueDirect.toByte())
                // FIXME: should we provide error handler and catch it?
                val ret = alsa_seq_h.snd_seq_event_output_direct(seq, ev)
                if (ret < 0)
                    throw AlsaException(ret)
            }
            i += ret.toInt()
        }
    }

    private fun sendUmp(port: Int, data: ByteArray, index: Int, count: Int) {
        if (midiEventParserOutput == null) {
            val ptr = snd_midi_event_t.allocate(Arena.ofShared())
            alsa_seq_midi_event_h.snd_midi_event_new(midiEventBufferSize, ptr)
            midiEventParserOutput = ptr
        }

        val ev = snd_seq_ump_event_t.allocate(Arena.ofShared())
        val source = snd_seq_addr_t.allocate(Arena.ofShared())
        snd_seq_addr_t.client(source, clientInfo.client.toByte())
        snd_seq_addr_t.port(source, port.toByte())
        snd_seq_ump_event_t.source(ev, source)
        val dest = snd_seq_addr_t.allocate(Arena.ofShared())
        snd_seq_addr_t.client(dest, AddressSubscribers.toByte())
        snd_seq_addr_t.port(dest, AddressUnknown.toByte())
        snd_seq_ump_event_t.queue(ev, QueueDirect.toByte())
        snd_seq_ump_event_t.type(ev, alsa_seq_h.SND_SEQ_EVENT_NONE.toByte())

        Ump.fromBytes(data, index, count).forEach { ump ->
            val size = ump.sizeInInts
            snd_seq_ump_event_payload_t.ump(ev, 0, ump.int1)
            if (size > 1)
                snd_seq_ump_event_payload_t.ump(ev, 1, ump.int2)
            if (size > 2)
                snd_seq_ump_event_payload_t.ump(ev, 2, ump.int3)
            if (size > 3)
                snd_seq_ump_event_payload_t.ump(ev, 3, ump.int4)
            // FIXME: should we provide error handler and catch it?
            alsa_seq_h.snd_seq_ump_event_output_direct(seq, ev)
        }
    }

    private val defaultInputTimeout = -1
    fun startListening(
        applicationPort: Int,
        buffer: ByteArray,
        onReceived: (ByteArray, Int, Int) -> Unit,
        timeout: Int = defaultInputTimeout
    ) = SequencerLoopContext(this, midiEventBufferSize).apply { startListening(applicationPort, buffer, onReceived, timeout) }

    fun stopListening(loop: SequencerLoopContext) { loop.stopListening() }

    class SequencerLoopContext(private val sequencer: AlsaSequencer, private val midiEventBufferSize: Long) {
        private val seq: MemorySegment = sequencer.sequencerHandle!!
        private var eventLoopStopped = false
        private lateinit var eventLoopBuffer: ByteArray
        private var inputTimeout: Int = 0
        private var eventLoopTask: Job? = null
        private lateinit var onReceived: (ByteArray, Int, Int) -> Unit

        fun startListening(
            applicationPort: Int,
            buffer: ByteArray,
            onReceived: (ByteArray, Int, Int) -> Unit,
            timeout: Int
        ) {
            eventLoopBuffer = buffer
            inputTimeout = timeout
            this.onReceived = onReceived
            eventLoopTask = GlobalScope.launch { eventLoop(applicationPort) }
        }

        fun stopListening() {
            eventLoopStopped = true
        }

        private fun eventLoop(port: Int) {

            val pollfdSizeDummy = 8
            val count = alsa_seq_h.snd_seq_poll_descriptors_count(seq, POLLIN.toShort())
            //val pollfdArrayRef = BytePointer((count * pollfdSizeDummy).toLong())
            //val fd = MemorySegment.ofAddress(pollfdArrayRef.address())
            val fd = Arena.ofShared().allocate(count * pollfdSizeDummy.toLong())
            val ret = alsa_seq_h.snd_seq_poll_descriptors(seq, fd, count, POLLIN.toShort())
            if (ret < 0)
                throw AlsaException(ret)

            val midiProtocol = sequencer.clientInfo.midiVersion

            while (!eventLoopStopped) {
                val rt = poll_h.poll(fd, count.toLong(), inputTimeout)
                if (rt > 0) {
                    if (midiProtocol == 2) {
                        val len = receiveUmp(port, eventLoopBuffer, 0, eventLoopBuffer.size)
                        onReceived(eventLoopBuffer, 0, len)
                    } else {
                        val len = receiveMidi1(port, eventLoopBuffer, 0, eventLoopBuffer.size)
                        onReceived(eventLoopBuffer, 0, len)
                    }
                }
            }
        }

        private var midiEventParserInput: MemorySegment? = null

        private fun receiveMidi1(port: Int, data: ByteArray, index: Int, count: Int): Int {
            var received = 0

            if (midiEventParserInput == null) {
                val ptr = snd_midi_event_t.allocate(Arena.ofShared())
                alsa_seq_midi_event_h.snd_midi_event_new(midiEventBufferSize, ptr)
                midiEventParserInput = ptr
            }

            var remaining = true
            while (remaining && index + received < count) {
                val sevt = snd_seq_event_t.allocate(Arena.ofShared())
                val ret = alsa_seq_h.snd_seq_event_input(seq, sevt)
                remaining = alsa_seq_h.snd_seq_event_input_pending(seq, 0) > 0
                if (ret < 0)
                    throw AlsaException(ret)
                val converted = alsa_seq_midi_event_h.snd_midi_event_decode(
                    midiEventParserInput,
                    MemorySegment.ofBuffer(ByteBuffer.wrap(data, index + received, data.size - index - received)),
                    (count - received).toLong(),
                    sevt
                )
                if (converted > 0)
                    received += converted.toInt()
            }
            return received
        }

        private fun receiveUmp(port: Int, data: ByteArray, index: Int, count: Int): Int {
            var received = 0

            val numEvents = alsa_seq_h.snd_seq_event_input_pending(seq, 1)
            var eventsProcessed = 0

            while (index + received < count) {
                val sevt = snd_seq_ump_event_t.allocate(Arena.ofShared())
                val ret = alsa_seq_h.snd_seq_ump_event_input(seq, sevt)
                if (ret < 0)
                    throw AlsaException(ret)
                val size = umpSizeInInts(snd_seq_ump_event_payload_t.ump(sevt, 0))
                val bytes = snd_seq_ump_event_payload_t.ump(sevt)
                bytes.asByteBuffer().get(data, index + received, size * 4)
                received += size * 4
                if (++eventsProcessed == numEvents)
                    break
            }
            return received
        }
    }

    //#endregion

    companion object {
        const val ClientSystem = 0
        const val POLLIN = 1

        private val seq_evt_size: Int
        private val seq_evt_off_source_port: Int
        private val seq_evt_off_dest_client: Int
        private val seq_evt_off_dest_port: Int
        private val seq_evt_off_queue: Int

        const val AddressUnknown = 253
        const val AddressSubscribers = 254
        const val AddressBroadcast = 255

        const val QueueDirect = 253

        init {
            val evtLayout = snd_seq_event_t.layout()
            val addrLayout = snd_seq_addr_t.layout()
            seq_evt_size = snd_seq_event_t.sizeof().toInt()
            val getEvtOffset = { name: String -> evtLayout.memberLayouts().first { it.name()?.get() == name }.byteOffset().toInt() }
            val getAddrOffset = { name: String -> addrLayout.memberLayouts().first { it.name()?.get() == name }.byteOffset().toInt() }
            seq_evt_off_source_port = getEvtOffset("source") + getAddrOffset("port")
            seq_evt_off_dest_client = getEvtOffset("dest") + getAddrOffset("client")
            seq_evt_off_dest_port = getEvtOffset("dest") + getAddrOffset("port")
            seq_evt_off_queue = getEvtOffset("queue")
        }
    }

    init {
        val nameBytes = driverName.toByteArray(Charsets.UTF_8)
        val driverNameMS = MemorySegment.ofBuffer(ByteBuffer.wrap(nameBytes))
        val ptr = snd_seq_t.allocate(Arena.ofShared())
        val err = alsa_seq_h.snd_seq_open(ptr, driverNameMS, ioType, ioMode)
        if (err != 0)
            throw AlsaException(err)
        seq = ptr
    }
}
