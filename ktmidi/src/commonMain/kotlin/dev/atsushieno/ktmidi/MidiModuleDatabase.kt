package dev.atsushieno.ktmidi

abstract class MidiModuleDatabase {
    abstract fun all(): Iterable<MidiModuleDefinition>

    abstract fun resolve(moduleName: String): MidiModuleDefinition
}

class MergedMidiModuleDatabase : MidiModuleDatabase {
    constructor(sources: Iterable<MidiModuleDatabase>) {
        list = arrayListOf<MidiModuleDatabase>()
    }

    val list: List<MidiModuleDatabase>

    override fun all(): Iterable<MidiModuleDefinition> {
        return list.flatMap { d -> d.all() }
    }

    override fun resolve(moduleName: String): MidiModuleDefinition {
        return list.map { d -> d.resolve(moduleName) }.first()
    }
}

class MidiModuleDefinition {
    var name: String? = null

    var match: String? = null

    var instrument = MidiInstrumentDefinition()
}

class MidiInstrumentDefinition {
    var maps = arrayListOf<MidiInstrumentMap>()

    var drumMaps = arrayListOf<MidiInstrumentMap>()
}

class MidiInstrumentMap {
    var name: String? = null

    var programs = arrayListOf<MidiProgramDefinition>()
}

class MidiProgramDefinition {
    var name: String? = null
    var index: Int = 0

    var banks = arrayListOf<MidiBankDefinition>()
}

class MidiBankDefinition {
    var name: String? = null
    var msb: Int = 0
    var lsb: Int = 0
}
