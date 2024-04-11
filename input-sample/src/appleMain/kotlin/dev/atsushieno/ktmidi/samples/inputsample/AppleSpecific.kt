package dev.atsushieno.ktmidi.samples.inputsample

import dev.atsushieno.ktmidi.MidiAccess
import dev.atsushieno.ktmidi.MidiTransportProtocol
import dev.atsushieno.ktmidi.TraditionalCoreMidiAccess
import dev.atsushieno.ktmidi.UmpCoreMidiAccess
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFRunLoopGetMain
import platform.CoreFoundation.CFRunLoopRef
import platform.CoreFoundation.CFRunLoopRun
import platform.CoreFoundation.CFRunLoopStop

actual fun getNativeMidiAccessApi(midiTransportProtocol: Int): MidiAccess =
    if (midiTransportProtocol == MidiTransportProtocol.UMP)
        UmpCoreMidiAccess()
    else
        TraditionalCoreMidiAccess()

@OptIn(ExperimentalForeignApi::class)
actual fun runLoop(body: ()->Unit) {
    CFRunLoopRun()
    body()
    CFRunLoopStop(CFRunLoopGetMain())
}
