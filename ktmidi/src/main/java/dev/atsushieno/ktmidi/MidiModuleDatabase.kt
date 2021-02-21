package dev.atsushieno.ktmidi

import java.io.InputStream
import java.io.OutputStream

abstract class MidiModuleDatabase
{
    companion object {
        val default : MidiModuleDatabase = DefaultMidiModuleDatabase ()

    }

    abstract fun all (): Iterable<MidiModuleDefinition>

    abstract fun resolve (moduleName: String) :MidiModuleDefinition
}

class MergedMidiModuleDatabase : MidiModuleDatabase
{
    constructor(sources: Iterable<MidiModuleDatabase>)
    {
        list = arrayListOf<MidiModuleDatabase> ()
    }

    val list: List<MidiModuleDatabase>

    override fun all(): Iterable<MidiModuleDefinition>
    {
        return list.flatMap { d -> d.all() }
    }

    override fun resolve(moduleName: String): MidiModuleDefinition {
        return list.map { d -> d.resolve (moduleName)}.first ()
    }
}

class DefaultMidiModuleDatabase : MidiModuleDatabase
{
    companion object {

        /*
    static readonly Assembly ass = typeof (DefaultMidiModuleDatabase).GetTypeInfo ().Assembly;

    // am too lazy to adjust resource names :/
    public static Stream GetResource (string name)
    {
        return ass.GetManifestResourceStream (name) ?? ass.GetManifestResourceStream (
        ass.GetManifestResourceNames ().FirstOrDefault (m =>
        m.EndsWith (name, StringComparison.OrdinalIgnoreCase)));
    }
    */
        fun getResource(name: String): InputStream {
            throw NotImplementedError()
        }
    }

    constructor()
    {
        modules = arrayListOf<MidiModuleDefinition> ();
        var catalog = java.io.InputStreamReader(getResource ("midi-module-catalog.txt")).readText().split ('\n');
        for (filename in catalog)
        if (filename.length > 0)
            modules.add (MidiModuleDefinition.load (getResource (filename)));
    }

    override fun all() : Iterable<MidiModuleDefinition> { return modules }

    override fun resolve(moduleName: String) : MidiModuleDefinition
    {
        var name = resolvePossibleAlias (moduleName);
        return modules.first {m -> m.name == name} ?: modules.first { m -> m.match != null && Regex.fromLiteral (m.match!!).matches(name) || name.contains (m.name!!)}
    }

    fun resolvePossibleAlias (name: String) : String
    {
        when (name) {
            "Microsoft GS Wavetable Synth" -> return "Microsoft GS Wavetable SW Synth";
        }
        return name;
    }

    val modules : List<MidiModuleDefinition>
}

class MidiModuleDefinition {
    var name: String? = null

    var match: String? = null

    var instrument = MidiInstrumentDefinition()

    // serialization

    fun save(stream: OutputStream) {
        throw NotImplementedError()
        //var ds = new DataContractJsonSerializer (typeof (MidiModuleDefinition));
        //ds.WriteObject (stream, this);
    }

    companion object {

        fun load(stream: InputStream): MidiModuleDefinition {
            throw NotImplementedError()
            //var ds = new DataContractJsonSerializer (typeof (MidiModuleDefinition));
            //return (MidiModuleDefinition) ds.ReadObject (stream);
        }
    }
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
