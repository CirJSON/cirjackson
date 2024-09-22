package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.Test
import kotlin.test.fail

class ParserErrorHandlingTest : TestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testInvalidKeywords() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            for (value in VALUES) {
                invalidKeywords(mode, value)
            }
        }
    }

    private fun invalidKeywords(mode: Int, value: String) {
        var doc = "{ \"__cirJsonId__\" : \"root\", \"key1\" : $value }"
        var parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_OBJECT, parser.nextToken())
        assertToken(CirJsonToken.CIRJSON_ID_PROPERTY_NAME, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            assertToken(CirJsonToken.PROPERTY_NAME, parser.nextToken())
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unrecognized token")
            verifyException(e, value)
        }

        parser.close()

        doc = "$value "
        parser = createParser(factory, mode, doc)

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unrecognized token")
            verifyException(e, value)
        }

        parser.close()
    }

    @Test
    fun testMangledNumbersInt() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            mangledNumbersInt(mode)
        }
    }

    private fun mangledNumbersInt(mode: Int) {
        val doc = "123true"
        val parser = createParser(factory, mode, doc)

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "expected space")
        }

        parser.close()
    }

    @Test
    fun testMangledNumbersFloat() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            mangledNumbersFloat(mode)
        }
    }

    private fun mangledNumbersFloat(mode: Int) {
        val doc = "1.5false"
        val parser = createParser(factory, mode, doc)

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "expected space")
        }

        parser.close()
    }

    companion object {

        private val VALUES =
                arrayOf("nul", "Null", "nulla", "fal", "False", "fals0", "falsett0", "tr", "truE", "treu", "trueenough",
                        "C")

    }

}