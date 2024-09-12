package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class ArrayParsingTest : TestBase() {

    @Test
    fun testValidEmpty() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            validEmpty(mode)
        }
    }

    private fun validEmpty(mode: Int) {
        val parser = createParser(mode, "[\"root\"   \n  ]")
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testInvalidEmptyMissingClose() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            invalidEmptyMissingClose(mode)
        }
    }

    private fun invalidEmptyMissingClose(mode: Int) {
        val parser = createParser(mode, "[\"root\" ")
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "expected close marker for ARRAY")
        }

        parser.close()
    }

    @Test
    fun testInvalidMissingFieldName() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            invalidMissingFieldName(mode)
        }
    }

    private fun invalidMissingFieldName(mode: Int) {
        val parser = createParser(mode, "[\"root\", : 3 ]")
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character")
        }

        parser.close()
    }

    @Test
    fun testInvalidExtraComma() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            invalidExtraComma(mode)
        }
    }

    private fun invalidExtraComma(mode: Int) {
        val parser = createParser(mode, "[\"root\", 24, ]")
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(24, parser.intValue)

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "expected a value", "expected a valid value")
        }

        parser.close()
    }

    @Test
    fun testMissingValueAsNullByEnablingFeature() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            missingValueAsNullByEnablingFeature(mode)
        }
    }

    private fun missingValueAsNullByEnablingFeature(mode: Int) {
        val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_MISSING_VALUES).build()
        val parser = createParser(factory, mode, "[\"root\", \"a\",,,,\"abc\", ]")
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("a", parser.valueAsString)
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NULL, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

    @Test
    fun testMissingValueAsNullByNotEnablingFeature() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            missingValueAsNullByNotEnablingFeature(mode)
        }
    }

    private fun missingValueAsNullByNotEnablingFeature(mode: Int) {
        val parser = createParser(mode, "[\"root\", \"a\",,\"abc\"]")
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("a", parser.valueAsString)

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "expected a value", "expected a valid value")
        }

        parser.close()
    }

    @Test
    fun testNotMissingValueByEnablingFeature() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            notMissingValueByEnablingFeature(mode)
        }
    }

    private fun notMissingValueByEnablingFeature(mode: Int) {
        val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_MISSING_VALUES).build()
        val parser = createParser(factory, mode, "[\"root\", \"a\",\"abc\"]")
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("a", parser.valueAsString)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        assertNull(parser.nextToken())
        parser.close()
    }

}