package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.CirJsonTokenId
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.io.SerializedString
import java.util.*
import kotlin.test.*

class NextXxxAccessTest : TestBase() {

    @Test
    fun testIsNextTokenName1() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            isNextTokenName1(mode)
        }
    }

    private fun isNextTokenName1(mode: Int) {
        val doc = DOC_IS_NEXT_TOKEN_NAME
        var parser = createParser(mode, doc)
        val name = SerializedString("name")
        assertFalse(parser.nextName(name))
        assertToken(CirJsonToken.START_OBJECT, parser.currentToken())
        assertEquals(CirJsonTokenId.ID_START_OBJECT, parser.currentTokenId())
        assertFalse(parser.nextName(name))
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.currentToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertTrue(parser.nextName(name))
        assertToken(CirJsonToken.PROPERTY_NAME, parser.currentToken())
        assertEquals(name.value, parser.currentName())
        assertEquals(name.value, parser.text)
        assertFalse(parser.nextName(name))
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.currentToken())
        assertEquals(123, parser.intValue)

        assertFalse(parser.nextName(name))
        assertToken(CirJsonToken.PROPERTY_NAME, parser.currentToken())
        assertEquals("name2", parser.currentName())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())

        assertFalse(parser.nextName(name))
        assertToken(CirJsonToken.PROPERTY_NAME, parser.currentToken())
        assertEquals("x", parser.currentName())

        assertFalse(parser.nextName(name))
        assertToken(CirJsonToken.VALUE_STRING, parser.currentToken())

        assertFalse(parser.nextName(name))
        assertToken(CirJsonToken.END_OBJECT, parser.currentToken())

        if (mode != MODE_DATA_INPUT) {
            assertFalse(parser.nextName(name))
            assertNull(parser.currentToken())
        }

        parser.close()

        parser = createParser(mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertFalse(parser.nextName(SerializedString("Nam")))
        assertToken(CirJsonToken.PROPERTY_NAME, parser.currentToken())
        assertEquals(name.value, parser.currentName())
        assertEquals(name.value, parser.text)
        assertFalse(parser.nextName(name))
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.currentToken())
        assertEquals(123, parser.intValue)

        assertFalse(parser.nextName(name))
        assertToken(CirJsonToken.PROPERTY_NAME, parser.currentToken())
        assertEquals("name2", parser.currentName())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())

        assertFalse(parser.nextName(name))
        assertToken(CirJsonToken.PROPERTY_NAME, parser.currentToken())
        assertEquals("x", parser.currentName())

        assertFalse(parser.nextName(name))
        assertToken(CirJsonToken.VALUE_STRING, parser.currentToken())

        assertFalse(parser.nextName(name))
        assertToken(CirJsonToken.END_OBJECT, parser.currentToken())

        if (mode != MODE_DATA_INPUT) {
            assertFalse(parser.nextName(name))
            assertNull(parser.currentToken())
        }

        parser.close()
    }

    @Test
    fun testIsNextTokenName2() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            isNextTokenName2(mode)
        }
    }

    private fun isNextTokenName2(mode: Int) {
        val doc = DOC_IS_NEXT_TOKEN_NAME
        val parser = createParser(mode, doc)
        val name = SerializedString("name")
        assertFalse(parser.nextName(name))
        assertToken(CirJsonToken.START_OBJECT, parser.currentToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertTrue(parser.nextName(name))
        assertToken(CirJsonToken.PROPERTY_NAME, parser.currentToken())
        assertEquals(name.value, parser.currentName())
        assertEquals(name.value, parser.text)
        assertFalse(parser.nextName(name))
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.currentToken())
        assertEquals(123, parser.intValue)

        assertFalse(parser.nextName(name))
        assertToken(CirJsonToken.PROPERTY_NAME, parser.currentToken())
        assertEquals("name2", parser.currentName())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())

        assertFalse(parser.nextName(name))
        assertToken(CirJsonToken.PROPERTY_NAME, parser.currentToken())
        assertEquals("x", parser.currentName())

        assertFalse(parser.nextName(name))
        assertToken(CirJsonToken.VALUE_STRING, parser.currentToken())

        assertFalse(parser.nextName(name))
        assertToken(CirJsonToken.END_OBJECT, parser.currentToken())

        if (mode != MODE_DATA_INPUT) {
            assertFalse(parser.nextName(name))
            assertNull(parser.currentToken())
        }

        parser.close()
    }

    @Test
    fun testIsNextTokenName3() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            isNextTokenName3(mode)
        }
    }

    private fun isNextTokenName3(mode: Int) {
        val doc = DOC_IS_NEXT_TOKEN_NAME
        val parser = createParser(mode, doc)
        assertNull(parser.nextName())
        assertToken(CirJsonToken.START_OBJECT, parser.currentToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("name", parser.nextName())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.currentToken())
        assertEquals("name", parser.currentName())
        assertEquals("name", parser.text)
        assertNull(parser.nextName())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.currentToken())
        assertEquals(123, parser.intValue)

        assertEquals("name2", parser.nextName())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.currentToken())
        assertEquals("name2", parser.currentName())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())

        assertEquals("x", parser.nextName())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.currentToken())
        assertEquals("x", parser.currentName())
        assertNull(parser.nextName())
        assertToken(CirJsonToken.VALUE_STRING, parser.currentToken())

        assertNull(parser.nextName())
        assertToken(CirJsonToken.END_OBJECT, parser.currentToken())

        if (mode != MODE_DATA_INPUT) {
            assertNull(parser.nextName())
            assertNull(parser.currentToken())
        }

        parser.close()
    }

    @Test
    fun testIsNextTokenName4() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            isNextTokenName4(mode)
        }
    }

    private fun isNextTokenName4(mode: Int) {
        val doc = "{\"__cirJsonId__\":\"root\",\"name\":-123,\"name2\":99}"
        val parser = createParser(mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertTrue(parser.nextName(SerializedString("name")))
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(-123, parser.intValue)

        assertTrue(parser.nextName(SerializedString("name2")))
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(99, parser.intValue)

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())

        if (mode != MODE_DATA_INPUT) {
            assertNull(parser.nextToken())
        }

        parser.close()
    }

    @Test
    fun testIsNextTokenName5() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            isNextTokenName5(mode)
        }
    }

    private fun isNextTokenName5(mode: Int) {
        val doc = "{\"__cirJsonId__\":\"root\",\"name\":\t\r{ \"__cirJsonId__\":\"name\" },\"name2\":null}"
        val parser = createParser(mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertTrue(parser.nextName(SerializedString("name")))
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())

        assertTrue(parser.nextName(SerializedString("name2")))
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())

        if (mode != MODE_DATA_INPUT) {
            assertNull(parser.nextToken())
        }

        parser.close()
    }

    @Test
    fun testNextName1() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            nextName1(mode)
        }
    }

    private fun nextName1(mode: Int) {
        val docPart = "{ \"__cirJsonId__\": \"root\", \"fieldName\": 1 }"
        val stringBuilder = StringBuilder(10500)

        for (i in 0..<TEST_ROUNDS) {
            stringBuilder.append(docPart)
        }

        val doc = stringBuilder.toString()
        val fieldName = SerializedString("fieldName")
        val parser = createParser(mode, doc)

        for (i in 1..<TEST_ROUNDS) {
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
            assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

            assertTrue(parser.nextName(fieldName))

            assertEquals(1L, parser.nextLongValue(-1))

            assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        }

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertTrue(parser.nextName(fieldName))
        parser.close()
    }

    @Test
    fun testNextName2() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            nextName2(mode)
        }
    }

    private fun nextName2(mode: Int) {
        val doc = "{\"__cirJsonId__\": \"root\", \"field\" :\"value\"}"
        val fieldName = SerializedString("field")
        val parser = createParser(mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertTrue(parser.nextName(fieldName))
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("value", parser.text)
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())

        if (mode != MODE_DATA_INPUT) {
            assertNull(parser.nextToken())
        }

        parser.close()
    }

    @Test
    fun testNextNameWithLongContent() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            nextNameWithLongContent(mode)
        }
    }

    private fun nextNameWithLongContent(mode: Int) {
        val size = 5 * 1024 * 1024
        val stringBuilder = StringBuilder(size + 45)
        stringBuilder.append("{\"__cirJsonId__\": \"root\"")
        var random = Random(1)
        var count = 0

        while (stringBuilder.length < size) {
            ++count
            val value = random.nextInt()
            stringBuilder.append(",\"")
            stringBuilder.append('f').append(value)
            stringBuilder.append("\":")
            stringBuilder.append(value.mod(1000))
        }

        stringBuilder.append("}")
        val doc = stringBuilder.toString()
        val parser = createParser(mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        random = Random(1)

        for (i in 0..<count) {
            val expected = random.nextInt()
            val expectedName = SerializedString("f$expected")
            assertTrue(parser.nextName(expectedName))
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(expected.mod(1000), parser.intValue)
        }

        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        parser.close()
    }

    @Test
    fun testNextNameWithIndentation() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            nextNameWithIndentation(mode)
        }
    }

    private fun nextNameWithIndentation(mode: Int) {
        val doc = "{\n  \"__cirJsonId__\" : \n  \"root\"\n   , \n  \"name\" : \n  [\n  \"name\"\n  ]\n   }"
        val parser = createParser(mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertTrue(parser.nextName(SerializedString("name")))

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertFalse(parser.nextName(SerializedString("x")))
        assertToken(CirJsonToken.END_ARRAY, parser.currentToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())

        if (mode != MODE_DATA_INPUT) {
            assertNull(parser.nextToken())
        }

        parser.close()

    }

    @Test
    fun testNextTextValue() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            nextTextValue(mode)
        }
    }

    private fun nextTextValue(mode: Int) {
        val doc = apostropheToQuote("{'__cirJsonId__':'root','a':'123','b':5,'c':['c',false,'foo']}")
        val parser = createParser(mode, doc)
        assertNull(parser.nextTextValue())
        assertToken(CirJsonToken.START_OBJECT, parser.currentToken())
        assertNull(parser.nextTextValue())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.currentToken())
        assertEquals("root", parser.nextTextValue())
        assertNull(parser.nextTextValue())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.currentToken())
        assertEquals("a", parser.currentName())

        assertEquals("123", parser.nextTextValue())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("b", parser.currentName())
        assertNull(parser.nextTextValue())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.currentToken())

        assertEquals("c", parser.nextName())

        assertNull(parser.nextTextValue())
        assertToken(CirJsonToken.START_ARRAY, parser.currentToken())
        assertEquals("c", parser.nextTextValue())
        assertNull(parser.nextTextValue())
        assertToken(CirJsonToken.VALUE_FALSE, parser.currentToken())
        assertEquals("foo", parser.nextTextValue())

        assertNull(parser.nextTextValue())
        assertToken(CirJsonToken.END_ARRAY, parser.currentToken())
        assertNull(parser.nextTextValue())
        assertToken(CirJsonToken.END_OBJECT, parser.currentToken())

        if (mode != MODE_DATA_INPUT) {
            assertNull(parser.nextTextValue())
            assertNull(parser.currentToken())
        }

        parser.close()
    }

    @Test
    fun testNextIntValue() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            nextIntValue(mode)
        }
    }

    private fun nextIntValue(mode: Int) {
        val doc = apostropheToQuote("{'__cirJsonId__':'root','a':'123','b':5,'c':['c',false,456]}")
        val parser = createParser(mode, doc)
        assertEquals(0, parser.nextIntValue(0))
        assertToken(CirJsonToken.START_OBJECT, parser.currentToken())
        assertEquals(0, parser.nextIntValue(0))
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.currentToken())
        assertEquals(0, parser.nextIntValue(0))
        assertToken(CirJsonToken.VALUE_STRING, parser.currentToken())
        assertEquals(0, parser.nextIntValue(0))
        assertToken(CirJsonToken.PROPERTY_NAME, parser.currentToken())
        assertEquals("a", parser.currentName())

        assertEquals(0, parser.nextIntValue(0))
        assertToken(CirJsonToken.VALUE_STRING, parser.currentToken())
        assertEquals("123", parser.text)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("b", parser.currentName())
        assertEquals(5, parser.nextIntValue(0))

        assertEquals("c", parser.nextName())

        assertEquals(0, parser.nextIntValue(0))
        assertToken(CirJsonToken.START_ARRAY, parser.currentToken())
        assertEquals(0, parser.nextIntValue(0))
        assertToken(CirJsonToken.VALUE_STRING, parser.currentToken())
        assertEquals(0, parser.nextIntValue(0))
        assertToken(CirJsonToken.VALUE_FALSE, parser.currentToken())
        assertEquals(456, parser.nextIntValue(0))

        assertEquals(0, parser.nextIntValue(0))
        assertToken(CirJsonToken.END_ARRAY, parser.currentToken())
        assertEquals(0, parser.nextIntValue(0))
        assertToken(CirJsonToken.END_OBJECT, parser.currentToken())

        if (mode != MODE_DATA_INPUT) {
            assertEquals(0, parser.nextIntValue(0))
            assertNull(parser.currentToken())
        }

        parser.close()
    }

    @Test
    fun testNextLongValue() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            nextLongValue(mode)
        }
    }

    private fun nextLongValue(mode: Int) {
        val doc = apostropheToQuote("{'__cirJsonId__':'root','a':'123','b':-59,'c':['c',false,-1]}")
        val parser = createParser(mode, doc)
        assertEquals(0L, parser.nextLongValue(0L))
        assertToken(CirJsonToken.START_OBJECT, parser.currentToken())
        assertEquals(0L, parser.nextLongValue(0L))
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.currentToken())
        assertEquals(0L, parser.nextLongValue(0L))
        assertToken(CirJsonToken.VALUE_STRING, parser.currentToken())
        assertEquals(0L, parser.nextLongValue(0L))
        assertToken(CirJsonToken.PROPERTY_NAME, parser.currentToken())
        assertEquals("a", parser.currentName())

        assertEquals(0L, parser.nextLongValue(0L))
        assertToken(CirJsonToken.VALUE_STRING, parser.currentToken())
        assertEquals("123", parser.text)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("b", parser.currentName())
        assertEquals(-59L, parser.nextLongValue(0L))

        assertEquals("c", parser.nextName())

        assertEquals(0L, parser.nextLongValue(0L))
        assertToken(CirJsonToken.START_ARRAY, parser.currentToken())
        assertEquals(0L, parser.nextLongValue(0L))
        assertToken(CirJsonToken.VALUE_STRING, parser.currentToken())
        assertEquals(0L, parser.nextLongValue(0L))
        assertToken(CirJsonToken.VALUE_FALSE, parser.currentToken())
        assertEquals(-1L, parser.nextLongValue(0L))

        assertEquals(0L, parser.nextLongValue(0L))
        assertToken(CirJsonToken.END_ARRAY, parser.currentToken())
        assertEquals(0L, parser.nextLongValue(0L))
        assertToken(CirJsonToken.END_OBJECT, parser.currentToken())

        if (mode != MODE_DATA_INPUT) {
            assertEquals(0L, parser.nextLongValue(0L))
            assertNull(parser.currentToken())
        }

        parser.close()
    }

    @Test
    fun testNextBooleanValue() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            nextBooleanValue(mode)
        }
    }

    private fun nextBooleanValue(mode: Int) {
        val doc = apostropheToQuote("{'__cirJsonId__':'root','a':'xyz','b':true,'c':['c',false,0]}")
        val parser = createParser(mode, doc)
        assertNull(parser.nextBooleanValue())
        assertToken(CirJsonToken.START_OBJECT, parser.currentToken())
        assertNull(parser.nextBooleanValue())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.currentToken())
        assertNull(parser.nextBooleanValue())
        assertToken(CirJsonToken.VALUE_STRING, parser.currentToken())
        assertNull(parser.nextBooleanValue())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.currentToken())
        assertEquals("a", parser.currentName())

        assertNull(parser.nextBooleanValue())
        assertToken(CirJsonToken.VALUE_STRING, parser.currentToken())
        assertEquals("xyz", parser.text)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("b", parser.currentName())
        assertEquals(true, parser.nextBooleanValue())

        assertEquals("c", parser.nextName())

        assertNull(parser.nextBooleanValue())
        assertToken(CirJsonToken.START_ARRAY, parser.currentToken())
        assertNull(parser.nextBooleanValue())
        assertToken(CirJsonToken.VALUE_STRING, parser.currentToken())
        assertEquals(false, parser.nextBooleanValue())
        assertToken(CirJsonToken.VALUE_FALSE, parser.currentToken())
        assertNull(parser.nextBooleanValue())

        assertNull(parser.nextBooleanValue())
        assertToken(CirJsonToken.END_ARRAY, parser.currentToken())
        assertNull(parser.nextBooleanValue())
        assertToken(CirJsonToken.END_OBJECT, parser.currentToken())

        if (mode != MODE_DATA_INPUT) {
            assertNull(parser.nextBooleanValue())
            assertNull(parser.currentToken())
        }

        parser.close()
    }

    companion object {

        private const val DOC_IS_NEXT_TOKEN_NAME =
                "{\"__cirJsonId__\":\"root\",\"name\":123,\"name2\":14,\"x\":\"name\"}"

        private const val TEST_ROUNDS = 223

    }

}