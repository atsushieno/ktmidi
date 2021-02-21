ktmidi is an experimental MIDI Access API abstraction and MIDI data processing library for Kotlin. It implements the following features (and so on):

- `MidiAccess` : MIDI access abstraction API like what Web MIDI API 1.0 is going to provide.
- `MidiMusic` : reflects Standard MIDI File format structure, with reader and writer.
- `MidiPlayer` : basic MIDI player functionality based on customizible scheduler. Not realtime strict (as on GC-ed language / VM), but would suffice for general usage.

It is in general Kotlin port of C# [managed-midi](https://github.com/atsushieno/managed-midi) library. Also it is mostly copied from [fluidsynth-midi-service-j](https://github.com/atsushieno/fluidsynth-midi-service-j) project.

Currently there is no "driver" implementation.

## License

ktmidi is distributed under the MIT license.

