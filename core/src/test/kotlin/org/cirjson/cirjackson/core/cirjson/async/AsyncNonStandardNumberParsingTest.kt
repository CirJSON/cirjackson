package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class AsyncNonStandardNumberParsingTest : AsyncTestBase() {

    private val factory = newStreamFactory()

    @Test
    fun testHexadecimal() {
        for (mode in ALL_ASYNC_MODES) {
            hexadecimal(mode, 0, 99)
            hexadecimal(mode, 0, 5)
            hexadecimal(mode, 0, 3)
            hexadecimal(mode, 0, 2)
            hexadecimal(mode, 0, 1)

            hexadecimal(mode, 1, 99)
            hexadecimal(mode, 1, 3)
            hexadecimal(mode, 1, 1)
        }
    }

    private fun hexadecimal(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[\"0\", 0xc0ffee ]")
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('x'")
        }

        parser.close()
    }

    @Test
    fun testHexadecimalBigX() {
        for (mode in ALL_ASYNC_MODES) {
            hexadecimalBigX(mode, 0, 99)
            hexadecimalBigX(mode, 0, 5)
            hexadecimalBigX(mode, 0, 3)
            hexadecimalBigX(mode, 0, 2)
            hexadecimalBigX(mode, 0, 1)

            hexadecimalBigX(mode, 1, 99)
            hexadecimalBigX(mode, 1, 3)
            hexadecimalBigX(mode, 1, 1)
        }
    }

    private fun hexadecimalBigX(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[\"0\", 0XC0FFEE ]")
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('X'")
        }

        parser.close()
    }

    @Test
    fun testNegativeHexadecimal() {
        for (mode in ALL_ASYNC_MODES) {
            negativeHexadecimal(mode, 0, 99)
            negativeHexadecimal(mode, 0, 5)
            negativeHexadecimal(mode, 0, 3)
            negativeHexadecimal(mode, 0, 2)
            negativeHexadecimal(mode, 0, 1)

            negativeHexadecimal(mode, 1, 99)
            negativeHexadecimal(mode, 1, 3)
            negativeHexadecimal(mode, 1, 1)
        }
    }

    private fun negativeHexadecimal(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[\"0\", -0xc0ffee ]")
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('x'")
        }

        parser.close()
    }

    @Test
    fun testFloatMarker() {
        for (mode in ALL_ASYNC_MODES) {
            floatMarker(mode, 0, 99)
            floatMarker(mode, 0, 5)
            floatMarker(mode, 0, 3)
            floatMarker(mode, 0, 2)
            floatMarker(mode, 0, 1)

            floatMarker(mode, 1, 99)
            floatMarker(mode, 1, 3)
            floatMarker(mode, 1, 1)
        }
    }

    private fun floatMarker(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[\"0\", -0.123f ]")
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('f'")
        }

        parser.close()
    }

    @Test
    fun testDoubleMarker() {
        for (mode in ALL_ASYNC_MODES) {
            doubleMarker(mode, 0, 99)
            doubleMarker(mode, 0, 5)
            doubleMarker(mode, 0, 3)
            doubleMarker(mode, 0, 2)
            doubleMarker(mode, 0, 1)

            doubleMarker(mode, 1, 99)
            doubleMarker(mode, 1, 3)
            doubleMarker(mode, 1, 1)
        }
    }

    private fun doubleMarker(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[\"0\", -0.123d ]")
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('d'")
        }

        parser.close()
    }

    @Test
    fun testLongMarker() {
        for (mode in ALL_ASYNC_MODES) {
            longMarker(mode, 0, 99)
            longMarker(mode, 0, 5)
            longMarker(mode, 0, 3)
            longMarker(mode, 0, 2)
            longMarker(mode, 0, 1)

            longMarker(mode, 1, 99)
            longMarker(mode, 1, 3)
            longMarker(mode, 1, 1)
        }
    }

    private fun longMarker(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[\"0\", -123L ]")
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('L'")
        }

        parser.close()
    }

    @Test
    fun testTwoDecimalPoints() {
        for (mode in ALL_ASYNC_MODES) {
            twoDecimalPoints(mode, 0, 99)
            twoDecimalPoints(mode, 0, 5)
            twoDecimalPoints(mode, 0, 3)
            twoDecimalPoints(mode, 0, 2)
            twoDecimalPoints(mode, 0, 1)

            twoDecimalPoints(mode, 1, 99)
            twoDecimalPoints(mode, 1, 3)
            twoDecimalPoints(mode, 1, 1)
        }
    }

    private fun twoDecimalPoints(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[\"0\", -0.123.456 ]")
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('.'")
        }

        parser.close()
    }

    @Test
    fun testLeadingDotInDecimal() {
        for (mode in ALL_ASYNC_MODES) {
            leadingDotInDecimal(mode, 0, 99)
            leadingDotInDecimal(mode, 0, 5)
            leadingDotInDecimal(mode, 0, 3)
            leadingDotInDecimal(mode, 0, 2)
            leadingDotInDecimal(mode, 0, 1)

            leadingDotInDecimal(mode, 1, 99)
            leadingDotInDecimal(mode, 1, 3)
            leadingDotInDecimal(mode, 1, 1)
        }
    }

    private fun leadingDotInDecimal(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[\"0\", .123 ]")
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('.'")
        }

        parser.close()
    }

    @Test
    fun testLeadingDotInDecimalEnabled() {
        for (mode in ALL_ASYNC_MODES) {
            leadingDotInDecimalEnabled(mode, 0, 99)
            leadingDotInDecimalEnabled(mode, 0, 5)
            leadingDotInDecimalEnabled(mode, 0, 3)
            leadingDotInDecimalEnabled(mode, 0, 2)
            leadingDotInDecimalEnabled(mode, 0, 1)

            leadingDotInDecimalEnabled(mode, 1, 99)
            leadingDotInDecimalEnabled(mode, 1, 3)
            leadingDotInDecimalEnabled(mode, 1, 1)
        }
    }

    private fun leadingDotInDecimalEnabled(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[\"0\", .123 ]")
        val factory =
                CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS).build()
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(0.123, parser.doubleValue)
        assertEquals("0.123", parser.bigDecimalValue.toString())
        assertEquals(".123", parser.currentText())
        parser.close()
    }

    @Test
    fun testLeadingPlusSignInInteger() {
        for (mode in ALL_ASYNC_MODES) {
            leadingPlusSignInInteger(mode, 0, 99)
            leadingPlusSignInInteger(mode, 0, 5)
            leadingPlusSignInInteger(mode, 0, 3)
            leadingPlusSignInInteger(mode, 0, 2)
            leadingPlusSignInInteger(mode, 0, 1)

            leadingPlusSignInInteger(mode, 1, 99)
            leadingPlusSignInInteger(mode, 1, 3)
            leadingPlusSignInInteger(mode, 1, 1)
        }
    }

    private fun leadingPlusSignInInteger(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[\"0\", +123 ]")
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('+'")
        }

        parser.close()
    }

    @Test
    fun testLeadingPlusSignInDecimal() {
        for (mode in ALL_ASYNC_MODES) {
            leadingPlusSignInDecimal(mode, 0, 99)
            leadingPlusSignInDecimal(mode, 0, 5)
            leadingPlusSignInDecimal(mode, 0, 3)
            leadingPlusSignInDecimal(mode, 0, 2)
            leadingPlusSignInDecimal(mode, 0, 1)

            leadingPlusSignInDecimal(mode, 1, 99)
            leadingPlusSignInDecimal(mode, 1, 3)
            leadingPlusSignInDecimal(mode, 1, 1)
        }
    }

    private fun leadingPlusSignInDecimal(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[\"0\", +0.123 ]")
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('+'")
        }

        parser.close()
    }

    @Test
    fun testLeadingPlusSignInIntegerEnabled() {
        for (mode in ALL_ASYNC_MODES) {
            leadingPlusSignInIntegerEnabled(mode, 0, 99)
            leadingPlusSignInIntegerEnabled(mode, 0, 5)
            leadingPlusSignInIntegerEnabled(mode, 0, 3)
            leadingPlusSignInIntegerEnabled(mode, 0, 2)
            leadingPlusSignInIntegerEnabled(mode, 0, 1)

            leadingPlusSignInIntegerEnabled(mode, 1, 99)
            leadingPlusSignInIntegerEnabled(mode, 1, 3)
            leadingPlusSignInIntegerEnabled(mode, 1, 1)
        }
    }

    private fun leadingPlusSignInIntegerEnabled(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[\"0\", +123 ]")
        val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS).build()
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(123.0, parser.doubleValue)
        assertEquals("123", parser.bigDecimalValue.toString())
        assertEquals("+123", parser.currentText())
        parser.close()
    }

    @Test
    fun testLeadingPlusSignInDecimalEnabled() {
        for (mode in ALL_ASYNC_MODES) {
            leadingPlusSignInDecimalEnabled(mode, 0, 99)
            leadingPlusSignInDecimalEnabled(mode, 0, 5)
            leadingPlusSignInDecimalEnabled(mode, 0, 3)
            leadingPlusSignInDecimalEnabled(mode, 0, 2)
            leadingPlusSignInDecimalEnabled(mode, 0, 1)

            leadingPlusSignInDecimalEnabled(mode, 1, 99)
            leadingPlusSignInDecimalEnabled(mode, 1, 3)
            leadingPlusSignInDecimalEnabled(mode, 1, 1)
        }
    }

    private fun leadingPlusSignInDecimalEnabled(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[\"0\", +0.123 ]")
        val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS).build()
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(0.123, parser.doubleValue)
        assertEquals("0.123", parser.bigDecimalValue.toString())
        assertEquals("+0.123", parser.currentText())
        parser.close()
    }

    @Test
    fun testLeadingPlusSignNoLeadingZeroDisabled() {
        for (mode in ALL_ASYNC_MODES) {
            leadingPlusSignNoLeadingZeroDisabled(mode, 0, 99)
            leadingPlusSignNoLeadingZeroDisabled(mode, 0, 5)
            leadingPlusSignNoLeadingZeroDisabled(mode, 0, 3)
            leadingPlusSignNoLeadingZeroDisabled(mode, 0, 2)
            leadingPlusSignNoLeadingZeroDisabled(mode, 0, 1)

            leadingPlusSignNoLeadingZeroDisabled(mode, 1, 99)
            leadingPlusSignNoLeadingZeroDisabled(mode, 1, 3)
            leadingPlusSignNoLeadingZeroDisabled(mode, 1, 1)
        }
    }

    private fun leadingPlusSignNoLeadingZeroDisabled(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[\"0\", +.123 ]")
        val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS).build()
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('.'")
        }

        parser.close()
    }

    @Test
    fun testLeadingPlusSignNoLeadingZeroEnabled() {
        for (mode in ALL_ASYNC_MODES) {
            leadingPlusSignNoLeadingZeroEnabled(mode, 0, 99)
            leadingPlusSignNoLeadingZeroEnabled(mode, 0, 5)
            leadingPlusSignNoLeadingZeroEnabled(mode, 0, 3)
            leadingPlusSignNoLeadingZeroEnabled(mode, 0, 2)
            leadingPlusSignNoLeadingZeroEnabled(mode, 0, 1)

            leadingPlusSignNoLeadingZeroEnabled(mode, 1, 99)
            leadingPlusSignNoLeadingZeroEnabled(mode, 1, 3)
            leadingPlusSignNoLeadingZeroEnabled(mode, 1, 1)
        }
    }

    private fun leadingPlusSignNoLeadingZeroEnabled(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[\"0\", +.123 ]")
        val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS,
                CirJsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS).build()
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(0.123, parser.doubleValue)
        assertEquals("0.123", parser.bigDecimalValue.toString())
        assertEquals("+0.123", parser.currentText())
        parser.close()
    }

    @Test
    fun testLeadingDotInNegativeDecimalEnabled() {
        for (mode in ALL_ASYNC_MODES) {
            leadingDotInNegativeDecimalEnabled(mode, 0, 99)
            leadingDotInNegativeDecimalEnabled(mode, 0, 5)
            leadingDotInNegativeDecimalEnabled(mode, 0, 3)
            leadingDotInNegativeDecimalEnabled(mode, 0, 2)
            leadingDotInNegativeDecimalEnabled(mode, 0, 1)

            leadingDotInNegativeDecimalEnabled(mode, 1, 99)
            leadingDotInNegativeDecimalEnabled(mode, 1, 3)
            leadingDotInNegativeDecimalEnabled(mode, 1, 1)
        }
    }

    private fun leadingDotInNegativeDecimalEnabled(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[\"0\", -.123 ]")
        val factory =
                CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS).build()
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(-0.123, parser.doubleValue)
        assertEquals("-0.123", parser.bigDecimalValue.toString())
        assertEquals("-0.123", parser.currentText())
        parser.close()
    }

    @Test
    fun testTrailingDotInDecimal() {
        for (mode in ALL_ASYNC_MODES) {
            trailingDotInDecimal(mode, 0, 99)
            trailingDotInDecimal(mode, 0, 5)
            trailingDotInDecimal(mode, 0, 3)
            trailingDotInDecimal(mode, 0, 2)
            trailingDotInDecimal(mode, 0, 1)

            trailingDotInDecimal(mode, 1, 99)
            trailingDotInDecimal(mode, 1, 3)
            trailingDotInDecimal(mode, 1, 1)
        }
    }

    private fun trailingDotInDecimal(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[\"0\", 123. ]")
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character (' '")
        }

        parser.close()
    }

    @Test
    fun testTrailingDotInDecimalEnabled() {
        for (mode in ALL_ASYNC_MODES) {
            trailingDotInDecimalEnabled(mode, 0, 99)
            trailingDotInDecimalEnabled(mode, 0, 5)
            trailingDotInDecimalEnabled(mode, 0, 3)
            trailingDotInDecimalEnabled(mode, 0, 2)
            trailingDotInDecimalEnabled(mode, 0, 1)

            trailingDotInDecimalEnabled(mode, 1, 99)
            trailingDotInDecimalEnabled(mode, 1, 3)
            trailingDotInDecimalEnabled(mode, 1, 1)
        }
    }

    private fun trailingDotInDecimalEnabled(mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes("[\"0\", 123. ]")
        val factory =
                CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS).build()
        val parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(123.0, parser.doubleValue)
        assertEquals("123", parser.bigDecimalValue.toString())
        assertEquals("123.", parser.currentText())
        parser.close()
    }

}