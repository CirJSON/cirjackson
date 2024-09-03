package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.exception.StreamReadException
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class AsyncInvalidCharsTest : AsyncTestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testUtf8BOMHandling() {
        for (mode in ALL_ASYNC_MODES) {
            utf8BOMHandling(mode, 0, 99)
            utf8BOMHandling(mode, 0, 5)
            utf8BOMHandling(mode, 0, 3)
            utf8BOMHandling(mode, 0, 2)
            utf8BOMHandling(mode, 0, 1)

            utf8BOMHandling(mode, 1, 99)
            utf8BOMHandling(mode, 1, 3)
            utf8BOMHandling(mode, 1, 1)
        }
    }

    private fun utf8BOMHandling(mode: Int, padding: Int, bytesPerFeed: Int) {
        utf8BOMHandlingPassing(mode, padding, bytesPerFeed)
        utf8BOMHandlingFailing(mode, padding, bytesPerFeed, false,
                "Unexpected byte 0x5b following 0xEF; should get 0xBB as second byte")
        utf8BOMHandlingFailing(mode, padding, bytesPerFeed, true,
                "Unexpected byte 0x5b following 0xEF 0xBB; should get 0xBF as third byte")
    }

    private fun utf8BOMHandlingPassing(mode: Int, padding: Int, bytesPerFeed: Int) {
        val bytes = ByteArrayOutputStream()
        bytes.write(0xEF)
        bytes.write(0xBB)
        bytes.write(0xBF)
        bytes.write("[ \"1\" ]".toByteArray())
        val doc = bytes.toByteArray()

        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)

        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        val location = parser.parser.currentTokenLocation()
        assertEquals(-1L, location.charOffset)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    private fun utf8BOMHandlingFailing(mode: Int, padding: Int, bytesPerFeed: Int, okBytes: Boolean, verify: String) {
        val bytes = ByteArrayOutputStream()
        bytes.write(0xEF)

        if (okBytes) {
            bytes.write(0xBB)
        }

        bytes.write("[ \"1\" ]".toByteArray())
        val doc = bytes.toByteArray()

        try {
            createAsync(factory, mode, bytesPerFeed, doc, padding).use { parser ->
                assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
                fail("Should have thrown an exception")
            }
        } catch (e: StreamReadException) {
            verifyException(e, verify)
        }
    }

    @Test
    fun testHandlingOfInvalidSpace() {
        for (mode in ALL_ASYNC_MODES) {
            handlingOfInvalidSpace(mode, 0, 99)
            handlingOfInvalidSpace(mode, 0, 5)
            handlingOfInvalidSpace(mode, 0, 3)
            handlingOfInvalidSpace(mode, 0, 2)
            handlingOfInvalidSpace(mode, 0, 1)

            handlingOfInvalidSpace(mode, 1, 99)
            handlingOfInvalidSpace(mode, 1, 3)
            handlingOfInvalidSpace(mode, 1, 1)
        }
    }

    private fun handlingOfInvalidSpace(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = "{ \u0008 \"__cirJsonId__\":\"1\"}"
        val parser = createAsync(factory, mode, bytesPerFeed, utf8Bytes(doc), padding)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "code 8")
            verifyException(e, "code 8")
        }
    }

}