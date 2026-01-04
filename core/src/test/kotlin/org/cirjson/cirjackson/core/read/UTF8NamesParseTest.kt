package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.CirJsonTokenId
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.io.SerializedString
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UTF8NamesParseTest : TestBase() {

    @Test
    fun testEmptyName() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            emptyName(mode)
        }
    }

    private fun emptyName(mode: Int) {
        val doc = "{ \"__cirJsonId__\" : \"root\", \"\" : \"\" }"
        val parser = createParser(mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("", parser.currentName())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("", parser.text)
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testUtf8Name2Bytes() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (name in UTF8_2_BYTE_STRINGS) {
                utf8Name2Bytes(mode, name)
            }
        }
    }

    private fun utf8Name2Bytes(mode: Int, name: String) {
        val doc = "{ \"__cirJsonId__\" : \"root\", \"$name\" : 0 }"
        val parser = createParser(mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertTrue(parser.hasToken(CirJsonToken.PROPERTY_NAME))
        assertTrue(parser.hasTokenId(CirJsonTokenId.ID_PROPERTY_NAME))
        assertEquals(name, parser.currentName())

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertTrue(parser.hasToken(CirJsonToken.VALUE_NUMBER_INT))
        assertTrue(parser.hasTokenId(CirJsonTokenId.ID_NUMBER_INT))
        assertEquals(name, parser.currentName())

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testUtf8Name3Bytes() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (name in UTF8_3_BYTE_STRINGS) {
                utf8Name3Bytes(mode, name)
            }
        }
    }

    private fun utf8Name3Bytes(mode: Int, name: String) {
        val doc = "{ \"__cirJsonId__\" : \"root\", \"$name\" : true }"
        val parser = createParser(mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertTrue(parser.hasToken(CirJsonToken.PROPERTY_NAME))
        assertTrue(parser.hasTokenId(CirJsonTokenId.ID_PROPERTY_NAME))
        assertEquals(name, parser.currentName())

        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertTrue(parser.hasToken(CirJsonToken.VALUE_TRUE))
        assertTrue(parser.hasTokenId(CirJsonTokenId.ID_TRUE))
        assertEquals(name, parser.currentName())

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testUtf8StringTrivial() {
        val values = UTF8_2_BYTE_STRINGS + UTF8_3_BYTE_STRINGS

        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (value in values) {
                utf8StringTrivial(mode, value)
            }
        }
    }

    private fun utf8StringTrivial(mode: Int, value: String) {
        val doc = "[ \"$value\" ]"
        val parser = createParser(mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(value, getAndVerifyText(parser))
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testUtf8StringValue() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            utf8StringValue(mode, 2900)
            utf8StringValue(mode, 5300)
        }
    }

    private fun utf8StringValue(mode: Int, length: Int) {
        val value = createUtf8StringValue(length)

        var doc = "[ \"$value\" ]"
        var parser = createParser(mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        var actual = getAndVerifyText(parser)
        assertEquals(value.length, actual.length)
        assertEquals(value, actual)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()

        doc = "{ \"__cirJsonId__\" : \"root\", \"$value\" : 42 }"
        parser = createParser(mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        actual = getAndVerifyText(parser)
        assertEquals(value.length, actual.length)
        assertEquals(value, actual, "mode: $mode, length: $length")
        assertEquals(value, parser.currentName())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(42, parser.intValue)
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    private fun createUtf8StringValue(length: Int): String {
        val random = Random(13L)
        val stringBuilder = StringBuilder(length + 20)

        while (stringBuilder.length < length) {
            val c = if (random.nextBoolean()) {
                (32 + (random.nextInt() and 0x3F)).toChar().takeIf { it != '"' && it != '\\' } ?: ' '
            } else if (random.nextBoolean()) {
                (160 + (random.nextInt() and 0x3FF)).toChar()
            } else if (random.nextBoolean()) {
                (8000 + (random.nextInt() and 0x7FFF)).toChar()
            } else {
                val value = random.nextInt() and 0x3FFFF
                stringBuilder.append((0xD800 + (value shr 10)).toChar())
                (0xDC00 + (value and 0x3FF)).toChar()
            }
            stringBuilder.append(c)
        }

        return stringBuilder.toString()
    }

    @Test
    fun testNextFieldName() {
        val stringBuilder = StringBuilder(3600)
        stringBuilder.append("{ \"__cirJsonId__\" : \"root\",")

        for (i in 1..3995) {
            stringBuilder.append(' ')
        }

        stringBuilder.append("\"id\":2}")
        val doc = stringBuilder.toString()

        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            nextFieldName(mode, doc)
        }
    }

    private fun nextFieldName(mode: Int, doc: String) {
        val parser = createParser(mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        val id = SerializedString("id")
        assertTrue(parser.nextName(id))
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    companion object {

        private val UTF8_2_BYTE_STRINGS =
                arrayOf("b", "\u00d8", "A\u00D8", "ab\u00D8d", "abc\u00d8", "c3p0", "1234\u00C75", "......",
                        "Long\u00FAer", "Latin1-fully-\u00BE-develop\u00A8d",
                        "Some very long name, ridiculously long actually to see that buffer expansion works: \u00BF?")

        private val UTF8_3_BYTE_STRINGS =
                arrayOf("\uC823?", "A\u400F", "1\u1234?", "ab\u1234d", "Ab123\u4034", "Long \uC023 ish",
                        "Bit longer:\uC023", "Even-longer:\u3456", "Yet bit longer \uC023", "Even more \u3456 longer",
                        "\uC023 Possibly ridiculous", "But \uC023 this takes the cake")

    }

}