package dev.atsushieno.ktmidi.ci

class CommonRulesPropertyClient(private val logger: Logger, private val muid: Int, private val sendGetPropertyData: (msg: Message.GetPropertyData) -> Unit) :
    MidiCIPropertyClient {
    override fun createRequestHeader(resourceIdentifier: String, isPartialSet: Boolean): List<Byte> =
        CommonRulesPropertyHelper.createRequestHeaderBytes(resourceIdentifier, isPartialSet)

    override fun createSubscribeHeader(resourceIdentifier: String): List<Byte> =
        CommonRulesPropertyHelper.createSubscribeHeaderBytes(resourceIdentifier)

    override fun getPropertyIdForHeader(header: List<Byte>) = CommonRulesPropertyHelper.getPropertyIdentifier(header)

    override fun getMetadataList(): List<PropertyMetadata> = resourceList

    override suspend fun requestPropertyList(destinationMUID: Int, requestId: Byte) =
        requestResourceList(destinationMUID, requestId)

    override fun onGetPropertyDataReply(request: Message.GetPropertyData, reply: Message.GetPropertyDataReply) {
        // If the reply message is about ResourceList, then store the list internally.
        val list = getMetadataListForMessage(request, reply) ?: return
        resourceList.clear()
        resourceList.addAll(list)
        propertyCatalogUpdated.forEach { it() }
    }

    override fun getReplyStatusFor(header: List<Byte>): Int? = CommonRulesPropertyHelper.getReplyStatusFor(header)

    override fun getMediaTypeFor(replyHeader: List<Byte>): String =
        CommonRulesPropertyHelper.getMediaTypeFor(replyHeader)

    override fun getIsPartialFor(header: List<Byte>): Boolean =
        CommonRulesPropertyHelper.getReplyHeaderField(header, PropertyCommonHeaderKeys.SET_PARTIAL)?.token?.type == Json.TokenType.True

    override fun getCommandFieldFor(header: List<Byte>): String? =
        CommonRulesPropertyHelper.getReplyHeaderField(header, PropertyCommonHeaderKeys.COMMAND)?.stringValue

    override fun getSubscribedProperty(msg: Message.SubscribeProperty): String? {
        val subscribeId = CommonRulesPropertyHelper.getReplyHeaderField(msg.header, PropertyCommonHeaderKeys.SUBSCRIBE_ID)?.stringValue
        if (subscribeId == null) {
            logger.logError("Subscribe Id is not found in the property header")
            return null
        }
        val subscription = subscriptions.firstOrNull { it.subscribeId == subscribeId }
        if (subscription == null) {
            logger.logError("Property is not mapped to subscribeId $subscribeId")
            return null
        }
        return subscription.resource
    }

    override fun processPropertySubscriptionResult(propertyId: String, reply: Message.SubscribePropertyReply) {
        val status = getReplyStatusFor(reply.header)
        if (status != PropertyExchangeStatus.OK)
            return
        val subscribeId = CommonRulesPropertyHelper.getReplyHeaderField(reply.header, PropertyCommonHeaderKeys.SUBSCRIBE_ID) ?: return
        // does this MUID matter...?
        subscriptions.add(SubscriptionEntry(propertyId, reply.destinationMUID, subscribeId.stringValue))
    }

    override fun createStatusHeader(status: Int): List<Byte> =
        Json.JsonValue(mapOf(Pair(
            Json.JsonValue(PropertyCommonHeaderKeys.STATUS),
            Json.JsonValue(status.toDouble())
        )))
            .getSerializedBytes()

    override fun getUpdatedValue(existing: PropertyValue?, isPartial: Boolean, body: List<Byte>): Pair<Boolean, List<Byte>> {
        if (!isPartial)
            return Pair(true, body)

        if (existing == null)
            return Pair(false, listOf()) // it is partial update but there is no existing value

        val failureReturn = Pair(false, existing.body)

        // FIXME: apply conversion depending on mediaType
        val existingBytes = existing.body
        val existingJson = Json.parseOrNull(existingBytes.toByteArray().decodeToString())
            ?: return failureReturn // existing body is not a valid JSON string

        // FIXME: apply conversion depending on mediaType
        val bytes = body
        val jsonString = bytes.toByteArray().decodeToString()
        val bodyJson = Json.parseOrNull(MidiCIConverter.decodeASCIIToString(jsonString))
            ?: return failureReturn // reply body is not a valid JSON string

        val result = PropertyPartialUpdater.applyPartialUpdates(existingJson, bodyJson)
        return if (result.first)
            Pair(true, result.second.getSerializedBytes())
        else
            Pair(false, existing.body)
    }

    override val propertyCatalogUpdated = mutableListOf<() -> Unit>()

    // implementation

    private val resourceList = mutableListOf<PropertyMetadata>()
    val subscriptions = mutableListOf<SubscriptionEntry>()

    private fun requestResourceList(destinationMUID: Int, requestId: Byte) {
        val requestASCIIBytes = CommonRulesPropertyHelper.getResourceListRequestBytes()
        val msg = Message.GetPropertyData(muid, destinationMUID, requestId, requestASCIIBytes)
        sendGetPropertyData(msg)
    }

    private fun getMetadataListForMessage(request: Message.GetPropertyData, reply: Message.GetPropertyDataReply): List<PropertyMetadata>? {
        val id = getPropertyIdForHeader(request.header)
        return if (id == PropertyResourceNames.RESOURCE_LIST) getMetadataListForBody(reply.body) else null
    }

    private fun getMetadataListForBody(body: List<Byte>): List<PropertyMetadata> {
        try {
            val json = Json.parse(MidiCIConverter.decodeASCIIToString(body.toByteArray().decodeToString()))
            return getMetadataListForBody(json)
        } catch(ex: JsonParserException) {
            logger.logError(ex.message!!)
            return listOf()
        }
    }

    private fun getMetadataListForBody(body: Json.JsonValue): List<PropertyMetadata> {
        // list of entries (name: value such as {"resource": "foo", "canSet": "partial"})
        val list = body.token.seq.toList()
        return list.map { entry ->
            val res = PropertyMetadata()
            entry.token.map.forEach {
                val v = it.value
                when (it.key.stringValue) {
                    PropertyResourceFields.RESOURCE -> res.resource = v.stringValue
                    PropertyResourceFields.CAN_GET -> res.canGet = v.token.type == Json.TokenType.True
                    PropertyResourceFields.CAN_SET -> res.canSet = v.stringValue
                    PropertyResourceFields.CAN_SUBSCRIBE -> res.canSubscribe = v.token.type == Json.TokenType.True
                    PropertyResourceFields.REQUIRE_RES_ID -> res.requireResId = v.token.type == Json.TokenType.True
                    PropertyResourceFields.ENCODINGS -> res.encodings = v.token.seq.map { e -> e.stringValue }.toList()
                    PropertyResourceFields.MEDIA_TYPE -> res.mediaTypes = v.token.seq.map { e -> e.stringValue }.toList()
                    PropertyResourceFields.SCHEMA -> res.schema = v
                    PropertyResourceFields.CAN_PAGINATE -> res.canPaginate = v.token.type == Json.TokenType.True
                    PropertyResourceFields.COLUMNS -> res.columns = v.token.seq.map { c ->
                        val col = PropertyResourceColumn()
                        c.token.map.forEach { prc ->
                            val cv = prc.value
                            when (prc.key.stringValue) {
                                PropertyResourceColumnFields.PROPERTY -> col.property = cv.stringValue
                                PropertyResourceColumnFields.LINK -> col.link = cv.stringValue
                                PropertyResourceColumnFields.TITLE -> col.title = cv.stringValue
                            }
                        }
                        col
                    }.toList()
                }
            }
            res
        }.toList()
    }
}