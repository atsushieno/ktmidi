package dev.atsushieno.ktmidi.ci

class CommonRulesPropertyClient(private val muid: Int, private val sendGetPropertyData: (msg: Message.GetPropertyData) -> Unit) :
    MidiCIPropertyClient {
    override fun createRequestHeader(resourceIdentifier: String, isPartialSet: Boolean): List<Byte> =
        CommonRulesPropertyHelper.createRequestHeaderBytes(resourceIdentifier, isPartialSet)

    override fun getPropertyIdForHeader(header: List<Byte>) = CommonRulesPropertyHelper.getPropertyIdentifier(header)

    override fun getPropertyList(): List<PropertyResource> = resourceList

    override suspend fun requestPropertyList(destinationMUID: Int, requestId: Byte) =
        requestResourceList(destinationMUID, requestId)

    override fun onGetPropertyDataReply(request: Message.GetPropertyData, reply: Message.GetPropertyDataReply) {
        // If the reply message is about ResourceList, then store the list internally.
        val list = getPropertyListForMessage(request, reply) ?: return
        resourceList.clear()
        resourceList.addAll(list)
        propertyCatalogUpdated.forEach { it() }
    }

    override fun getReplyStatusFor(header: List<Byte>): Int? = CommonRulesPropertyHelper.getReplyStatusFor(header)

    override fun getMediaTypeFor(replyHeader: List<Byte>): String =
        CommonRulesPropertyHelper.getMediaTypeFor(replyHeader)

    override val propertyCatalogUpdated = mutableListOf<() -> Unit>()

    // implementation

    private val resourceList = mutableListOf<PropertyResource>()

    private fun requestResourceList(destinationMUID: Int, requestId: Byte) {
        val requestASCIIBytes = CommonRulesPropertyHelper.getResourceListRequestBytes()
        val msg = Message.GetPropertyData(muid, destinationMUID, requestId, requestASCIIBytes)
        sendGetPropertyData(msg)
    }

    private fun getPropertyListForMessage(request: Message.GetPropertyData, reply: Message.GetPropertyDataReply): List<PropertyResource>? {
        val id = getPropertyIdForHeader(request.header)
        return if (id == PropertyResourceNames.RESOURCE_LIST) getResourceListForBody(reply.body) else null
    }

    private fun getResourceListForBody(body: List<Byte>): List<PropertyResource> {
        val json = Json.parse(PropertyCommonConverter.decodeASCIIToString(body.toByteArray().decodeToString()))
        return getResourceListForBody(json)
    }

    private fun getResourceListForBody(body: Json.JsonValue): List<PropertyResource> {
        // list of entries (name: value such as {"resource": "foo", "canSet": "partial"})
        val list = body.token.seq.toList()
        return list.map { entry ->
            val res = PropertyResource()
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
                        c.token.map.forEach {
                            val cv = it.value
                            when (it.key.stringValue) {
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