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
- `JvmMidiAccess`: based on `javax.sound.midi` API, on Kotlin/JVM. The feature is quite limited because of Java's poor support and classic design to make everything in G.C.M. It is not recommended to use it; use `RtMidiAccess` or `LibreMidiAccess` for better feature sets.
- `AlsaMidiAccess`: based on [atsushieno/alsakt](https://github.com/atsushieno/alsakt), on Kotlin/JVM. Supports virtual MIDI ports, and partial device detection. More importantly, it is based on ALSA sequencer API which covers much more than javax.sound.midi which is based on ALSA rawmidi (which lacks support for virtual instruments etc.).
- `RtMidiAccess`: based on [thestk/rtmidi](https://github.com/thestk/rtmidi), on Kotlin/JVM. Supports virtual MIDI ports (wherever supported), no device detection.
- `RtMidiNativeAccess`: same as `RtMidiAccess`, but for Kotlin/Native.
- `LibreMidiAccess`: based on [celtera/libremidi](https://github.com/celtera/libremidi), on Kotlin/JVM. Supports virtual MIDI ports (wherever supported), supports MIDI 2.0 UMP ports, no device detection (due to [binder limitation](https://github.com/atsushieno/libremidi-javacpp/issues/2)). It should be used the default cross-platform desktop implementation on Linux, Mac and Windows ([TODO](https://github.com/atsushieno/libremidi-javacpp/issues/4)).
- `JzzMidiAccess` : based on [Jazz-Soft/JZZ](https://jazz-soft.net/doc/JZZ/), on Kotlin/JS. Not really tested yet.
- `WebMidiAccess` : direct Web MIDI API implementation for Kotlin/Wasm.

It is also possible to implement this API for custom MIDI devices and/or
ports. This design is inherited from managed-midi API. Therefore it is possible to build something like `FluidsynthMidiAccess` in [nfluidsynth](https://github.com/atsushieno/nfluidsynth) which was for managed-midi.

Lastly, but as often the most useful implementation, there is `EmptyMidiAccess` implementation that basically does NO-OP.

## Feature set

There are various options on how a cross-platform MIDI access API can be designed.
For now, `MidiAccess` class basically starts with what Web MIDI API provides.

Listing available ports may be unreliable depending on the platforms. WinMM does not provide such functionality, so it needs to repeatedlly check available ports to detect any diffs from the previously checked list.
If you really need it, you can observe Inputs and Outputs of `MidiAccess` using simple timer loop.

It also provides functionality to create arbitrary virtual input and output ports. It is doable only with Linux (ALSA / RtMidi / LibreMidi), Mac JVM (RtMidi / LibreMidi), Mac Native (RtMidiNative), and probably iOS (CoreMidi, untested). Windows does not support virtual ports and therefore we do not support them either.


## asynchronous API

It is argurable that the API should be asynchronous or not. At this state, these operations are exposed as `suspend fun` on the common interface:

- `MidiAccess.openInput()`
- `MidiAccess.openOutput()`

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

## dealing with UMP endpoints

Since `MidiAccess` API is generally based on what Web MIDI API provides, and Web MIDI API does not have any distinct concept between a "device" and a "port". But now we have many enhancements over it e.g. we can handle UMP ports, and we can create virtual ports.

Combining those concepts brought in an interesting problem: what if we need a bidirectional UMP ports? Currently we only have `createVirtualInputSender()` and `createVirtualOutputReceiver()` and it is impossible to have them as bidirection. Should there be a unified `MidiInput` and `MidiOutput` interface like `MidiDuplex` and `createVirtualDuplexHandler()` ? We don't have any design decision yet.

Currently LibreMidi and CoreMIDI have support for virtual UMP ports (ALSA not yet working), and currently they have no problem as long as what ktmidi provides. But those input and output ports are enumerated as different devices underneath (not sure about CoreMIDI, but ALSA `aconnect -l` tells us how those ALSA clients and ports are structured).
