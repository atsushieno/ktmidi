
# MIDI-CI support in ktmidi

## Overview

ktmidi has comprehensive MIDI-CI support as well as its dogfooding "ktmidi-ci-tool". They are almost feature complete. The features include:

- All those MIDI-CI SysEx messages that MIDI-CI version 1.2 specification defines
- Profile Configuration
  - Initiator can query the profiles on the connected device
  - Responder can configure its own profile settings
  - Set Profile On/Off with Enabled/Disabled Reports
  - Profile Details inquiry (results are visible on the logs)
- Property Exchange 
  - Get, Set, and Subscribe to property (also unsubscribing from it), with corresponding notifications.
  - Metadata system based on Common Rules for Property Exchange version 1.1 specification.
  - mediaType aware content editor on either `TextField` or binary file uploads
  - Partial value updates based on RFC 6901 (JSON Pointer), and of course, `full` updates too, as per Common Rules for PE specification, section 8.
  - Paginated list queries as per Common Rules for PE specification, section 6.6.2.
  - mutualEncoding: ASCII, Mcoded7, and zlib+Mcoded7 (to be implemented ubiquitously)
- Process Inquiry
  - implemented everything in logic, along with `Midi1Machine` or `Midi2Machine` in ktmidi module
  - actual implementation in `ktmidi-ci-tool` is only for MIDI 1.0 bytestream
  - UI is only for requesting. Responses are simply logged (although nicely chunked)
- Both Profile Configuration and Property Exchange are designed to be MIDI-CI core version agnostic in `ktmidi-ci` module
   - if those "Common Rules" specification or any alternatives are published then those client and hosting features can be upgraded independent of the Core implementation.
- Transport agnostic: MIDI-CI support resides in its own `ktmidi-ci` module, which does not depend on `ktmidi`.
  - We could make it to send arbitrary SMF (`Midi1Music`) or MIDI Clip File (`Midi2Track` maybe), we just need app implementation...

## Known missing features

- Good API design; everything has been messed throughout the development
- Tests (I am usually test-driven, but I did not come up with good API structure in the first place this time...)
- UMP support; ktmidi-ci-tool is built upon platform `MidiAccess` API which currently does not support MIDI 2.0 except for ALSA virtual devices.
- App lifecycle management
- zlib+Mcoded7 support on some platforms: ubiquitous zlib implementation is currently on hold until ktor-io 3.0.0 comes up on Kotlin/Wasm. Also, I cannot find any implementation that has no issues.
- Profile specific messages (seeing no use at the moment)
- Any timer based session management (i.e. no timeouts implemented)
  - Therefore, no request ID management; it simply increments within `Byte`

## Code Structure

Modules:

- `ktmidi-ci` implements the actor model as well as CI message serialization.
  - Targets `jvm`, `android`, `js` (IR), `wasmJs` (experimental), and Kotlin/Native platforms including iOS.
  - It is designed to NOT have `ktmidi` as a dependency, so far.
- `ktmidi-ci-tool` is a Compose Multiplatform app that wraps `ktmidi-ci` models within the observable repository and views (views includes view models here).
  - No particular architecture model applied yet. Probably [Essenty](https://github.com/arkivanov/Essenty) if I choose one.
  - Targets JVM desktop, Android, iOS, and wasm/JS. Not covering Kotlin/Native, JS (non-wasm), and wasmWasi (should match what Compose Multiplatform covers)
  - iOS has no `MidiAccess` implementation, so while the UI runs, the app itself is useless so far. `CoreMidiAccess` work in progress (but not in high priority).

Classes:

- In `ktmidi-ci` module:
  - `MidiCIDevice` plays the primary role. It works as the facade for most of the MIDI-CI features. It holds `initiator` and `responder` for now, but we have been making significant changes in the structure, so do not count on them to exist.
  - `Message` and all those subclasses represent MIDI-CI SysEx messages. Data model and serialization in most classes (not all)
  - `ObservableProfileList` and `ObservablePropertyList` holds profile and properties (both values and metadata for now) that can notify listeners. Models in `ktmidi-ci-tool` make use of them.
  - `MidiCIPropertyClient` and `MidiCIPropertyService` exists to decouple "Common Rules for PE" specific implementation from the core - ideally. They still inject the core API in reality yet.
    - The actual Common Rules for PE implementation lies in `dev.atsushieno.ktmidi.ci.propertycommonrules` package.
- In `ktmidi-ci-tool` module:
  - `dev.atsushieno.ktmidi.citool.view` package contains `@Composable`s.
  - `CIToolRepository` is supposed to work as the repository facade.
  - `SavedSettings` works as the serializable saved configuration. We use kotlinx.Serialization JSON to save the settings.