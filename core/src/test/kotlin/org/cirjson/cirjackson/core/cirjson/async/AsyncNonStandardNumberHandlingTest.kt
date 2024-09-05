package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.cirjson.CirJsonReadFeature
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail

class AsyncNonStandardNumberHandlingTest : AsyncTestBase() {

    @Test
    fun testDefaultsForAsync() {
        assertFalse(CirJsonFactory().isEnabled(CirJsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS))
        assertFalse(CirJsonFactory().isEnabled(CirJsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS))
    }

    @Test
    fun testLeadingZeroesInt() {
        val baseValues =
                arrayOf("00003" to 3, "-00007" to -7, "056" to 56, "-04" to -4, "0${Int.MAX_VALUE}" to Int.MAX_VALUE)
        val values = baseValues.flatMap { (valueString, value) ->
            listOf(valueString to value, " $valueString" to value, "$valueString " to value, " $valueString " to value)
        }

        for (mode in ALL_ASYNC_MODES) {
            for ((valueString, value) in values) {
                leadingZeroesInt(valueString, value, mode, 0, 99)
                leadingZeroesInt(valueString, value, mode, 0, 5)
                leadingZeroesInt(valueString, value, mode, 0, 3)
                leadingZeroesInt(valueString, value, mode, 0, 2)
                leadingZeroesInt(valueString, value, mode, 0, 1)

                leadingZeroesInt(valueString, value, mode, 1, 99)
                leadingZeroesInt(valueString, value, mode, 1, 3)
                leadingZeroesInt(valueString, value, mode, 1, 1)
            }
        }
    }

    private fun leadingZeroesInt(valueString: String, value: Int, mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes(valueString)
        var factory = CirJsonFactory()
        var parser = createAsync(factory, mode, bytesPerFeed, doc, padding)

        try {
            parser.nextToken()
            parser.currentText()
            fail("Should have thrown an exception for doc <$valueString>")
        } catch (e: StreamReadException) {
            verifyException(e, "invalid numeric value")
        }

        parser.close()

        factory = factory.rebuild().enable(CirJsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS).build()
        parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.VALUE_NUMBER_INT, parser.nextToken())
        assertEquals(value, parser.intValue, "valueString: \"$valueString\", value: $value")
        assertEquals(value.toString(), parser.currentText(), "valueString: \"$valueString\", value: $value")
    }

    @Test
    fun testLeadingZeroesFloat() {
        val baseValues = arrayOf("00.25" to 0.25, "-000.5" to -0.5)
        val values = baseValues.flatMap { (valueString, value) ->
            listOf(valueString to value, " $valueString" to value, "$valueString " to value, " $valueString " to value)
        }

        for (mode in ALL_ASYNC_MODES) {
            for ((valueString, value) in values) {
                leadingZeroesFloat(valueString, value, mode, 0, 99)
                leadingZeroesFloat(valueString, value, mode, 0, 5)
                leadingZeroesFloat(valueString, value, mode, 0, 3)
                leadingZeroesFloat(valueString, value, mode, 0, 2)
                leadingZeroesFloat(valueString, value, mode, 0, 1)

                leadingZeroesFloat(valueString, value, mode, 1, 99)
                leadingZeroesFloat(valueString, value, mode, 1, 3)
                leadingZeroesFloat(valueString, value, mode, 1, 1)
            }
        }
    }

    private fun leadingZeroesFloat(valueString: String, value: Double, mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes(valueString)
        var factory = CirJsonFactory()
        var parser = createAsync(factory, mode, bytesPerFeed, doc, padding)

        try {
            parser.nextToken()
            parser.currentText()
            fail("Should have thrown an exception for doc <$valueString>")
        } catch (e: StreamReadException) {
            verifyException(e, "invalid numeric value")
        }

        parser.close()

        factory = factory.rebuild().enable(CirJsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS).build()
        parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(value.toString(), parser.currentText(), "valueString: \"$valueString\", value: $value")
        assertEquals(value, parser.doubleValue, "valueString: \"$valueString\", value: $value")
    }

    @Test
    fun testLeadingPeriodFloat() {
        val baseValues = arrayOf(".25" to 0.25, ".1" to 0.1, ".6125" to 0.6125)
        val values = baseValues.flatMap { (valueString, value) ->
            listOf(valueString to value, " $valueString" to value, "$valueString " to value, " $valueString " to value)
        }

        for (mode in ALL_ASYNC_MODES) {
            for ((valueString, value) in values) {
                leadingPeriodFloat(valueString, value, mode, 0, 99)
                leadingPeriodFloat(valueString, value, mode, 0, 5)
                leadingPeriodFloat(valueString, value, mode, 0, 3)
                leadingPeriodFloat(valueString, value, mode, 0, 2)
                leadingPeriodFloat(valueString, value, mode, 0, 1)

                leadingPeriodFloat(valueString, value, mode, 1, 99)
                leadingPeriodFloat(valueString, value, mode, 1, 3)
                leadingPeriodFloat(valueString, value, mode, 1, 1)
            }
        }
    }

    private fun leadingPeriodFloat(valueString: String, value: Double, mode: Int, padding: Int, bytesPerFeed: Int) {
        val doc = utf8Bytes(valueString)
        var factory = CirJsonFactory()
        var parser = createAsync(factory, mode, bytesPerFeed, doc, padding)

        try {
            parser.nextToken()
            parser.currentText()
            fail("Should have thrown an exception for doc <$valueString>")
        } catch (e: StreamReadException) {
            verifyException(e, "Unexpected character ('.'")
            verifyException(e, "expected a valid value")
        }

        parser.close()

        factory = factory.rebuild().enable(CirJsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS).build()
        parser = createAsync(factory, mode, bytesPerFeed, doc, padding)
        assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken())
        assertEquals(valueString.trim(), parser.currentText(), "valueString: \"$valueString\", value: $value")
        assertEquals(value, parser.doubleValue, "valueString: \"$valueString\", value: $value")
    }

}