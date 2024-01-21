package dev.atsushieno.ktmidi.citool

import android.media.midi.MidiDeviceService
import android.media.midi.MidiReceiver
import android.media.midi.MidiSender

class KtMidiToolDeviceService : MidiDeviceService() {
    private lateinit var receiver: KtMidiToolReceiver
    private lateinit var sender: KtMidiToolSender

    override fun onCreate() {
        super.onCreate()
        initializeAppModel(applicationContext)
        receiver = KtMidiToolReceiver()
        sender = KtMidiToolSender()
    }
    override fun onGetInputPortReceivers(): Array<MidiReceiver> {
        return arrayOf(receiver)
    }
}

class KtMidiToolReceiver : MidiReceiver() {
    override fun onSend(msg: ByteArray?, offset: Int, count: Int, timestamp: Long) {
        if (msg != null)
            AppModel.ciDeviceManager.processMidiInput(msg, offset, count)
    }
}

class KtMidiToolSender : MidiSender() {
    private val receivers = mutableListOf<MidiReceiver>()
    override fun onConnect(receiver: MidiReceiver?) {
        if (receiver != null)
            receivers.add(receiver)
    }

    override fun onDisconnect(receiver: MidiReceiver?) {
        receivers.remove(receiver)
    }

    init {
        AppModel.midiDeviceManager.midiOutputSent.add { bytes, timestamp ->
            receivers.forEach { it.send(bytes, 0, bytes.size, timestamp) }
        }
    }
}