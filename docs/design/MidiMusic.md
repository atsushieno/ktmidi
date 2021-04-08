
# SMF structure

`MidiMusic` class is to represent a Standard MIDI File format compliant song.

An SMF consists of (1) a header chunk and (2) track chunks.
A track is represented as `MidiTrack` class, and it just contains a set of
`MidiMessage` structures.
A `MidiMessage` is a timed `MidiEvent` structure, where "time" is represented
(in Int32) as a "delta time".


- MidiMusic
  - `deltaTimeSpec` part of header chunk (negative SMTPe or positive clock count for quarter notes)
  - `format` part of header chunk (0 or 1)
  - MidiTrack list
    - MidiMessage list
      - DeltaTime
      - MidiEvent
        - byte array `data` for `F0` (sysex) and `FF` (meta) messages. null for non-Fx event.
        - int32 `value`, which bundles status byte, MSB, and LSB.

There is also feature parity with MIDI 2.0 UMP support, named `Midi2Music`, `Midi2Track`, and `Ump`. However, since MIDI 2.0 does not come up with Standard music file format like SMF, the anything other than `Ump` is only to achieve feature parity with `MidiMusic`.


# Design notes

## simple raw bytes oriented API

Regarding how to represent MIDI messages, there are two kinds of MIDI libraries:

- has strongly-typed MIDI messages for each message type e.g. NoteOnMessage, NoteOffMessage, PitchBendMessage etc.
- represents MIDI messages only in byte arrays.

managed-midi is basically latter, passing raw bytes around.
It is for better performance - MIDI access APIs shouldn't have to spend computing resources on converting bytes and strongly-typed arrays around.

But for `MidiPlayer`, it does convert those raw bytes to those `MidiMessage` arrays. `SmfReader` parses SMFs to `MidiMusic`, and `SmfWriter` writes to `MutableList<Byte>`. For most of MIDI programmers would just need these classes to deal with SMFs.

There are also for MIDI 2.0 support: `Midi2MusicReader` and `Midi2MusicWriter`.
