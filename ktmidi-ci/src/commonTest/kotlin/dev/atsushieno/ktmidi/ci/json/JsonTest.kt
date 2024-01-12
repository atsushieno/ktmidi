package dev.atsushieno.ktmidi.ci.json

import kotlin.test.Test
import kotlin.test.assertEquals

class JsonTest {
    @Test
    fun parseString() {
        val str1 = Json.parse("\"TEST1\"")
        assertEquals(Json.TokenType.String, str1.token.type, "str1.token.type")
        assertEquals(0, str1.token.offset, "str1.token.offset")
        assertEquals(7, str1.token.length, "str1.token.length")
        assertEquals("TEST1", Json.getUnescapedString(str1))

        val str2 = Json.parse("\"TEST2\\\\r\\n\\t\\/\\b\\f\\u1234\\uFEDC\"")
        assertEquals(Json.TokenType.String, str2.token.type, "str2.token.type")
        assertEquals(0, str2.token.offset, "str2.token.offset")
        assertEquals(32, str2.token.length, "str2.token.length")
        assertEquals("TEST2\r\n\t/\b\u000c\u1234\uFEDC", Json.getUnescapedString(str2))
    }
    @Test
    fun parseNull() {
        val nullVal = Json.parse("null")
        assertEquals(Json.TokenType.Null, nullVal.token.type, "nullVal.token.type")
        assertEquals(0, nullVal.token.offset, "nullVal.token.offset")
        assertEquals(4, nullVal.token.length, "nullVal.token.length")
    }
    @Test
    fun parseBoolean() {
        val trueVal = Json.parse("true")
        assertEquals(Json.TokenType.True, trueVal.token.type, "trueVal.token.type")
        assertEquals(0, trueVal.token.offset, "trueVal.token.offset")
        assertEquals(4, trueVal.token.length, "trueVal.token.length")

        val falseVal = Json.parse("false")
        assertEquals(Json.TokenType.False, falseVal.token.type, "falseVal.token.type")
        assertEquals(0, falseVal.token.offset, "falseVal.token.offset")
        assertEquals(5, falseVal.token.length, "falseVal.token.length")
    }
    @Test
    fun parseNumber() {
        val num1 = Json.parse("0")
        assertEquals(Json.TokenType.Number, num1.token.type, "num1.token.type")
        assertEquals(0, num1.token.offset, "num1.token.offset")
        assertEquals(1, num1.token.length, "num1.token.length")
        assertEquals(0.0, num1.token.number, "num1.token.number")

        val num2 = Json.parse("10")
        assertEquals(10.0, num2.token.number, "num2.token.number")
        val num3 = Json.parse("10.0")
        assertEquals(10.0, num3.token.number, "num3.token.number")
        val num4 = Json.parse("-1")
        assertEquals(-1.0, num4.token.number, "num4.token.number")
        val num5 = Json.parse("-0")
        assertEquals(-0.0, num5.token.number, "num5.token.number")
        val num6 = Json.parse("0.1")
        assertEquals(0.1, num6.token.number, "num6.token.number")
        val num7 = Json.parse("-0.1")
        assertEquals(-0.1, num7.token.number, "num7.token.number")
        val num8 = Json.parse("-0.1e12")
        assertEquals(-0.1e12, num8.token.number, "num8.token.number")
        val num9 = Json.parse("-0.1e-12")
        assertEquals(-0.1e-12, num9.token.number, "num9.token.number")
        val num10 = Json.parse("-0e-12")
        assertEquals(-0e-12, num10.token.number, "num10.token.number")
        val num11 = Json.parse("1e+1")
        assertEquals(1e+1, num11.token.number, "num11.token.number")
    }

    @Test
    fun parseObject() {
        val obj1 = Json.parse("{}")
        assertEquals(Json.TokenType.Object, obj1.token.type, "obj1.token.type")
        assertEquals(0, obj1.token.offset, "obj1.token.offset")
        assertEquals(2, obj1.token.length, "obj1.token.length")
        assertEquals(0, obj1.token.map.size, "obj1.token.map.size")

        val obj2 = Json.parse("{ }")
        assertEquals(Json.TokenType.Object, obj2.token.type, "obj2.token.type")
        assertEquals(0, obj2.token.offset, "obj2.token.offset")
        assertEquals(3, obj2.token.length, "obj2.token.length")
        assertEquals(0, obj2.token.map.size, "obj2.token.map.size")
    }

    @Test
    fun parseObject2() {
        val obj2 = Json.parse("{\"x,y\": 5, \"a,\\b\": 7}")
        assertEquals(Json.TokenType.Object, obj2.token.type, "obj2.token.type")
        assertEquals(0, obj2.token.offset, "obj2.token.offset")
        assertEquals(21, obj2.token.length, "obj2.token.length")
        val obj2Map = obj2.token.map.toList()
        assertEquals(2, obj2Map.size, "arr2Items.size")
        assertEquals(obj2.source, obj2Map[0].second.source, "arr2.source")
        assertEquals(Json.TokenType.Number, obj2Map[0].second.token.type, "obj2Map[0].token.type")
        val obj2At0First = obj2Map[0].first
        assertEquals("x,y", Json.getUnescapedString(obj2At0First), "obj2Map[0] as string")
        assertEquals(5.0, obj2Map[0].second.token.number, "obj2Map[0].token.number")
        val obj2At1First = obj2Map[1].first
        val s = Json.getUnescapedString(obj2At1First)
        assertEquals("a,\b", s, "obj2Map[1] as string")
        assertEquals(7.0, obj2Map[1].second.token.number, "obj2Map[1].token.number")
    }

    @Test
    fun parseObject3() {
        val obj3 = Json.parse("{\"key1\": null, \"key2\": {\"key2-1\": true}, \"key3\": {\"key3-1\": {}, \"key3-2\": []} }")
        assertEquals(3, obj3.token.map.size, "obj3.token.map.size")
    }

    @Test
    fun parseArray() {
        val arr1 = Json.parse("[]")
        assertEquals(Json.TokenType.Array, arr1.token.type, "arr1.token.type")
        assertEquals(0, arr1.token.offset, "arr1.token.offset")
        assertEquals(2, arr1.token.length, "arr1.token.length")
        assertEquals(0, arr1.token.map.size, "arr1.token.map.size")

        val arr2 = Json.parse("[ ]")
        assertEquals(Json.TokenType.Array, arr2.token.type, "arr2.token.type")
        assertEquals(0, arr2.token.offset, "arr2.token.offset")
        assertEquals(3, arr2.token.length, "arr2.token.length")
        assertEquals(0, arr2.token.map.size, "arr2.token.map.size")
    }

    @Test
    fun parseArray2() {
        val arr1 = Json.parse("[1,2,3,4,5]")
        assertEquals(Json.TokenType.Array, arr1.token.type, "arr1.token.type")
        assertEquals(0, arr1.token.offset, "arr1.token.offset")
        assertEquals(11, arr1.token.length, "arr1.token.length")
        val arr1Items = arr1.token.seq.toList()
        assertEquals(5, arr1Items.size, "arr1Items.size")
        assertEquals(arr1.source, arr1Items[0].source, "arr1.source")
        assertEquals(Json.TokenType.Number, arr1Items[0].token.type, "arr1Items[0].token.type")
        assertEquals(1.0, arr1Items[0].token.number, "arr1Items[0].token.number")
        assertEquals(5.0, arr1Items[4].token.number, "arr1Items[4].token.number")
    }

    @Test
    fun parseArray3() {
        val arr2 = Json.parse("[\"1\",2,[3,4],{\"x,y\": 5, \"a,\\b\": 7}, {\"\": {}}, \"{}[]\"]")
        assertEquals(Json.TokenType.Array, arr2.token.type, "arr2.token.type")
        assertEquals(0, arr2.token.offset, "arr2.token.offset")
        assertEquals(53, arr2.token.length, "arr2.token.length")
        val arr2Items = arr2.token.seq.toList()
        assertEquals(6, arr2Items.size, "arr2Items.size")
        assertEquals(arr2.source, arr2Items[0].source, "arr2.source")
        assertEquals(Json.TokenType.String, arr2Items[0].token.type, "arr2Items[0].token.type")
        assertEquals("1", Json.getUnescapedString(arr2Items[0]), "arr2Items[0] as string")
        assertEquals(Json.TokenType.Number, arr2Items[1].token.type, "arr2Items[1].token.type")
        assertEquals(Json.TokenType.Array, arr2Items[2].token.type, "arr2Items[2].token.type")
        assertEquals(Json.TokenType.Object, arr2Items[3].token.type, "arr2Items[3].token.type")
        assertEquals(Json.TokenType.Object, arr2Items[4].token.type, "arr2Items[4].token.type")
        assertEquals(Json.TokenType.String, arr2Items[5].token.type, "arr2Items[5].token.type")
        assertEquals("{}[]", Json.getUnescapedString(arr2Items[5]), "arr2Items[5] value")
    }

    @Test
    fun parseArray4() {
        val arr3 = Json.parse("[{\"resource\":\"DeviceInfo\"},{\"resource\":\"foo\"},{\"resource\":\"bar\"}]")
        assertEquals(Json.TokenType.Array, arr3.token.type, "arr3.token.type")
        val arr3Items = arr3.token.seq.toList()
        assertEquals(3, arr3Items.size, "arr3Items.size")
        arr3Items.forEachIndexed { index, it ->
            assertEquals(Json.TokenType.Object, it.token.type, "arr3Items[$index].type")
            assertEquals(1, it.token.map.size, "arr3Items[$index].map.size")
        }
    }
}