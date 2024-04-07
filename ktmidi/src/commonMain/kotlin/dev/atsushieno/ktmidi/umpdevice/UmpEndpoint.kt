package dev.atsushieno.ktmidi.umpdevice

import dev.atsushieno.ktmidi.*

class UmpEndpoint(val config: UmpEndpointConfiguration) {

    val errorHandlers = mutableListOf<(String)->Unit>()

    // They are invoked when it received a sequence of UMP inputs.
    // Then the session input handler is performed.
    val messageReceived = mutableListOf<(Sequence<Ump>)->Unit>()

    // They work as the output handlers.
    // Your app is supposed to add your connected MIDI output sender (such as `MidiOutput.send()`)
    val outputSenders = mutableListOf<(Sequence<Ump>)->Unit>()

    var targetEndpoint = UmpEndpointConfiguration("", "", UmpDeviceIdentity.empty,
        UmpEndpointConfiguration.UmpStreamConfiguration(false, false, false, false), false, mutableListOf())

    var autoSendFunctionBlockDiscovery = true

    fun sendDiscovery() {
        sendOutput(sequenceOf(
            UmpFactory.endpointDiscovery(
                1, 1,
                UmpDiscoveryFlags.ALL
            )
        ))
    }

    fun sendFunctionBlockDiscovery(index: Int) {
        sendOutput(sequenceOf(UmpFactory.functionBlockDiscovery(index.toByte(), FunctionBlockDiscoveryFlags.ALL)))
    }

    private fun handleError(message: String) = errorHandlers.forEach { it(message) }

    fun sendOutput(messages: Sequence<Ump>) = outputSenders.forEach { it(messages) }

    private fun <T> iteratePrepended(head: T, tail: Iterator<T>) = iterator {
        yield(head)
        yieldAll(tail)
    }

    fun processInput(messages: Sequence<Ump>) {
        messageReceived.forEach {
            it(messages)
        }
        var iterator = messages.iterator()
        while (iterator.hasNext()) {
            val ump = iterator.next()
            if (!ump.isUmpStream)
                continue
            // note that we have to use statusByte, not statusCode, for UMP stream messages
            when (ump.statusByte.toByte()) {
                // client
                UmpStreamStatus.ENDPOINT_INFO_NOTIFICATION -> {
                    if (ump.endpointInfoUmpVersionMajor != 1 || ump.endpointInfoUmpVersionMinor != 1) {
                        handleError("Unsupported UMP version in UMP endpoint info notification: ${ump.endpointInfoUmpVersionMajor}.${ump.endpointInfoUmpVersionMinor}")
                        continue
                    }
                    targetEndpoint.streamConfiguration = UmpEndpointConfiguration.UmpStreamConfiguration(
                        ump.endpointInfoMidi1Capable,
                        ump.endpointInfoMidi2Capable,
                        ump.endpointInfoSupportsRxJR,
                        ump.endpointInfoSupportsTxJR)
                    targetEndpoint.functionBlocks.clear()
                    (0 until ump.endpointInfoFunctionBlockCount).forEach {
                        targetEndpoint.functionBlocks.add(FunctionBlock(it.toByte()))
                    }
                    if (autoSendFunctionBlockDiscovery)
                        (0 until ump.endpointInfoFunctionBlockCount).forEach {
                            sendFunctionBlockDiscovery(it)
                        }
                }
                UmpStreamStatus.ENDPOINT_NAME_NOTIFICATION -> {
                    iterator = iteratePrepended(ump, iterator)
                    targetEndpoint.name = UmpRetriever.getEndpointName(iterator)
                }
                UmpStreamStatus.PRODUCT_INSTANCE_ID_NOTIFICATION -> {
                    iterator = iteratePrepended(ump, iterator)
                    targetEndpoint.productInstanceId = UmpRetriever.getProductInstanceId(iterator)
                }
                UmpStreamStatus.DEVICE_IDENTITY_NOTIFICATION ->
                    targetEndpoint.deviceIdentity = UmpDeviceIdentity(
                        ump.deviceIdentificationManufacturer,
                        ump.deviceIdentificationFamily,
                        ump.deviceIdentificationModelNumber,
                        ump.deviceIdentificationSoftwareRevisionLevel)
                UmpStreamStatus.STREAM_CONFIGURATION_NOTIFICATION ->
                    targetEndpoint.streamConfiguration = UmpEndpointConfiguration.UmpStreamConfiguration(
                        (ump.streamConfigProtocol and 1) != 0,
                        (ump.streamConfigProtocol and 2) != 0,
                        ump.streamConfigSupportsRxJR,
                        ump.streamConfigSupportsTxJR)
                UmpStreamStatus.FUNCTION_BLOCK_NAME_NOTIFICATION ->
                    if (ump.functionBlockIndex < targetEndpoint.functionBlocks.size) {
                        iterator = iteratePrepended(ump, iterator)
                        targetEndpoint.functionBlocks[ump.functionBlockIndex.toInt()].name =
                            UmpRetriever.getFunctionBlockName(iterator)
                    }
                    else
                        handleError("Function Block Name Notification specified index out of range: ${ump.functionBlockIndex}")
                UmpStreamStatus.FUNCTION_BLOCK_INFO_NOTIFICATION ->
                    if (ump.functionBlockIndex < targetEndpoint.functionBlocks.size) {
                        val fb = targetEndpoint.functionBlocks[ump.functionBlockIndex.toInt()]
                        fb.functionBlockIndex = ump.functionBlockIndex
                        fb.isActive = ump.functionBlockActive
                        fb.direction = ump.functionBlockDirection
                        fb.uiHint = ump.functionBlockUiHint
                        fb.groupIndex = ump.functionBlockFirstGroup
                        fb.groupCount = ump.functionBlockGroupCount
                        fb.ciVersionFormat = ump.functionBlockCIVersion
                        fb.maxSysEx8Streams = ump.functionBlockMaxSysEx8
                    }
                    else
                        handleError("Function Block Info Notification specified index out of range: ${ump.functionBlockIndex}")

                // service
                UmpStreamStatus.ENDPOINT_DISCOVERY -> {
                    if (ump.endpointDiscoveryUmpVersionMajor != 1 || ump.endpointDiscoveryUmpVersionMinor != 1) {
                        handleError("Unsupported UMP version in UMP endpoint info notification: ${ump.endpointInfoUmpVersionMajor}.${ump.endpointInfoUmpVersionMinor}")
                        continue
                    }
                    // return multiple results
                    val filter = ump.endpointDiscoveryFilterBitmap
                    val results = mutableListOf<Ump>()
                    // e
                    if ((filter and UmpDiscoveryFlags.ENDPOINT_INFO.toInt()) != 0)
                        results.add(UmpFactory.endpointInfoNotification(1, 1,
                            config.isStaticFunctionBlock, config.functionBlocks.size.toByte(),
                            config.streamConfiguration.supportsMidi2, config.streamConfiguration.supportsMidi1,
                            config.streamConfiguration.rxJR, config.streamConfiguration.txJR))
                    // d
                    if ((filter and UmpDiscoveryFlags.DEVICE_IDENTITY.toInt()) != 0)
                        results.add(UmpFactory.deviceIdentityNotification(
                            config.deviceIdentity.manufacturer,
                            config.deviceIdentity.family,
                            config.deviceIdentity.modelNumber,
                            config.deviceIdentity.softwareRevisionLevel))
                    // n
                    if ((filter and UmpDiscoveryFlags.ENDPOINT_NAME.toInt()) != 0)
                        results.addAll(UmpFactory.endpointNameNotification(config.name))
                    // i
                    if ((filter and UmpDiscoveryFlags.PRODUCT_INSTANCE_ID.toInt()) != 0)
                        results.addAll(UmpFactory.productInstanceIdNotification(config.productInstanceId))
                    // s
                    val stream = config.streamConfiguration
                    if ((filter and UmpDiscoveryFlags.STREAM_CONFIGURATION.toInt()) != 0)
                        results.add(UmpFactory.streamConfigNotification(
                            ((if (stream.supportsMidi2) 2 else 0) + (if (stream.supportsMidi1) 1 else 0)).toByte(),
                            stream.rxJR,
                            stream.txJR))
                    sendOutput(results.asSequence())
                }
                UmpStreamStatus.FUNCTION_BLOCK_DISCOVERY -> {
                    val index = ump.functionBlockIndex
                    if (index < config.functionBlocks.size) {
                        val results = mutableListOf<Ump>()
                        val fb = config.functionBlocks[index.toInt()]
                        val filter = ump.functionBlockDiscoveryFilter
                        if ((filter and FunctionBlockDiscoveryFlags.INFO.toInt()) != 0)
                            results.add(UmpFactory.functionBlockInfoNotification(
                                fb.isActive, index,
                                fb.uiHint, fb.midi1, fb.direction, fb.groupIndex, fb.groupCount, fb.ciVersionFormat,
                                fb.maxSysEx8Streams))
                        if ((filter and FunctionBlockDiscoveryFlags.NAME.toInt()) != 0)
                            results.addAll(UmpFactory.functionBlockNameNotification(index, fb.name))
                        sendOutput(results.asSequence())
                    }
                    else
                        handleError("Function Block Discovery specified index out of range: ${ump.functionBlockIndex}")
                }
            }
        }
    }
}
