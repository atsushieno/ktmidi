package dev.atsushieno.ktmidi


class MergedMidiAccess(override val name: String, private val list: List<MidiAccess>) : MidiAccess() {
    private class MidiPortDetailsWrapper(
        val midiAccess: MidiAccess,
        val source: MidiPortDetails
    ) : MidiPortDetails {
        override val id = "${midiAccess.name}_${source.id}"
        override val manufacturer = source.manufacturer
        override val name = source.name
        override val version = source.version
        override val midiTransportProtocol = source.midiTransportProtocol
    }

    private class MidiInputWrapper(
        override val details: MidiPortDetailsWrapper,
        private val impl: MidiInput
    ) : MidiInput {
        override fun setMessageReceivedListener(listener: OnMidiReceivedEventListener) =
            impl.setMessageReceivedListener(listener)

        override val connectionState: MidiPortConnectionState by impl::connectionState

        override fun close() = impl.close()

    }

    private class MidiOutputWrapper(
        override val details: MidiPortDetailsWrapper,
        private val impl: MidiOutput
    ): MidiOutput {
        override fun send(mevent: ByteArray, offset: Int, length: Int, timestampInNanoseconds: Long) =
            impl.send(mevent, offset, length, timestampInNanoseconds)

        override val connectionState: MidiPortConnectionState by impl::connectionState

        override fun close() = impl.close()
    }

    override val inputs: Iterable<MidiPortDetails>
        get() = list.flatMap { access -> access.inputs.map { MidiPortDetailsWrapper(access, it) } }
    override val outputs: Iterable<MidiPortDetails>
        get() = list.flatMap { access -> access.outputs.map { MidiPortDetailsWrapper(access, it) } }

    override suspend fun openInput(portId: String): MidiInput {
        val details = inputs.first { it.id == portId } as MidiPortDetailsWrapper
        return MidiInputWrapper(details, details.midiAccess.openInput(details.source.id))
    }

    override suspend fun openOutput(portId: String): MidiOutput {
        val details = outputs.first { it.id == portId } as MidiPortDetailsWrapper
        return MidiOutputWrapper(details, details.midiAccess.openOutput(details.source.id))
    }

    @Deprecated("Use canCreateVirtualPort(PortCreatorContext) instead")
    override val canCreateVirtualPort: Boolean
        get() = list.any { it.canCreateVirtualPort }

    override val supportsUmpTransport: Boolean
        get() = list.any { it.supportsUmpTransport }

    override fun canCreateVirtualPort(context: PortCreatorContext) = list.any { it.canCreateVirtualPort(context) }

    override suspend fun createVirtualInputSender(context: PortCreatorContext): MidiOutput =
        list.firstOrNull { it.canCreateVirtualPort(context) }
            ?.createVirtualInputSender(context)
            ?: throw UnsupportedOperationException()

    override suspend fun createVirtualOutputReceiver(context: PortCreatorContext): MidiInput =
        list.firstOrNull { it.canCreateVirtualPort(context) }
            ?.createVirtualOutputReceiver(context)
            ?: throw UnsupportedOperationException()
}
