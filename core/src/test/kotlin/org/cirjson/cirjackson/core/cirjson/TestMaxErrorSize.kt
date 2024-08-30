package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

@Suppress("KotlinConstantConditions")
class TestMaxErrorSize : TestBase() {

    @Test
    fun testShortErrorMessage() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            shortErrorMessage(mode)
        }
    }

    private fun shortErrorMessage(mode: Int) {
        assertTrue(SHORT_DOC.length < 256)
        val parser = createParser(mode, SHORT_DOC)

        try {
            parser.nextToken()
            fail("Expected an exception for unrecognized token")
        } catch (e: StreamReadException) {
            val expectedPrefix = "Unrecognized token '"
            val expectedSuffix = "': was expecting"
            verifyException(e, expectedPrefix)
            verifyException(e, expectedSuffix)
            val message = e.message
            val tokenLength = message.indexOf(expectedSuffix) - expectedPrefix.length
            assertEquals(SHORT_DOC.length, tokenLength)
        }

        parser.close()
    }

    @Test
    fun testLargeErrorMessage() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            largeErrorMessage(mode)
        }
    }

    private fun largeErrorMessage(mode: Int) {
        assertTrue(LARGE_DOC.length > 256)
        val parser = createParser(mode, LARGE_DOC)

        try {
            parser.nextToken()
            fail("Expected an exception for unrecognized token")
        } catch (e: StreamReadException) {
            val expectedPrefix = "Unrecognized token '"
            val expectedSuffix = "...': was expecting"
            verifyException(e, expectedPrefix)
            verifyException(e, expectedSuffix)
            val message = e.message
            val tokenLength = message.indexOf(expectedSuffix) - expectedPrefix.length
            assertEquals(EXPECTED_MAX_TOKEN_LEN, tokenLength)
        }

        parser.close()
    }

    companion object {

        private const val EXPECTED_MAX_TOKEN_LEN = 256

        private const val SHORT_DOC =
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

        private const val LARGE_DOC =
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

    }

}