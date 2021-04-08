# MidiAccess API design notes

`MidiAccess` is the platform-agnostic interface that provides access to platform-specific native MIDI access API. Although, it is important to note that that we do provide everything.

## Kotlin Multiplatform

ktmidi is built on Kotlin multiplatform paradigm, and it aims to become runnable on any of those (JVM/JS/Native/wasm in 1.5+). However the scope of this ubiquity is so far limited to common MIDI data structures and MIDI access abstraction layer.

Unlike [managed-midi](https://github.com/atsushieno/managed-midi) which was the origin of this project, Kotlin library outcomes are in different formats across platforms, in terms of library binary:

- Kotlin/JVM: Java bytecode in a JAR (and if applicable it should become an AAR on Android)
- Kotlin/Native: "klib" if not compiled down to native shared libraries or apps. (I have little experience with this)
- Kotlin/JS: Javascript (I have no experience with this)
- Kotlin/Wasm (if you use 1.5+): WebAssembly, then it should be usable either with JS or native runtimes. (I have no experience with this)

We have the following platform-specific implementations, only for Kotlin/JVM:

- AndroidMidiAccess: based on `android.media.midi` API. The feature is limited due to Android platform limitation.
- JvmMidiAccess: based on `javax.sound.midi` API. The feature is quite limited because of Java's poor support and classic design to make everything in G.C.M.
- AlsaMidiAccess: based on [atsushieno/alsakt](https://github.com/atsushieno/alsakt). Supports virtual MIDI ports, and partial device detection. More importantly, it is based on ALSA sequencer API which covers much more than javax.sound.midi which is based on ALSA rawmidi (which lacks support for virtual instruments etc.).

While we support Android, ALSA, there is no support for them via Kotlin/Native (nor anything else). There are chances for Web MIDI API support in Kotlin/JS and Kotlin/Wasm, or cinterop with platform API (WinMM/CoreMIDI/ALSA) or cross-platform API (like rtmidi, portmidi or libremidi).

It is also possible to implement this API for custom MIDI devices and/or
ports. This design is inherited from managed-midi API.
Therefore it is possible to build something like `FluidsynthMidiAccess` in [nfluidsynth](https://github.com/atsushieno/nfluidsynth).

Lastly, but as often the most useful implementation, there is `EmptyMidiAccess` implementation that basically does NO-OP.

## Feature set

There are various options on how a cross-platform MIDI access API can be designed.
For now, `MidiAccess` class basically starts with what Web MIDI API provides, except that it does not currently track device connection state changes.
If you really need it, you can observe Inputs and Outputs of `MidiAccess` using simple timer loop.

It also provides functionality to create arbitrary virtual input and output ports. It is doable only with Linux (ALSA), and throws error everywhere else. It will be possible with CoreMIDI (Mac/iOS) later on.


## asynchronous API

**FIXME**: open/close functions are not asynchronous (yet - still not sure if we will make them `suspend` or not).

It is argurable that the API should be asynchronous or not. At this state, these operations are exposed as asynchronous on the common interface:

- `MidiAccess.openInputAsync()`
- `MidiAccess.openOutputAsync()`
- `MidiInput.closeAsync()`
- `MidiOutput.closeAsync()`

Other operations, such as `MidiOutput.send()` and `MidiInput.messageReceived` are implemented as synchronous.
While they are designed to be synchronous, users shouldn't expect them to "block" until the actual MIDI messaging is done.
They are "fire and forget" style, like UDP messages.

Implementation of these interfaces can be "anything". It is possible, especially when network transport is involved like RTP MIDI, that even those send/receive operations can take a while to complete.
If these methods are designed to be blocked, then applications likely get messed.

On the other hand, if it is designed as Task-based asynchronous API, then users will deal with async/await context.
But for ordinal MIDI access like WinMM or CoreMIDI, they shouldn't take too much time to process. On the other hand, those MIDI messages can be sent in very short time, and in such case tons of awaits only cause extraneous burden on users apps.

For MIDI access implementations like RTP support, their send/receive operations should be still implemented to return immediately, while queuing messages on some other messaging.

For open and close operations, there wouldn't be too many calls and there is no performance concern.
