package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class AsyncScopeMatchingTest : AsyncTestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testUnclosedArray() {
        val doc = utf8Bytes("  [ \"root\", 1, 2  ")

        for (mode in ALL_ASYNC_MODES) {
            unclosedArray(mode, 0, doc, 99)
            unclosedArray(mode, 0, doc, 5)
            unclosedArray(mode, 0, doc, 3)
            unclosedArray(mode, 0, doc, 2)
            unclosedArray(mode, 0, doc, 1)

            unclosedArray(mode, 1, doc, 99)
            unclosedArray(mode, 1, doc, 3)
            unclosedArray(mode, 1, doc, 1)
        }
    }

    private fun unclosedArray(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
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
        val doc = utf8Bytes("{ \"__cirJsonId__\":\"root\", \"key\": 3 ")

        for (mode in ALL_ASYNC_MODES) {
            unclosedObject(mode, 0, doc, 99)
            unclosedObject(mode, 0, doc, 5)
            unclosedObject(mode, 0, doc, 3)
            unclosedObject(mode, 0, doc, 2)
            unclosedObject(mode, 0, doc, 1)

            unclosedObject(mode, 1, doc, 99)
            unclosedObject(mode, 1, doc, 3)
            unclosedObject(mode, 1, doc, 1)
        }
    }

    private fun unclosedObject(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
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
        val doc = utf8Bytes("{ \"__cirJsonId__\":\"root\", \"abcd")

        for (mode in ALL_ASYNC_MODES) {
            eofInName(mode, 0, doc, 99)
            eofInName(mode, 0, doc, 5)
            eofInName(mode, 0, doc, 3)
            eofInName(mode, 0, doc, 2)
            eofInName(mode, 0, doc, 1)

            eofInName(mode, 1, doc, 99)
            eofInName(mode, 1, doc, 3)
            eofInName(mode, 1, doc, 1)
        }
    }

    private fun eofInName(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected end-of-input")
        }

        parser.close()
    }

    @Test
    fun testMismatchedArray() {
        val doc = utf8Bytes("[ \"root\" }")

        for (mode in ALL_ASYNC_MODES) {
            mismatchedArray(mode, 0, doc, 99)
            mismatchedArray(mode, 0, doc, 5)
            mismatchedArray(mode, 0, doc, 3)
            mismatchedArray(mode, 0, doc, 2)
            mismatchedArray(mode, 0, doc, 1)

            mismatchedArray(mode, 1, doc, 99)
            mismatchedArray(mode, 1, doc, 3)
            mismatchedArray(mode, 1, doc, 1)
        }
    }

    private fun mismatchedArray(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
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
        val doc = utf8Bytes("{ \"__cirJsonId__\":\"root\"]")

        for (mode in ALL_ASYNC_MODES) {
            mismatchedObject(mode, 0, doc, 99)
            mismatchedObject(mode, 0, doc, 5)
            mismatchedObject(mode, 0, doc, 3)
            mismatchedObject(mode, 0, doc, 2)
            mismatchedObject(mode, 0, doc, 1)

            mismatchedObject(mode, 1, doc, 99)
            mismatchedObject(mode, 1, doc, 3)
            mismatchedObject(mode, 1, doc, 1)
        }
    }

    private fun mismatchedObject(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
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
        val doc = utf8Bytes("{ \"__cirJsonId__\":\"root\", \"a\" \"b\" }")

        for (mode in ALL_ASYNC_MODES) {
            missingColon(mode, 0, doc, 99)
            missingColon(mode, 0, doc, 5)
            missingColon(mode, 0, doc, 3)
            missingColon(mode, 0, doc, 2)
            missingColon(mode, 0, doc, 1)

            missingColon(mode, 1, doc, 99)
            missingColon(mode, 1, doc, 3)
            missingColon(mode, 1, doc, 1)
        }
    }

    private fun missingColon(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "was expecting a colon")
        }

        parser.close()
    }

}