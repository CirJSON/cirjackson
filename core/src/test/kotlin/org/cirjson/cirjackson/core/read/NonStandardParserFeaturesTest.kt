package org.cirjson.cirjackson.core.read

import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.*

class NonStandardParserFeaturesTest : TestBase() {

    private val sharedFactory = sharedStreamFactory()

    private val leadingZeroesFactory =
            CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS).build()

    @Test
    fun testDefaults() {
        assertFalse(sharedFactory.isEnabled(CirJsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS))
        assertFalse(sharedFactory.isEnabled(CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS))
    }

    @Test
    fun testNonStandardAnyCharQuoting() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            nonStandardAnyCharQuoting(mode)
        }
    }

    private fun nonStandardAnyCharQuoting(mode: Int) {
        val doc = quote("\\'")
        var parser = createParser(sharedFactory, mode, doc)

        try {
            parser.nextToken()
            parser.text
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "unrecognized character escape")
        }

        parser.close()

        val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER).build()
        parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertEquals("'", parser.text)
        parser.close()
    }

    @Test
    fun testLeadingZeroes() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            leadingZeroes(mode, true)

            if (mode != MODE_DATA_INPUT) {
                leadingZeroes(mode, false)
            }
        }
    }

    private fun leadingZeroes(mode: Int, appendSpaces: Boolean) {
        var doc = "00003"

        if (appendSpaces) {
            doc += " "
        }

        var parser = createParser(sharedFactory, mode, doc)

        try {
            parser.nextToken()
            parser.text
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "invalid numeric value")
        }

        parser.close()

        parser = createParser(sharedFactory, mode, "[ \"root\", -000 ]")
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            parser.text
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "invalid numeric value")
        }

        parser.close()

        parser = createParser(leadingZeroesFactory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(3, parser.intValue)
        assertEquals("3", parser.text)
        parser.close()

        doc = "0${Int.MAX_VALUE}"

        if (appendSpaces) {
            doc += " "
        }

        parser = createParser(leadingZeroesFactory, mode, doc)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(Int.MAX_VALUE.toString(), parser.text)
        assertEquals(Int.MAX_VALUE, parser.intValue)
        assertSame(Integer::class.java, parser.numberValue::class.java)
        parser.close()

        parser = createParser(leadingZeroesFactory, mode, "[ \"root\", -000 ]")
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(0, parser.intValue)
        assertEquals("-0", parser.text)
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    @Test
    fun testAllowNaN() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            allowNaN(mode)
        }
    }

    private fun allowNaN(mode: Int) {
        val doc = "[ \"root\", NaN ]"
        var parser = createParser(sharedFactory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "non-standard")
        }

        parser.close()

        val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS).build()
        parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        val double = parser.doubleValue
        assertTrue(double.isNaN())
        assertEquals(Double.NaN, double)
        assertEquals("NaN", parser.text)

        try {
            parser.bigDecimalValue
        } catch (e: NumberFormatException) {
            verifyException(e, "can not be deserialized as `java.math.BigDecimal`")
        }

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()

        parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

    @Test
    fun testAllowInfinity() {
        for (mode in ALL_NON_ASYNC_PARSER_MODES) {
            allowInfinity(mode)
        }
    }

    private fun allowInfinity(mode: Int) {
        val doc = "[ \"root\", -INF, +INF, +Infinity, Infinity, -Infinity ]"
        var parser = createParser(sharedFactory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        try {
            parser.nextToken()
            fail("Should have thrown an exception")
        } catch (e: StreamReadException) {
            verifyException(e, "Non-standard token '-INF'")
        }

        parser.close()

        val factory = CirJsonFactory.builder().enable(CirJsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS).build()
        parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())

        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        var double = parser.doubleValue
        assertEquals("-INF", parser.text)
        assertTrue(double.isInfinite())
        assertEquals(Double.NEGATIVE_INFINITY, double)

        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        double = parser.doubleValue
        assertEquals("+INF", parser.text)
        assertTrue(double.isInfinite())
        assertEquals(Double.POSITIVE_INFINITY, double)

        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        double = parser.doubleValue
        assertEquals("+Infinity", parser.text)
        assertTrue(double.isInfinite())
        assertEquals(Double.POSITIVE_INFINITY, double)

        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        double = parser.doubleValue
        assertEquals("Infinity", parser.text)
        assertTrue(double.isInfinite())
        assertEquals(Double.POSITIVE_INFINITY, double)

        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        double = parser.doubleValue
        assertEquals("-Infinity", parser.text)
        assertTrue(double.isInfinite())
        assertEquals(Double.NEGATIVE_INFINITY, double)

        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()

        parser = createParser(factory, mode, doc)
        assertToken(CirJsonToken.START_ARRAY, parser.nextToken())
        assertToken(CirJsonToken.VALUE_STRING, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertToken(CirJsonToken.END_ARRAY, parser.nextToken())
        parser.close()
    }

}