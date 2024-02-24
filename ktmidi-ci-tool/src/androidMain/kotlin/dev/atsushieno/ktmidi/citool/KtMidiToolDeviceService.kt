package dev.atsushieno.ktmidi.citool

import AndroidPlatform
import android.media.midi.MidiDeviceService
import android.media.midi.MidiReceiver
import android.media.midi.MidiUmpDeviceService
import androidPlatform
import androidx.annotation.RequiresApi
import dev.atsushieno.ktmidi.MidiTransportProtocol

class KtMidiToolDeviceService : MidiDeviceService() {
    private lateinit var repository: CIToolRepository
    private lateinit var receiver: KtMidiToolReceiver

    override fun onCreate() {
        androidPlatform = androidPlatform ?: AndroidPlatform(applicationContext)
        repository = CIToolRepository()
        receiver = KtMidiToolReceiver(repository, MidiTransportProtocol.MIDI1)

        repository.midiDeviceManager.midiOutputSent.add { bytes, timestamp ->
            outputPortReceivers.forEach { it.send(bytes, 0, bytes.size, timestamp) }
        }

        // It is usually in the wrong order, but MidiDeviceService invokes onGetInputPortReceivers(),
        // which returns the lateinit var that is being initialized *in this method*.
        // So, it must be invoked later than initialization.
        super.onCreate()
    }
    override fun onGetInputPortReceivers(): Array<MidiReceiver> {
        return arrayOf(receiver)
    }
}

class KtMidiToolReceiver(private val repository: CIToolRepository, private val protocol: Int) : MidiReceiver() {
    override fun onSend(msg: ByteArray?, offset: Int, count: Int, timestamp: Long) {
        if (msg != null)
            if (protocol == MidiTransportProtocol.UMP)
                repository.ciDeviceManager.processUmpInput(msg, offset, count)
            else
                repository.ciDeviceManager.processMidi1Input(msg, offset, count)
    }
}

@RequiresApi(35)
class KtMidiToolUmpDeviceService : MidiUmpDeviceService() {
    private lateinit var repository: CIToolRepository
    private lateinit var receiver: KtMidiToolReceiver

    override fun onCreate() {
        androidPlatform = androidPlatform ?: AndroidPlatform(applicationContext)
        repository = CIToolRepository()
        receiver = KtMidiToolReceiver(repository, MidiTransportProtocol.UMP)

        repository.midiDeviceManager.midiOutputSent.add { bytes, timestamp ->
            outputPortReceivers.forEach { it.send(bytes, 0, bytes.size, timestamp) }
        }

        // It is usually in the wrong order, but MidiDeviceService invokes onGetInputPortReceivers(),
        // which returns the lateinit var that is being initialized *in this method*.
        // So, it must be invoked later than initialization.
        super.onCreate()
    }
    override fun onGetInputPortReceivers(): List<MidiReceiver> {
        return listOf(receiver)
    }
}
