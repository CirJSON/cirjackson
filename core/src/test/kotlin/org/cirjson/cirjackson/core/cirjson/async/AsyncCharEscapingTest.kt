package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class AsyncCharEscapingTest : AsyncTestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testMissingLinefeedEscaping() {
        val doc = utf8Bytes(apostropheToQuote("['0','Linefeed: \n.']"))

        for (mode in ALL_ASYNC_MODES) {
            missingLinefeedEscaping(mode, 0, doc, 99)
            missingLinefeedEscaping(mode, 0, doc, 5)
            missingLinefeedEscaping(mode, 0, doc, 3)
            missingLinefeedEscaping(mode, 0, doc, 2)
            missingLinefeedEscaping(mode, 0, doc, 1)

            missingLinefeedEscaping(mode, 1, doc, 99)
            missingLinefeedEscaping(mode, 1, doc, 3)
            missingLinefeedEscaping(mode, 1, doc, 1)
        }
    }

    private fun missingLinefeedEscaping(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            fail("Expected an exception for un-escaped linefeed in string value")
        } catch (e: StreamReadException) {
            verifyException(e, "has to be escaped")
        }

        parser.close()
    }

    @Test
    fun testSimpleEscaping() {
        for (mode in ALL_ASYNC_MODES) {
            simpleEscaping(mode, 0, 99)
            simpleEscaping(mode, 0, 5)
            simpleEscaping(mode, 0, 3)
            simpleEscaping(mode, 0, 2)
            simpleEscaping(mode, 0, 1)

            simpleEscaping(mode, 1, 99)
            simpleEscaping(mode, 1, 3)
            simpleEscaping(mode, 1, 1)
        }
    }

    private fun simpleEscaping(mode: Int, padding: Int, bytesPerFeed: Int) {
        var doc = utf8Bytes(apostropheToQuote("['0','LF=\\n']"))
        var parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("LF=\n", parser.currentText())
        parser.close()

        doc = utf8Bytes(apostropheToQuote("['0','NULL:\\u0000!']"))
        parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("NULL:\u0000!", parser.currentText())
        parser.close()

        doc = utf8Bytes(apostropheToQuote("['0','\\u0123']"))
        parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("\u0123", parser.currentText())
        parser.close()

        doc = utf8Bytes(apostropheToQuote("['0','\\u0041\\u0043']"))
        parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("AC", parser.currentText())
        parser.close()
    }

    @Test
    fun testSimpleNameEscaping() {
        for (mode in ALL_ASYNC_MODES) {
            simpleNameEscaping(mode, 0, 99)
            simpleNameEscaping(mode, 0, 5)
            simpleNameEscaping(mode, 0, 3)
            simpleNameEscaping(mode, 0, 2)
            simpleNameEscaping(mode, 0, 1)

            simpleNameEscaping(mode, 1, 99)
            simpleNameEscaping(mode, 1, 3)
            simpleNameEscaping(mode, 1, 1)
        }
    }

    private fun simpleNameEscaping(mode: Int, padding: Int, bytesPerFeed: Int) {
        for (i in 0..<16) {
            val base = "1234567890abcdef".substring(0, i)
            val inputKey = "$base\\\""
            val expected = "$base\""
            val doc = utf8Bytes("{\"__cirJsonId__\": \"root\", \"$inputKey\": 123456789}       ")
            val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
            assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
            assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            assertEquals(expected, parser.currentName)
            assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
            assertEquals(123456789, parser.intValue)
            assertToken(CirJsonToken.END_OBJECT, parser.nextToken())
            parser.close()
        }
    }

    @Test
    fun testInvalid() {
        val doc = utf8Bytes("[\"0\", \"\\u41=A\"]")

        for (mode in ALL_ASYNC_MODES) {
            invalid(mode, 0, doc, 99)
            invalid(mode, 0, doc, 5)
            invalid(mode, 0, doc, 3)
            invalid(mode, 0, doc, 2)
            invalid(mode, 0, doc, 1)

            invalid(mode, 1, doc, 99)
            invalid(mode, 1, doc, 3)
            invalid(mode, 1, doc, 1)
        }
    }

    private fun invalid(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            parser.currentText()
            fail("Expected an exception for unclosed ARRAY")
        } catch (e: StreamReadException) {
            verifyException(e, "for character escape")
        }

        parser.close()
    }

    @Test
    fun testEightDigitSequence() {
        val doc = utf8Bytes("[\"0\", \"\\u00411234\"]")

        for (mode in ALL_ASYNC_MODES) {
            eightDigitSequence(mode, 0, doc, 99)
            eightDigitSequence(mode, 0, doc, 5)
            eightDigitSequence(mode, 0, doc, 3)
            eightDigitSequence(mode, 0, doc, 2)
            eightDigitSequence(mode, 0, doc, 1)

            eightDigitSequence(mode, 1, doc, 99)
            eightDigitSequence(mode, 1, doc, 3)
            eightDigitSequence(mode, 1, doc, 1)
        }
    }

    private fun eightDigitSequence(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("A1234", parser.currentText())
        parser.close()
    }

    @Test
    fun testInvalidEscape() {
        val doc = utf8Bytes("[\"0\", \"\\u\u0080...\"]")

        for (mode in ALL_ASYNC_MODES) {
            invalidEscape(mode, 0, doc, 99)
            invalidEscape(mode, 0, doc, 5)
            invalidEscape(mode, 0, doc, 3)
            invalidEscape(mode, 0, doc, 2)
            invalidEscape(mode, 0, doc, 1)

            invalidEscape(mode, 1, doc, 99)
            invalidEscape(mode, 1, doc, 3)
            invalidEscape(mode, 1, doc, 1)
        }
    }

    private fun invalidEscape(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
            parser.currentText()
            fail("Should not pass")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character")
        }

        parser.close()
    }

}