@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package dev.atsushieno.alsakt

object AlsaClientType {
    const val Kernel = 0
    const val User = 1
}

object AlsaIOMode {
    const val None = 0
    const val NonBlocking = 1
}

object AlsaIOType {
    const val Output = 1
    const val Input = 2
    const val Duplex = Output or Input
}

object AlsaPortCapabilities {
    const val
            /**< readable from this port  */
            Read = 1 shl 0

    /**
     * < writable to this port
     */
    const val Write = 1 shl 1

    /**
     * < for synchronization (not implemented)
     */
    const val SyncRead = 1 shl 2

    /**
     * < for synchronization (not implemented)
     */
    const val SyncWrite = 1 shl 3

    /**
     * < allow read/write duplex
     */
    const val Duplex = 1 shl 4
    @Deprecated("Use Duplex", ReplaceWith("Duplex"))
    const val Duple = 1 shl 4

    /**
     * < allow read subscription
     */
    const val SubsRead = 1 shl 5

    /**
     * < allow write subscription
     */
    const val SubsWrite = 1 shl 6

    /**
     * < routing not allowed
     */
    const val NoExport = 1 shl 7

    /**
     * < inactive port
     */
    const val Inactive = 1 shl 8

    /**
     * < UMP Endpoint
     */
    const val UmpEndpoint = 1 shl 9
}

object AlsaPortDirection {
    const val Unknown = 0
    const val Input = 1
    const val Output = 2
    const val Bidirection = 3
}

object AlsaPortType {
    const val
            /** Messages sent from/to this port have device-specific semantics.  */
            Specific = 1

    /**
     * This port understands MIDI messages.
     */
    const val MidiGeneric = 1 shl 1

    /**
     * This port is compatible with the General MIDI specification.
     */
    const val MidiGM = 1 shl 2

    /**
     * This port is compatible with the Roland GS standard.
     */
    const val MidiGS = 1 shl 3

    /**
     * This port is compatible with the Yamaha XG specification.
     */
    const val MidiXG = 1 shl 4

    /**
     * This port is compatible with the Roland MT-32.
     */
    const val MidiMT32 = 1 shl 5

    /**
     * This port is compatible with the General MIDI 2 specification.
     */
    const val MidiGM2 = 1 shl 6

    /**
     * This port is a UMP port.
     */
    const val Ump = 1 shl 7

    /**
     * This port understands SND_SEQ_EVENT_SAMPLE_xxx messages
     * (these are not MIDI messages).
     */
    const val Synth = 1 shl 10

    /**
     * Instruments can be downloaded to this port
     * (with SND_SEQ_EVENT_INSTR_xxx messages sent directly).
     */
    const val DirectSample = 1 shl 11

    /**
     * Instruments can be downloaded to this port
     * (with SND_SEQ_EVENT_INSTR_xxx messages sent directly or through a queue).
     */
    const val Sample = 1 shl 12

    /**
     * This port is implemented in hardware.
     */
    const val Hardware = 1 shl 16

    /**
     * This port is implemented in software.
     */
    const val Software = 1 shl 17

    /**
     * Messages sent to this port will generate sounds.
     */
    const val Synthesizer = 1 shl 18

    /**
     * This port may connect to other devices
     * (whose characteristics are not known).
     */
    const val Port = 1 shl 19

    /**
     * This port belongs to an application, such as a sequencer or editor.
     */
    const val Application = 1 shl 20
}

object AlsaSequencerType // seq.h (62, 14)
{
    const val Hardware = 0
    const val SharedMemory = 1
    const val Network = 2
}
object AlsaSubscriptionQueryType {
    const val Read = 0
    const val Write = 1
}

object AlsaQueueTimerType {
    const val Alsa = 0
    const val MidiClock = 1
    const val MidiTick = 2
}

object AlsaRemoveFlags {
    const val Input = 1 shl 0
    const val Output = 1 shl 1
    const val Destination = 1 shl 2
    const val DestinationChannel = 1 shl 3
    const val TimeBefore = 1 shl 4
    const val TimeAfter = 1 shl 5
    const val TimeTick = 1 shl 6
    const val EventType = 1 shl 7
    const val IgnoreOff = 1 shl 8
    const val TagMatch = 1 shl 9
}

object AlsaSequencerEventType {
    const val System = 0
    const val Result = 1
    const val Note = 5
    const val NoteOn = 6
    const val NoteOff = 7
    const val KeyPress = 7
    const val Controller = 10
    const val ProgramChange = 11
    const val ChannelPressure = 12
    const val PitchBend = 13
    const val Control14 = 14
    const val Nprn = 15
    const val Rpn = 16
    const val SongPos = 20
    const val SongSel = 21
    const val QFrame = 22
    const val TimeSign = 23
    const val KeySign = 24
    const val Start = 30
    const val Continue = 31
    const val Stop = 32
    const val SetPositionTick = 33
    const val SetPositionTime = 34
    const val Tempo = 35
    const val Clock = 36
    const val Tick = 37
    const val QueueSkew = 38
    const val SyncPosition = 39
    const val TuneRequest = 40
    const val Reset = 41
    const val Sensing = 42
    const val Echo = 50
    const val Oss = 52
    const val ClientStart = 60
    const val ClientExit = 61
    const val ClientChange = 62
    const val PortStart = 63
    const val PortExit = 64
    const val PortChange = 65
    const val PortSubscribed = 66
    const val PortUnsubscribed = 67
    const val User0 = 90
    const val User1 = 91
    const val User2 = 92
    const val User3 = 93
    const val User4 = 94
    const val User5 = 95
    const val User6 = 96
    const val User7 = 97
    const val User8 = 98
    const val User9 = 99
    const val Sysex = 130
    const val Bounce = 131
    const val UserVar0 = 135
    const val UserVar1 = 136
    const val UserVar2 = 137
    const val UserVar3 = 139
    const val UserVar4 = 139
    const val None = 255
}