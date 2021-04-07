package dev.atsushieno.ktmidi

import java.io.InputStream
import java.io.OutputStream

val defaultMidiModuleDatabase = DefaultMidiModuleDatabase()

fun saveMidiModuleDefinition(stream: OutputStream, module: MidiModuleDefinition) {
    throw NotImplementedError()
    //var ds = new DataContractJsonSerializer (typeof (MidiModuleDefinition));
    //ds.WriteObject (stream, this);
}

// serialization

fun loadMidiModuleDefinition(stream: InputStream): MidiModuleDefinition {
    throw NotImplementedError()
    //var ds = new DataContractJsonSerializer (typeof (MidiModuleDefinition));
    //return (MidiModuleDefinition) ds.ReadObject (stream);
}


class DefaultMidiModuleDatabase : MidiModuleDatabase {
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
        fun getResource(name: String): InputStream = this::class.java.classLoader!!.getResourceAsStream(name)
    }

    constructor() {
        modules = arrayListOf<MidiModuleDefinition>();
        var catalog = java.io.InputStreamReader(getResource("midi-module-catalog.txt")).readText().split('\n');
        for (filename in catalog)
            if (filename.length > 0)
                modules.add(loadMidiModuleDefinition(getResource(filename)));
    }

    override fun all(): Iterable<MidiModuleDefinition> {
        return modules
    }

    override fun resolve(moduleName: String): MidiModuleDefinition {
        var name = resolvePossibleAlias(moduleName);
        return modules.first { m -> m.name == name } ?: modules.first { m ->
            m.match != null && Regex.fromLiteral(m.match!!).matches(name) || name.contains(m.name!!)
        }
    }

    fun resolvePossibleAlias(name: String): String {
        when (name) {
            "Microsoft GS Wavetable Synth" -> return "Microsoft GS Wavetable SW Synth";
        }
        return name;
    }

    val modules: List<MidiModuleDefinition>
}

