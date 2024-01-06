package dev.atsushieno.ktmidi.ci

class JsonParserException(message: String = "JSON parser exception", innerException: Exception? = null) : Exception(message, innerException)

object Json {
    enum class TokenType { Null, False, True, Number, String, Array, Object }

    val emptySequence = sequenceOf<JsonValue>()
    val emptyMap = mapOf<JsonValue,JsonValue>()
    data class JsonToken(val type: TokenType, val offset: Int, val length: Int, val number: Double = 0.0, val seq: Sequence<JsonValue> = emptySequence, val map: Map<JsonValue,JsonValue> = emptyMap)
    class JsonValue(val source: String, val token: JsonToken) {
        constructor(value: String) : this('"' + getEscapedString(value) + '"', "")
        private constructor(s: String, source: String)
                : this(s, JsonToken(TokenType.String, 0, s.length))
        constructor(value: Double): this(value.toString(), value)
        private constructor(s: String, value: Double)
                : this(s, JsonToken(TokenType.Number, 0, s.length, number = value))
        constructor(array: List<JsonValue>)
                : this("", JsonToken(TokenType.Array, 0, 0, seq = array.asSequence()))
        constructor(map: Map<JsonValue, JsonValue>)
                : this("", JsonToken(TokenType.Object, 0, 0, map = map))

        val numberValue: Number
            get() = if (token.type == TokenType.Number) token.number else Double.NaN
        val stringValue: String
            get() = if (token.type == TokenType.String) getUnescapedString(this) else ""
        val arrayValue: Sequence<JsonValue>
            get() = if (token.type == TokenType.Array) token.seq else sequenceOf()
        val objectValue: Map<JsonValue,JsonValue>
            get() = if (token.type == TokenType.Object) token.map else mapOf()

        val allObjectKeys: List<String>
            get() = if (token.type == TokenType.Object) token.map.keys.map { it.stringValue } else listOf()
        fun getObjectValue(key: String): JsonValue? =
            if (token.type == TokenType.Object)
                token.map.firstNotNullOfOrNull { if (it.key.stringValue == key) it.value else null }
            else null
    }

    val NullValue = JsonValue("null", JsonToken(TokenType.Null, 0, 4))
    val TrueValue = JsonValue("true", JsonToken(TokenType.True, 0, 4))
    val FalseValue = JsonValue("false", JsonToken(TokenType.False, 0, 5))

    fun parse(source: String) = parse(source, 0, source.length)
    fun parseOrNull(source: String): JsonValue? = try {
        parse(source, 0, source.length)
    } catch(ex: JsonParserException) {
        null
    }

    private val splitChecked = charArrayOf(',', '{', '[', '}', ']', '"', ':')
    private fun <T>splitEntries(source: String, offset: Int, length: Int, isMap: Boolean): Sequence<T> = sequence {
        val start = skipWhitespace(source, offset)
        val end = offset + length
        val len = length - (start - offset)

        if (len == 0)
            return@sequence

        if (!isMap) {
            val range = IntRange(offset, end - 1)
            val sliced = source.slice(range)
            val pos = sliced.indexOf(',') + start
            if (pos < start) {
                if (isMap && skipWhitespace(sliced, start) == sliced.length)
                    return@sequence
                else if (!isMap) {
                    yield(parse(source, start, len) as T)
                    return@sequence
                }
            }
        }

        // there might be commas within nested split or string literal
        var lastTokenPos = start
        var p = start
        var inQuote = false
        var openBrace = 0
        var openCurly = 0
        var key: JsonValue? = null // object key

        while (p < end) {
            var t = source.indexOfAny(splitChecked, p)
            if (t < 0 || t >= end) {
                if (inQuote || openCurly > 0 || openBrace > 0)
                    throw JsonParserException("Incomplete content within ${if (isMap) "object" else "array"} (begins at $start)")
            }
            else when (source[t]) {
                '[' -> if (!inQuote) openBrace++
                ']' -> if (!inQuote) openBrace--
                '{' -> if (!inQuote) openCurly++
                '}' -> if (!inQuote) openCurly--
                '\\' -> if (inQuote) t++ // skip next character, which may be "
                '"' -> inQuote = !inQuote
                ':' -> if (isMap && openBrace == 0 && openCurly == 0 && !inQuote) {
                    key = parse(source, start, t - start)
                    lastTokenPos = t + 1
                }
                ',' -> if (openBrace == 0 && openCurly == 0 && !inQuote) {
                    val entryOrKey = parse(source, lastTokenPos, t - lastTokenPos)
                    if (isMap) {
                        if (key == null)
                            key = entryOrKey
                        else
                            yield(Pair(key, entryOrKey) as T)
                    }
                    else
                        yield(entryOrKey as T)
                    splitEntries<T>(source, t + 1, len - (t - start) - 1, isMap).forEach { yield(it) }
                    return@sequence
                }
                else -> {}
            }
            p = t + 1
        }

        val entryOrKey = parse(source, lastTokenPos, length - (lastTokenPos - offset))
        if (isMap) {
            if (key == null)
                throw JsonParserException("An entry in JSON object misses the key (begins at $offset)")
            else
                yield(Pair(key, entryOrKey) as T)
        }
        else
            yield(entryOrKey as T)
    }

    private fun findLastMatching(source: String, target: Char, offset: Int, length: Int): Int {
        val start = skipWhitespace(source, offset)
        val end = offset + length
        var p = end - 1
        var inQuote = false
        var openBrace = 0
        var openCurly = 0

        while (p >= start) {
            var t = source.lastIndexOfAny(splitChecked, p)
            if (t < 0 || t < start) {
                if (inQuote || openCurly > 0 || openBrace > 0)
                    throw JsonParserException("Incomplete content within an object or an array (begins at $start)")
            }
            else if (source[t] == target && !inQuote && openBrace == 0 && openCurly == 0)
                return t
            else when (source[t]) {
                '[' -> if (!inQuote) openBrace++
                ']' -> if (!inQuote) openBrace--
                '{' -> if (!inQuote) openCurly++
                '}' -> if (!inQuote) openCurly--
                '\\' -> if (inQuote) t++ // skip next character, which may be "
                '"' -> inQuote = !inQuote
                else -> {}
            }
            p = t - 1
        }
        return -1
    }

    private fun parse(source: String, offset: Int, length: Int) : JsonValue {
        val pos = skipWhitespace(source, offset)
        if (pos == source.length)
            throw JsonParserException("Unexpected empty content in JSON (at offset $offset)")
        return when (source[pos]) {
            '{' -> {
                val start = skipWhitespace(source, pos + 1)
                val end = findLastMatching(source, '}', start, length - (start - offset))
                checkRange(source, offset, length, start, end, "Incomplete JSON object token")
                JsonValue(source, JsonToken(TokenType.Object, pos, end - pos + 1, map = splitEntries<Pair<JsonValue,JsonValue>>(source, start, end - start, true).toMap()))
            }
            '[' -> {
                val start = skipWhitespace(source, pos + 1)
                val end = findLastMatching(source, ']', start, length - (start - offset))
                checkRange(source, offset, length, start, end, "Incomplete JSON array token")
                JsonValue(source, JsonToken(TokenType.Array, pos, end - pos + 1, seq = splitEntries(source, start, end - start, false)))
            }
            '"' -> {
                val end = findStringTerminator(source, pos + 1, length - (pos - offset - 1))
                checkRange(source, offset, length, pos, end, "Incomplete JSON string token")
                JsonValue(source, JsonToken(TokenType.String, pos, end - pos + 1))
            }
            '-', in '9' downTo  '0' -> {
                /*
                val neg = source[pos] == '-'
                val start = if (neg) pos + 1 else pos
                val end = offset + length - (start - offset)
                val value = source.slice(IntRange(start, end - 1)).toDouble()
                 */
                // FIXME: it does not strictly conform to the JSON number specification.
                val range = IntRange(pos, pos + length - (pos - offset) - 1)
                val sliced = source.slice(range)
                val value = sliced.toDouble()
                JsonValue(source, JsonToken(TokenType.Number, range.first, range.last - range.first + 1, number = value))
            }
            'n' -> {
                if (source.slice(IntRange(pos, pos + length - (pos - offset) - 1)) != "null")
                    throw JsonParserException("Unexpected token in JSON (at offset $offset)")
                JsonValue(source, JsonToken(TokenType.Null, offset, length))
            }
            't' -> {
                if (source.slice(IntRange(pos, pos + length - (pos - offset) - 1)) != "true")
                    throw JsonParserException("Unexpected token in JSON (at offset $offset)")
                JsonValue(source, JsonToken(TokenType.True, offset, length))
            }
            'f' -> {
                if (source.slice(IntRange(pos, pos + length - (pos - offset) - 1)) != "false")
                    throw JsonParserException("Unexpected token in JSON (at offset $offset)")
                JsonValue(source, JsonToken(TokenType.False, offset, length))
            }
            else -> throw JsonParserException("Unexpected character in JSON (at offset $offset)")
        }
    }

    private fun checkRange(source: String, offset: Int, length: Int, pos: Int, end: Int, incompleteError: String) {
        if (end < 0 || end > skipWhitespace(source, offset + length))
            throw JsonParserException("$incompleteError (begins at offset $pos)")
        if (skipWhitespace(source, end + 1) < offset + length)
            throw JsonParserException("Extraneous JSON token (begins at offset ${end + 1})")
    }

    private fun findStringTerminator(source: String, offset: Int, length: Int) : Int {
        var ret = offset
        val end = offset + length
        while(ret < end) {
            if (source[ret] == '\\')
                ret++
            else if (source[ret] == '"')
                return ret
            ret++
        }
        return ret
    }

    fun getUnescapedString(value: JsonValue) =
        getUnescapedString(value.source.substring(value.token.offset + 1, value.token.offset + value.token.length - 1))

    // here we do not pass index and offset as we will need substring instance anyway.
    fun getUnescapedString(source: String) =
        if (!source.contains('\\')) source
        else source.split('\\').mapIndexed { index, s ->
            if (index == 0 || s.isEmpty())
                s
            else when (s[0]) {
                '"', '\\', '/' -> s
                'b' -> '\b' + s.substring(1)
                'f' -> '\u000c' + s.substring(1)
                'n' -> '\n' + s.substring(1)
                'r' -> '\r' + s.substring(1)
                't' -> '\t' + s.substring(1)
                'u' -> s.substring(1, 5).toInt(16).toChar() + s.substring(5)
                else -> throw JsonParserException("Invalid string escape ('\\${s[0]}')")
            }
        }.joinToString("")

    fun getEscapedString(source: String) = PropertyCommonConverter.encodeStringToASCII(source).replace("\\", "\\\\")

    private fun skipWhitespace(source: String, offset: Int) : Int {
        var ret = offset
        while(ret < source.length) {
            when(source[ret]) {
                ' ', '\t', '\r', '\n' -> ret++
                else -> return ret
            }
        }
        return source.length
    }

    fun serialize(json: JsonValue): String {
        return when(json.token.type) {
            TokenType.Null -> "null"
            TokenType.True -> "true"
            TokenType.False -> "false"
            TokenType.String -> json.source.substring(json.token.offset, json.token.offset + json.token.length)
            // do not try to serialize non-fractional numbers with floating points.
            // For example, we do not need {"status": 200.0}
            TokenType.Number -> if (json.token.number.compareTo(json.token.number.toInt()) == 0) json.token.number.toInt().toString() else json.token.number.toString()
            TokenType.Array -> "[" + json.token.seq.map { serialize(it) }.joinToString(", ") + "]"
            TokenType.Object -> "{" + json.token.map.map { serialize(it.key) + ":" + serialize(it.value) }.joinToString(",") + "}"
        }
    }
}
