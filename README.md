# ktmidi: Kotlin Multiplatform library for MIDI 1.0 and MIDI 2.0

![maven repo](https://img.shields.io/maven-central/v/dev.atsushieno/ktmidi)

ktmidi is a Kotlin Multiplatform library for MIDI Access API and MIDI data processing that covers MIDI 1.0 and MIDI 2.0. 

## Features

It implements the following features (and so on):

- MIDI 1.0 and 2.0 UMPs everywhere.
- `MidiAccess` : MIDI access abstraction API like what Web MIDI API 1.0 is going to provide.
  - There are actual implementations for some platform specific MIDI API within this library, and you can implement your own backend if you need.
  - Unlike `javax.sound.midi` API, this API also covers creating virtual ports wherever possible.
    - `ktmidi-jvm-desktop` module actually contains ALSA MIDI Access implementation as well as [RtMidi](https://github.com/thestk/rtmidi) backend that support it. (Unsupported platforms are left unsupported.)
- `MidiMusic` and `Midi2Music` : reflects Standard MIDI File format structure, with reader and writer. (MIDI 2.0 support is not based on standard, as there is nothing like SMF specification for MIDI 2.0 yet.)
  - No strongly-typed message types (something like NoteOnMessage, NoteOffMessage, and so on). There is no point of defining strongly-typed messages for each mere MIDI status byte - you wouldn't need message type abstraction.
  - No worries, there are MidiCC, MidiRpnType, MidiMetaType, MidiEvent fields (of `Byte` or `Int`) and more, so that you don't have to remember the actual constants.
  - `SmfReader` reads and `SmfWriter` writes to SMF (standard MIDI format) files with MIDI messages, with `SmfTrackMerger` and `SmfTrackSplitter` that help you implement sequential event processing for your own MIDI players, or per-track editors if needed.
- `MidiPlayer` and `Midi2Player`: provides MIDI player functionality: play/pause/stop and fast-forwarding.
  - Midi messages are sent to its "message listeners". If you don't pass a Midi Access instance or a Midi Output instance, it will do nothing but event dispatching.
  - It is based on customizible scheduler `MidiTimer`.
  - Not realtime strict (as on GC-ed language / VM), but would suffice for general usage.

## Using ktmidi

Here is an example code excerpt to set up platform MIDI device access, load an SMF from some file, and play it:

```
// for some complicated reason we don't have simple "default" MidiAccess API instance
val access = if(File("/dev/snd/seq").exists()) AlsaMidiAccess() else JvmMidiAccess()
val bytes = Files.readAllBytes(Path.of(fileName)).toList()
val music = MidiMusic()
music.read(bytes)
val player = MidiPlayer(music, access)
player.play()
```

To use ktmidi, add the following lines in the `dependencies` section in `build.gradle`:

```
dependencies {
    implementation 'dev.atsushieno:ktmidi:+' // replace + with the actual version
}
```

The actual artifact might be platform dependent like `dev.atsushieno:ktmidi-android:+` or `dev.atsushieno:ktmidi-js:+`, depending on the project targets.

If you want to bring better user experience on desktop (which @atsushieno recommends as `javax.sound.midi` on Linux is quite featureless), add `ktmidi-jvm-desktop` too,


```
dependencies {
    implementation 'dev.atsushieno:ktmidi-jvm-desktop:+' // replace + with the actual version
}
```

... and use `AlsaMidiAccess` on Linux, or `RtMidiAccess` elsewhere. I use `if (File.exists("/dev/snd/seq")) AlsaMidiAccess() else RtMidiAccess()` (or `JvmMidiAccess` instead of `RtMidiAccess`) to create best `MidiAccess` instance. `RtMidiAccess` is still unstable to some extent, so it's up to your choice.

ktmidi is released at sonatype and hence available at Maven Central.

## Resources

We use [GitHub issues](https://github.com/atsushieno/ktmidi/issues) for bug reports etc., and [GitHub Discussions boards](https://github.com/atsushieno/ktmidi/discussions/) open to everyone.

For hacking and/or contributing to ktmidi, please have a look at [HACKING.md](HACKING.md).

For Applications that use ktmidi, check out [this Discussions thread](https://github.com/atsushieno/ktmidi/discussions/14).

API documentation is published at: https://atsushieno.github.io/ktmidi/

The documentation can be built using `./gradlew dokkaHtml` and it will be generated locally at `build/dokka/html`.

There are couple of API/implementation design docs:

- [docs/MidiAccess.md](docs/MidiAccess.md)
- [docs/MidiMusic.md](docs/MidiMusic.md)
- [docs/MidiPlayer.md](docs/MidiPlayer.md)


## Platform Access API

For platform MIDI access API, we cover the following APIs:

- `AndroidMidiAccess`: Android MIDI API (in Kotlin)
- `AlsaMidiAccess`: ALSA sequencer
- `RtMidiAccess`: RtMidi (which covers Windows, Mac, Linux, and iOS, but iOS is in general excluded in JVM solution)
- `JvmMidiAccess`: javax.sound.midi API (with limited feature set).

For dependency resolution reason, ALSA implementation and RtMidi implementation are split from `ktmidi-jvm` and formed as `ktmidi-jvm-desktop`.

ktmidi builds for Kotlin/JVM, Kotlin/JS and Kotlin/Native (though I only develop with Kotlin/JVM and Kotlin/JS so far).

The entire API is still subject to change, and it had been actually radically changing when development was most active.

## MIDI 2.0 support

ktmidi supports MIDI 2.0. It can be either in our own manner (i.e. it presumes MIDI 2.0 protocol is already established elsewhere), or partially using MIDI-CI protocol. It is up to apps... for example, [atsushieno/kmmk](https://github.com/atsushieno/kmmk) sends MICI-CI "Set New Protocol" message to the output devices, which kind of declares that it will be sending MIDI 2.0 UMPs (but without "Test New Protocol", as it requires bidirectional messaging that kmmk does not implement). If you want to follow MIDI-CI system exclusive messages, establish pair of MIDI input and output and handle message exchanges manually.

ktmidi assumes there are various other use-cases without those message exchanges e.g. use of UMPs in MIDI 2.0-only messaging in apps or audio plugins.

Since you can derive from `MidiAccess` abstract API, you can create your own MIDI access implementation and don't have to wait for platform native API to support MIDI 2.0. Note that MIDI 2.0 support is still an experimental work.

It would be useful for general MIDI 2.0 software tools such as MIDI 2.0 UMP player.

Here is a list of MIDI 2.0 extensibility in this API:

- `MidiInput` and `MidiOutput` now has `midiProtocol` property which can be get and/or set. When `MidiCIProtocolValue.MIDI2_V1` is specified, then the I/O object is supposed to process UMPs (Universal MIDI Packets).
- `Midi2Music` is a feature parity with `MidiMusic`, but all the messages are stored as UMPs. However, since SMF concepts of time calculation (namely delta time quantization / specification) is useful, we optionally blend it into UMPs and their JR Timestamp messages are actually fake - they store delta times just like SMF.
- `Midi2Player` is a feature parity with `MidiPlayer`.
- `UmpFactory` class contains a bunch of utility functions that are used to construct UMP integer values.
- `dev.atsushieno.ktmidi.ci` package contains a bunch of utility functions that are used to construct MIDI-CI system exclusive packets.

[atsushieno/kmmk](https://github.com) supports "MIDI 2.0 mode" which sends MIDI messages in MIDI 2.0 UMPs. There is also an ongoing experimental project to process MIDI 2.0 UMPs in [audio plugins on Android](https://github.com/atsushieno/android-audio-plugin-framework/tree/main/java/aap-midi-device-service).

### SMF alternative format

Since there is no comparable standard music file format like SMF for MIDI 2.0, we had to come up with our own. Our `Midi2Player` accepts files in the format described below:

```
// Data Format:
//   identifier: 0xAAAAAAAAAAAAAAAA (16 bytes)
//   i32 deltaTimeSpec
//   i32 numTracks
//   tracks
//        identifier: 0xEEEEEEEEEEEEEEEE (16 bytes)
//       i32 numUMPs
//       umps (i32, i64 or i128)
```

If `deltaTimeSpec` is a positive integer, it works like the value in SMF header chunk. There is no "format" specifier in this format - if "numTracks" is 1 then it is obviously compatible with FORMAT 0.

Also, we have a workaround for META events, now that system message has its own message type with fixed (limited) length - in this format they are stored as SYSEX8 messages with all 0s for manufacturer ID, device ID, and sub IDs.

[mugene-ng](https://github.com/atsushieno/mugene-ng) can generate music files based on this format.

## Historical background

It started as the Kotlin port of C# [managed-midi](https://github.com/atsushieno/managed-midi) library. Also it started with partial copy of [fluidsynth-midi-service-j](https://github.com/atsushieno/fluidsynth-midi-service-j) project.

However everything in this project went far beyond them and now we are making it usable for MIDI 2.0.

Some of the MIDI 2.0 related bits are ported from [cmidi2](https://github.com/atsushieno/cmidi2) library.

Historically `ktmidi-jvm-desktop` used to reside in [its own repository](https://github.com/atsushieno/ktmidi-jvm-desktop) to avoid complicated dependency resolution, so there would be some outdated information that tells it was isolated from this project/repository.

## License

ktmidi is distributed under the MIT license.

