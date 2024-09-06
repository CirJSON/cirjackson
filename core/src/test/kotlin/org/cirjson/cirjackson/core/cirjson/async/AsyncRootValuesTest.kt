package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AsyncRootValuesTest : AsyncTestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testTokenRootTokens() {
        val baseValues = arrayOf("true" to CirJsonToken.VALUE_TRUE, "false" to CirJsonToken.VALUE_FALSE,
                "null" to CirJsonToken.VALUE_NULL)
        val indentedValues = baseValues.flatMap { (valueString, value) ->
            listOf(valueString to value, "    $valueString" to value, "$valueString    " to value,
                    "    $valueString    " to value)
        }
        val values = indentedValues.map { (valueString, value) -> utf8Bytes(valueString) to value }

        for (mode in ALL_ASYNC_MODES) {
            for ((doc, expectedToken) in values) {
                tokenRootTokens(doc, expectedToken, mode, 0, 99)
                tokenRootTokens(doc, expectedToken, mode, 0, 5)
                tokenRootTokens(doc, expectedToken, mode, 0, 3)
                tokenRootTokens(doc, expectedToken, mode, 0, 2)
                tokenRootTokens(doc, expectedToken, mode, 0, 1)

                tokenRootTokens(doc, expectedToken, mode, 1, 99)
                tokenRootTokens(doc, expectedToken, mode, 1, 3)
                tokenRootTokens(doc, expectedToken, mode, 1, 1)
            }
        }
    }

    private fun tokenRootTokens(doc: ByteArray, expectedToken: CirJsonToken, mode: Int, padding: Int,
            bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())
        assertToken(expectedToken, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testTokenRootSequence() {
        val doc = utf8Bytes("\n[ \"root\", true, false,\nnull  ,null\n,true,false]")

        for (mode in ALL_ASYNC_MODES) {
            tokenRootSequence(mode, 0, doc, 99)
            tokenRootSequence(mode, 0, doc, 5)
            tokenRootSequence(mode, 0, doc, 3)
            tokenRootSequence(mode, 0, doc, 2)
            tokenRootSequence(mode, 0, doc, 1)

            tokenRootSequence(mode, 1, doc, 99)
            tokenRootSequence(mode, 1, doc, 3)
            tokenRootSequence(mode, 1, doc, 1)
        }
    }

    private fun tokenRootSequence(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        assertTrue(parser.isClosed)
    }

    @Test
    fun testMixedRootSequence() {
        val bytes = ByteArrayOutputStream(100)
        bytes.write(utf8Bytes("{ \"__cirJsonId__\":\"rootObject\", \"a\" : 4 }"))
        bytes.write(utf8Bytes("[ \"rootArray\", 12, -987,false ]"))
        bytes.write(utf8Bytes(" 12356"))
        bytes.write(utf8Bytes(" true"))
        val doc = bytes.toByteArray()

        for (mode in ALL_ASYNC_MODES) {
            mixedRootSequence(mode, 0, doc, 99)
            mixedRootSequence(mode, 0, doc, 5)
            mixedRootSequence(mode, 0, doc, 3)
            mixedRootSequence(mode, 0, doc, 2)
            mixedRootSequence(mode, 0, doc, 1)

            mixedRootSequence(mode, 1, doc, 99)
            mixedRootSequence(mode, 1, doc, 3)
            mixedRootSequence(mode, 1, doc, 1)
        }
    }

    private fun mixedRootSequence(mode: Int, padding: Int, doc: ByteArray, bytesPerFeed: Int) {
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertNull(parser.currentToken())

        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
        assertEquals("a", parser.currentName)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(4, parser.intValue)
        assertToken(CirJsonToken.END_OBJECT, parser.nextToken())

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(12, parser.intValue)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(-987, parser.intValue)
        assertToken(CirJsonToken.VALUE_FALSE, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())

        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(12356, parser.intValue)

        assertToken(CirJsonToken.VALUE_TRUE, parser.nextToken())

        assertNull(parser.nextToken())
        assertTrue(parser.isClosed)
    }

}