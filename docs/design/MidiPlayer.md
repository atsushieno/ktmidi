
# MidiPlayer API

`MidiPlayer` provides somewhat featureful SMF playback engine.

The constructor takes `MidiAccess` or `MidiOutput` instance and a `MidiMusic` instance, which is then used to send MIDI messages in timely manner.

## playback control

There are `play()`, `pause()` and `stop()` methods to control playback state.
`MidiPlayer` can be used to play the song many times.

There is also `seek()` method that takes delta time ticks. The implementation is somewhat complicated, see design section later on this page.

## tempo and time

While playing the song, it keeps track of tempo and time signature information from META events.
Raw `tempo` property value is not very helpful to normal users, so there is also `bpm` property.

It also provides `playDeltaTime` which is the amount of ticks as of current position.
Raw delta time value is not very helpful either, so there is also `positionInMilliseconds` property.
`positionInMilliseconds` actually involves contextual calculation regarding tempo changes and delta times in the past messages, because conversion from clock counts to TimeSpan requires information on when tempo changes happened.
Therefore this property is not for casual consumption.

There is also `totalPlayTimeMilliseconds` property which returns the total play time of the song in milliseconds.

`MidiPlayer` supports fast-forwarding, or slow playback via `tempoChangeRatio` property.

## MIDI event notification

`MidiPlayer` provides `eventReceived` property that can be used like an event.

## MIDI 2.0 support

`MidiPlayer2` tries to achieve feature parity with `MidiPlayer` for MIDI 2.0 UMP-based music structure represented as `Midi2Music` which is also a MIDI 2.0 feature parity with `MidiMusic`. It is still an ongoing work.

The usage is similar, but since MIDI 2.0 UMP does not come up with the concept of ticks with "beat", there will be some lack of features.


# Design notes


## Driver-agnostic MIDI player

MidiPlayer is designed to become platform-agnostic.

MidiPlayer itself does not access platform-specific MIDI outputs.
IMidiAccess and IMidiOutput are the interfaces that provides raw MIDI access, and they are implemented for each platform-supported backends.

Raw MIDI access separation makes it easy to test MIDI player functionality without any platform-specific hussle, especially with `MidiAccessManager.empty` (NO-OP midi access implementation) and `VirtualMidiPlayerTimer` explained later.


## Non-realtime event driven approach

In audio processing world, music playback engine would be implemented in realtime-safe manner. That is, it should not use any thread blockers like mutex, and all MIDI inputs (if any) are processed with audio inputs (if any) at a time.

Since audio processing is based on chunked buffers, MIDI inputs are also buffered in such processing approach.

Our `MidiPlayer` and `Midi2Player` is NOT realtime safe. It is simpler event based implementation that makes use of mutex. Kotlin programs build on top of garbage collectors and JIT engines, so expecting realtime safety is awkward.


## Format 0

When dealing with sequential MIDI messages, it is much easier if every MIDI events from all the tracks are unified in one sequence.
Therefore MidiPlayer first converts the song to "Format 0" which has only one track.

MIDI 2.0 is also covered, we have the same kind of track merger and splitter in the same mindset.


## SMPTE vs. clock count

MidiPlayer basically doesn't support SMPTE. It is primarily used for serializing MIDI device inputs in real time, not for structured music.
It affects tempo calculation, and so far MidiPlayer aims to provide features for structured music.

(This does not really make sense as our MIDI 2.0 player is based on JR timestamp which is basically finer SMPTE.)


## MidiPlayerTimeManager

One of the annoyance with audio and MIDI API is that they involve real-world time.
If you want to test any code that plays some song that lasts 3 minutes, you don't want to actually wait for 3 minutes.
There should be some fake timer when testing something.

`MidiPlayerTimer` is designed to make it happen. You can consider it similar to Reactive-X "schedulers", which is also designed to make (occasionally) timed streams easily testable.

There is `SimpleAdjustingMidiPlayerTimer` which is based on real-world time, and `VirtualMidiPlayerTimer` where its users are supposed to manually control time progress.
The latter class provides two time controller methods:

- `waitBy()` is called by `MidiPlayer` internals, to virtually wait for the next event. Call to this method can cause blocking, until the virtual time "proceeds".
- `proceedBy()` is called by developers so that `MidiPlayer` can process next events. This method unblocks the player (caused by `waitBy()` calls)


`waitBy()` is actually the interface method that every timer has to implement.

`SimpleAdjustingMidiPlayerTimer` does somewhat complicated tasks beyond mere `delay()` - it remembers the last MIDI event and real-world time, and calculates how long it should actually wait for.
It works as a timestamp adjuster. It is important for MIDI players to play the song in exact timing, so someone needs to adjust the time between events.
Programs can delay at any time (especially .NET runtime can pause long time with garbage collectors) and it is inevitable, but this class plays an important role here to minimize the negative impact.


## MidiEventLooper

`MidiEventLooper` is an internal class to process MIDI messages in timely manner.
It's an internal that users have no access. MidiPlayer users it to control
play/pause/stop state, as well as give tempo changes (time stretching).

There could be more than one implementation for the event looper - current implementation "blocks" MIDI message "waits" in possibly real-world time.
To avoid that, we could implement the time manager so that it loops in very short time like per clock count, but it will consume too much computing resource, so we avoided that.
Those who have no power problem (e.g. on desktop PC) might want to have event loopers which is precisely controllable - we might provide choices (not in high priority now).


## SeekProcessor

`MidiPlayer` supports seek operation i.e. it can jump to any point specified as in clock count (delta time).

Implementing seek operation is not very simple. THere are couple of requirements.

First, it must mute ongoing notes. Without note-offs, the MIDI output device will keep playing extraneous notes.

Second, it cannot directly jump to the event at the specified time and play, because those MIDI channels may hold different values program changes, control changes, pitchbends and so on.
To reproduce precise values for them, the player first needs to go back to the top of the song, process those events with no time, skipping any note events.

Third, optionally, there will be a bunch of "ignorable" events when processing those events from the top of the song.
Consider pitch bend changes - they quickly changes in very short delta time, can be thousands, but in the end they would reset to zero or some fixed value.
Modern MIDI devices won't be in trouble, but classic MIDI devices may have trouble dealing with thousands of events in milliseconds.

Currently MidiPlayer implements solutions for the first two problems.
The third problem is something that had better be resolved, but so far we're not suffered from it very much.

It is also possible that developers want to control which kind of messages should be processed, especially regarding NRPNs, sysex, and meta events, because they might function like note operations (e.g. "vocaloid" made use of some of those messages).
For such uses, we should rather provide customizible seek processors. It is actually why there is ISeekProcessor interface.
However we are still unsure if current interface API is good for that. Therefore it is not exposed to the public yet. Feedbacks are welcome.

There is also room for `MidiMachine` class that can "remember" status of each MIDI channel. It works like a pseudo MIDI device.
