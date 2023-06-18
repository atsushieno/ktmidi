# MIDI 2.0 UMP music data formats

`Midi2Music` is MIDI 2.0 feature parity with SMF (Standard MIDI Files). However, since MIDI 2.0 does not come up with Standard music file formats yet, anything other than `Ump` is only to achieve feature parity with `MidiMusic`.

Note that "SMF2" a.k.a MIDI Clip File specification does NOT represent a complete music structure; it can only represent an alternative to one track in SMF at best. Therefore, we still need our own music file format until MMA publishes "MIDI Container File" format (or "SMF2 multitrack", whatever they call).

## timestamp formats

We provide two options for UMP-based music files:

- `*.umpmd` format, whose `Ump` content is fully compatible with UMP using plain UMP JR timestamps.
- `*.umpx` format, where it makes full use of the latest Delta Clockstamps messages in UMP June 2023 updates. We recoomend to use it. See also the data formats section for "deltaTimeSpec".

## data formats

`Midi2Music` and therefore `Midi2Player` accept files in the format described below. Note that it has been revised to reflect UMP June 2023 updates.

```
// Data Format:
//   - identifier "AAAAAAAAEEEEEEEE" in ASCII (16 bytes)
//   - i32 deltaTimeSpec
//   - i32 numTracks
//   - tracks - each track contains these items in order:
//     - identifier "SMF2CLIP" in ASCII (8 bytes)
//     - DCS(0) + DCTPQ
//     - DCS(0) + Start of Clip
//     - sequence of DCS-prepended UMPs (i32, i64 or i128) in BIG endian.
//     - DCS(0) + "End of Clip".
```

`deltaTimeSpec` is used for the same purpose as that of `MidiMusic`  (the value in SMF is to determine the semantics on the timestamp values). We would emit DCTPQ (Delta Clockstamps Ticks Per Quater notes) messages in each track, but since we need a *uniform* and *consistent* single deltaTimeSpec across ALL the tracks, it is ignored. We will use the value in the header section.

If `deltaTimeSpec` is a positive integer, it works like the `division` field value in SMF header chunk. There is no "format" specifier in this format - if "numTracks" is 1 then it is obviously compatible with FORMAT 0.

All the UMPs are serialized in BIG endian per 32-bit integer for persistant stability across platforms and better binary readability.

To conform to MIDI Clip File format, All those UMPs in a track come with Delta Clockstamp. If we support Profile Configuration UMPs then they will not come with DCS (as per MIDI Clip File specification describes), but we do not, so far.

## META events that cannot be mapped 

Meta eventsey are not part of MIDI 1.0 messages specification. In SMF specification, it "hijacks" MIDI system real-time messages (`FFh`). In `MidiMusic` for SMF1, every meta event type is supported as is. 

MIDI 2.0 UMP used to not have any corresponding messages before June 2023 updates, simply. In the latest UMP spefification, there are Flex Data messages that are to support SMF meta events alike. Performance binary events such as Set Tempo, Set Time Signature, Set Key Signature, etc. as well as some text events such as Copyright Notice, Composer (metadata text), and Lyrics (performance text) are covered. 

However, Marker, Cue, and Instrument Name SMF meta events have no corresponding Flex Data messages. Therefore, we still have a hack around SMF META events for them.

For those SMF Meta events that do not have the corresponding representation, In our UMP format, they are stored as sysex8 messages which start with the following 8 bytes:

- `0` manufacture ID byte
  - We don't use Universal Sysex here as it is *more* likely to collide with later MMA specifications.
- `0` DeviceID byte
- `0` SubID byte
- `0` SubID2 byte
- `0xFFFFFF` indicates our META event (3 bytes)
- A meta event type byte

Then the actual meta event message content is enclosed within the sysex8 packets, *without length specifier*. The length can be identified by the packet itself. One sysex8 packet suffice for a fixed-length META event (5 bytes at maximum).
