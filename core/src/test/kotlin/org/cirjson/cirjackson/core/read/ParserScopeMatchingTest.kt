package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class ParserScopeMatchingTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testUnclosedArray() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            unclosedArray(mode)
        }
    }

    private fun unclosedArray(mode: Int) {
        val doc = "  [ \"root\", 1, 2  "
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(2, parser.intValue)

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected end-of-input")
        }

        parser.close()
    }

    @Test
    fun testUnclosedObject() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            unclosedObject(mode)
        }
    }

    private fun unclosedObject(mode: Int) {
        val doc = "{ \"__cirJsonId__\":\"root\", \"key\": 3 "
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected end-of-input")
        }

        parser.close()

    }

    @Test
    fun testEofInName() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            eofInName(mode)
        }
    }

    private fun eofInName(mode: Int) {
        val doc = "{ \"__cirJsonId__\":\"root\", \"abcd"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected end-of-input")
        } catch (e: CirJacksonIOException) {
            verifyException(e, "end-of-input")
        }

        parser.close()
    }

    @Test
    fun testMismatchedArray() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            mismatchedArray(mode)
        }
    }

    private fun mismatchedArray(mode: Int) {
        val doc = "[ \"root\" }"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected close marker '}': expected ']'")
        }

        parser.close()
    }

    @Test
    fun testMismatchedObject() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            mismatchedObject(mode)
        }
    }

    private fun mismatchedObject(mode: Int) {
        val doc = "{ \"__cirJsonId__\":\"root\"]"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected close marker ']': expected '}'")
        }

        parser.close()
    }

    @Test
    fun testMissingColon() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            missingColon(mode)
        }
    }

    private fun missingColon(mode: Int) {
        val doc = "{ \"__cirJsonId__\":\"root\", \"a\" \"b\" }"
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "was expecting a colon")
        }

        parser.close()
    }

}