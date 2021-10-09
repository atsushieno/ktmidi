# MIDI 2.0 UMP music data formats

`Midi2Music` is MIDI 2.0 feature parity with SMF (Standard MIDI Files). However, since MIDI 2.0 does not come up with Standard music file formats yet, anything other than `Ump` is only to achieve feature parity with `MidiMusic`.

## timestamp formats

We provide two options for UMP-based music files:

- `*.umpmd` format, whose `Ump` content is fully compatible with UMP using plain UMP JR timestamps.
- `*.umpx` format, where JR Timestamp messages are "fake" that actually holds SMF-compatible delta time.

`deltaTimeSpec` is used for the same purpose as that of `MidiMusic`  (the value in SMF is to distinguish the timestamp values).

## data formats

`Midi2Music` and therefore `Midi2Player` accept files in the format described below:

```
// Data Format:
//   identifier: 0xAAAAAAAAAAAAAAAA (16 bytes)
//   i32 deltaTimeSpec
//   i32 numTracks
//   tracks - each track contains:
//       identifier: 0xEEEEEEEEEEEEEEEE (16 bytes)
//       i32 numUMPs
//       umps (i32, i64 or i128)
```

If `deltaTimeSpec` is a positive integer, it works like the value in SMF header chunk. There is no "format" specifier in this format - if "numTracks" is 1 then it is obviously compatible with FORMAT 0.

## META events

We also have a hack around SMF META events; they are not part of MIDI 1.0 messages specification. In SMF specification, it "hijacks" MIDI system real-time messages (`FFh`). In our UMP format, they are stored as sysex8 messages which start with the following 8 bytes:

- `0` manufacture ID byte
  - We don't use Universal Sysex here as it is *more* likely to collide with later MMA specifications.
- `0` DeviceID byte
- `0` SubID byte
- `0` SubID2 byte
- `0xFFFFFF` indicates our META event (3 bytes)
- A meta event type byte

Then the actual meta event message content is enclosed within the sysex8 packets, *without length specifier*. The length can be identified by the packet itself. One sysex8 packet suffice for a fixed-length META event (5 bytes at maximum).
