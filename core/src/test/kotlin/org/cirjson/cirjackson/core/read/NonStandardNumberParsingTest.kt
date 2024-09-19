package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.StreamReadFeature
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import org.cirjson.cirjackson.core.exception.StreamReadException
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail

class NonStandardNumberParsingTest : TestBase() {

    private val slowFactory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)
            .enable(CirJsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)
            .enable(CirJsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS).build()

    private val fastFactory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)
            .enable(CirJsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)
            .enable(CirJsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS)
            .enable(StreamReadFeature.USE_FAST_DOUBLE_PARSER).enable(StreamReadFeature.USE_FAST_DOUBLE_PARSER).build()

    private val factories = arrayOf(slowFactory, fastFactory)

    @Test
    fun testHexadecimal() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                hexadecimal(factory, mode)
            }
        }
    }

    private fun hexadecimal(factory: CirJsonFactory, mode: Int) {
        val doc = " 0xc0ffee "
        val parser = createParser(factory, mode, doc)

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('x'")
        }

        parser.close()
    }

    @Test
    fun testHexadecimalCapital() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                hexadecimalCapital(factory, mode)
            }
        }
    }

    private fun hexadecimalCapital(factory: CirJsonFactory, mode: Int) {
        val doc = " 0xC0FFEE "
        val parser = createParser(factory, mode, doc)

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('x'")
        }

        parser.close()
    }

    @Test
    fun testNegativeHexadecimal() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                negativeHexadecimal(factory, mode)
            }
        }
    }

    private fun negativeHexadecimal(factory: CirJsonFactory, mode: Int) {
        val doc = " -0xc0ffee "
        val parser = createParser(factory, mode, doc)

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('x'")
        }

        parser.close()
    }

    @Test
    fun testBigDecimal() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                bigDecimal(factory, mode)
            }
        }
    }

    private fun bigDecimal(factory: CirJsonFactory, mode: Int) {
        val value = "7976931348623157e309"
        val doc = " $value "
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(BigDecimal(value), parser.bigDecimalValue)
        assertFalse(parser.isNaN)
        assertEquals(Double.POSITIVE_INFINITY, parser.doubleValue)
        assertFalse(parser.isNaN)
    }

    @Test
    fun testFloatMarker() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                floatMarker(factory, mode)
            }
        }
    }

    private fun floatMarker(factory: CirJsonFactory, mode: Int) {
        val doc = " -0.123f "
        val parser = createParser(factory, mode, doc)

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
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                doubleMarker(factory, mode)
            }
        }
    }

    private fun doubleMarker(factory: CirJsonFactory, mode: Int) {
        val doc = " -0.123d "
        val parser = createParser(factory, mode, doc)

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
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                longMarker(factory, mode)
            }
        }
    }

    private fun longMarker(factory: CirJsonFactory, mode: Int) {
        val doc = " -123L "
        val parser = createParser(factory, mode, doc)

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
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                twoDecimalPoints(factory, mode)
            }
        }
    }

    private fun twoDecimalPoints(factory: CirJsonFactory, mode: Int) {
        val doc = " -0.123.456 "
        val parser = createParser(factory, mode, doc)

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
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            leadingDotInDecimal(mode)
        }
    }

    private fun leadingDotInDecimal(mode: Int) {
        val doc = " .123 "
        val parser = createParser(mode, doc)

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('.'")
        }

        parser.close()
    }

    @Test
    fun testLeadingPlusSignInDecimal() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            leadingPlusSignInDecimal(mode)
        }
    }

    private fun leadingPlusSignInDecimal(mode: Int) {
        var doc = " +123 "
        var parser = createParser(mode, doc)

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e,
                    "Unexpected character ('+' (code 43)) in numeric value: CirJSON spec does not allow numbers to have plus signs: enable `CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS` to allow")
        }

        parser.close()

        doc = " +0.123 "
        parser = createParser(mode, doc)

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e,
                    "Unexpected character ('+' (code 43)) in numeric value: CirJSON spec does not allow numbers to have plus signs: enable `CirJsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS` to allow")
        }

        parser.close()
    }

    @Test
    fun testTrailingDotInDecimal() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            trailingDotInDecimal(mode)
        }
    }

    private fun trailingDotInDecimal(mode: Int) {
        val doc = " 123. "
        val parser = createParser(mode, doc)

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Decimal point not followed by a digit")
        }

        parser.close()
    }

    @Test
    fun testLeadingDotInDecimalAllowed() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                leadingDotInDecimalAllowed(factory, mode)
            }
        }
    }

    private fun leadingDotInDecimalAllowed(factory: CirJsonFactory, mode: Int) {
        val doc = " .125 "
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(0.125, parser.valueAsDouble)
        assertEquals("0.125", parser.bigDecimalValue.toString())
        assertEquals(".125", parser.text)
        parser.close()
    }

    @Test
    fun testLeadingPlusSignInDecimalAllowed() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                leadingPlusSignInDecimalAllowed(factory, mode)
            }
        }
    }

    private fun leadingPlusSignInDecimalAllowed(factory: CirJsonFactory, mode: Int) {
        var doc = " +125 "
        var parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(125.0, parser.valueAsDouble)
        assertEquals("125", parser.bigDecimalValue.toString())
        assertEquals("125", parser.text)
        parser.close()

        doc = " +0.125 "
        parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(0.125, parser.valueAsDouble)
        assertEquals("0.125", parser.bigDecimalValue.toString())
        assertEquals("0.125", parser.text)
        parser.close()

        doc = " +.125 "
        parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(0.125, parser.valueAsDouble)
        assertEquals("0.125", parser.bigDecimalValue.toString())
        assertEquals(".125", parser.text)
        parser.close()
    }

    @Test
    fun testTrailingDotInDecimalAllowed() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                trailingDotInDecimalAllowed(factory, mode)
            }
        }
    }

    private fun trailingDotInDecimalAllowed(factory: CirJsonFactory, mode: Int) {
        val doc = " 125. "
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(125.0, parser.valueAsDouble)
        assertEquals("125", parser.bigDecimalValue.toString())
        assertEquals("125.", parser.text)
        parser.close()
    }

    @Test
    fun testLeadingDotInNegativeDecimalAllowed() {
        for (factory in factories) {
            for (mode in ALL_NON_ASYNC_PARSER_MODES) {
                leadingDotInNegativeDecimalAllowed(factory, mode)
            }
        }
    }

    private fun leadingDotInNegativeDecimalAllowed(factory: CirJsonFactory, mode: Int) {
        val doc = " -.125 "
        val parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(-0.125, parser.valueAsDouble)
        assertEquals("-0.125", parser.bigDecimalValue.toString())
        assertEquals("-.125", parser.text)
        parser.close()
    }

}