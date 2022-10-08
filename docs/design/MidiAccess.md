# MidiAccess API design notes

`MidiAccess` is the platform-agnostic interface that provides access to platform-specific native MIDI access API. Although, it is important to note that that we do provide everything.

## Kotlin Multiplatform

ktmidi is built on Kotlin multiplatform paradigm, and it aims to become runnable on any of those (JVM/JS/Native/wasm in 1.5+). However the scope of this ubiquity is so far limited to common MIDI data structures and MIDI access abstraction layer.

Unlike [managed-midi](https://github.com/atsushieno/managed-midi) which was the origin of this project, Kotlin library outcomes are in different formats across platforms, in terms of library binary:

- Kotlin/JVM: Java bytecode in a JAR (and if applicable it should become an AAR on Android)
- Kotlin/Native: "klib" if not compiled down to native shared libraries or apps. (I have little experience with this)
- Kotlin/JS: Javascript (I have no experience with this)
- Kotlin/Wasm (if you use 1.5+): WebAssembly, then it should be usable either with JS or native runtimes. (I have no experience with this)

We have the following platform-specific implementations:

- `AndroidMidiAccess`: based on `android.media.midi` API, on Kotlin/JVM. The feature is limited due to Android platform limitation.
- `JvmMidiAccess`: based on `javax.sound.midi` API, on Kotlin/JVM. The feature is quite limited because of Java's poor support and classic design to make everything in G.C.M. It is not recommended to use it; use RtMidiAccess for better feature sets.
- `AlsaMidiAccess`: based on [atsushieno/alsakt](https://github.com/atsushieno/alsakt), on Kotlin/JVM. Supports virtual MIDI ports, and partial device detection. More importantly, it is based on ALSA sequencer API which covers much more than javax.sound.midi which is based on ALSA rawmidi (which lacks support for virtual instruments etc.).
- `RtMidiAccess`: based on [thestk/rtmidi](https://github.com/thestk/rtmidi), on Kotlin/JVM. Supports virtual MIDI ports (wherever supported), no device detection. It should be used the default cross-platform desktop implementation on Mac and Windows.
- `RtMidiNativeAccess`: same as `RtMidiAccess`, but for Kotlin/Native.
- `JzzMidiAccess` : based on [Jazz-Soft/JZZ](https://jazz-soft.net/doc/JZZ/), on Kotlin/JS. Not really tested yet.

It is also possible to implement this API for custom MIDI devices and/or
ports. This design is inherited from managed-midi API. Therefore it is possible to build something like `FluidsynthMidiAccess` in [nfluidsynth](https://github.com/atsushieno/nfluidsynth) which was for managed-midi.

Lastly, but as often the most useful implementation, there is `EmptyMidiAccess` implementation that basically does NO-OP.

## Feature set

There are various options on how a cross-platform MIDI access API can be designed.
For now, `MidiAccess` class basically starts with what Web MIDI API provides, except that it does not currently track device connection state changes.
If you really need it, you can observe Inputs and Outputs of `MidiAccess` using simple timer loop.

It also provides functionality to create arbitrary virtual input and output ports. It is doable only with Linux (ALSA / RtMidi) and Mac/iOS probably (RtMidi, untested). Windows does not support virtual ports and therefore we do not support them either.


## asynchronous API

It is argurable that the API should be asynchronous or not. At this state, these operations are exposed as asynchronous on the common interface:

- `MidiAccess.openInputAsync()`
- `MidiAccess.openOutputAsync()`
- `MidiInput.closeAsync()`
- `MidiOutput.closeAsync()`

Other operations, such as `MidiOutput.send()` and `MidiInput.messageReceived` are implemented as synchronous.
While they are designed to be synchronous, users shouldn't expect them to "block" until the actual MIDI messaging is done.
They are "fire and forget" style, like UDP messages.

Implementation of these interfaces in ktmidi can be "anything". It is possible, especially when network transport is involved like RTP MIDI, that even those send/receive operations can take a while to complete.
If these methods are designed to be blocked, then applications likely get messed.

On the other hand, if it were designed as Task-based asynchronous API, then users (app developers) would have to deal with async/await context.
In real-time ready MIDI access APIs in C/C++ world, it is definitely no-go to introduce synchronization mechanisms here.
For ordinal MIDI access like WinMM or CoreMIDI, they shouldn't take too much time to process as those MIDI messages can be sent in very short time, whereas (on the other hand) in such case they only result in tons of awaits and cause extraneous burden on users apps.

For MIDI access implementations like RTP support, their send/receive operations should be still implemented to return immediately, while queuing messages on some other messaging.

For open and close operations, there wouldn't be too many calls and there is no performance concern.
