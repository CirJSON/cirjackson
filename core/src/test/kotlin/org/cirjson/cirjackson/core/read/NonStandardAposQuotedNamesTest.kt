package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class NonStandardAposQuotedNamesTest : TestBase() {

    private val sharedFactory = sharedStreamFactory()

    private val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_SINGLE_QUOTES).build()

    @Test
    fun testApostropheQuotingDisabled() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            apostropheQuotingDisabled(mode)
        }
    }

    private fun apostropheQuotingDisabled(mode: Int) {
        var doc = "[\"root\", 'text' ]"
        var parser = createParser(sharedFactory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('''")
        }

        parser.close()

        doc = "{ \"__cirJsonId__\":\"root\", 'a':1 }"
        parser = createParser(sharedFactory, mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('''")
        }

        parser.close()
    }

    @Test
    fun testApostropheQuotingEnabled() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            apostropheQuotingEnabled(mode)
        }
    }

    private fun apostropheQuotingEnabled(mode: Int) {
        var doc =
                "{ \"__cirJsonId__\":\"root\", 'a' : 1, \"foobar\": 'b', '_abcde1234':'d', '\"' : '\"\"', '':'', '${UNICODE_NAME}':'${UNICODE_VALUE}' }"
        var parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("a", parser.currentName)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals("1", parser.text)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("foobar", parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("b", parser.text)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("_abcde1234", parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("d", parser.text)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("\"", parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("\"\"", parser.text)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("", parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("", parser.text)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals(UNICODE_NAME, parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(UNICODE_VALUE, parser.text)
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        parser.close()

        doc =
                "{ \"__cirJsonId__\":\"root\", 'b':1,'array':[\"array\",{\"__cirJsonId__\":\"array/0\",'b':3}],'ob':{\"__cirJsonId__\":\"ob\",'b':4,'x':0,'y':'$UNICODE_SEGMENT','a':false } }"
        parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("b", parser.currentName)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("b", parser.currentName)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(3, parser.intValue)
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())

        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("b", parser.currentName)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(4, parser.intValue)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("x", parser.currentName)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(0, parser.intValue)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("y", parser.currentName)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(UNICODE_SEGMENT, parser.text)
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("a", parser.currentName)
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        parser.close()
    }

    @Test
    fun testSingleQuotesEscaped() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            singleQuotesEscaped(mode)
        }
    }

    private fun singleQuotesEscaped(mode: Int) {
        val doc = "[ '16\\'' ]"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("16'", parser.text)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    @Test
    fun testSingleQuotedKeys() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            singleQuotedKeys(mode, "{ \"__cirJsonId__\":\"root\", '\"\"': 'value'}", "\"\"")
            singleQuotedKeys(mode, "{ \"__cirJsonId__\":\"root\", '\"key\"': 'value'}", "\"key\"")
        }
    }

    private fun singleQuotedKeys(mode: Int, doc: String, expected: String) {
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(expected, parser.nextName())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("value", parser.text)
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        parser.close()
    }

    @Test
    fun testSingleQuotedValues() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            singleQuotedValues(mode, "{ \"__cirJsonId__\":\"root\", \"bar\": '\"\"'}", "\"\"")
            singleQuotedValues(mode, "{ \"__cirJsonId__\":\"root\", \"bar\": '\"stuff\"'}", "\"stuff\"")
        }
    }

    private fun singleQuotedValues(mode: Int, doc: String, expected: String) {
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("bar", parser.nextName())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals(expected, parser.text)
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
        parser.close()
    }

    companion object {

        private const val UNICODE_2_BYTES = 167.toChar()

        private const val UNICODE_3_BYTES = 0x4567.toChar()

        private const val UNICODE_NAME = "Uni$UNICODE_2_BYTES-key-$UNICODE_3_BYTES"

        private const val UNICODE_VALUE = "Uni$UNICODE_3_BYTES-value-$UNICODE_2_BYTES"

        private const val UNICODE_SEGMENT = "[${AsyncTestBase.UNICODE_2_BYTES}/${AsyncTestBase.UNICODE_3_BYTES}]"

    }

}